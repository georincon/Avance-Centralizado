package com.onboarding.service

import com.onboarding.common.ApiException
import com.onboarding.common.BadRequestException
import com.onboarding.common.UnauthorizedException
import com.onboarding.domain.AuditEvent
import com.onboarding.domain.ContactChannel
import com.onboarding.domain.OnboardingStatus
import com.onboarding.repository.AuditEventRepository
import com.onboarding.repository.OnboardingSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

/** Resultado de pedir el codigo: `devCode` solo se rellena en modo desarrollo y si hubo envio real. */
data class LostDeviceStartResult(val devCode: String?)

data class LostDeviceConfirmResult(
    val revoked: Int,
    val failed: Int,
    val withoutStatus: Int,
    val blocked: Boolean,
    /** Entradas de la wallet (y de su módulo de claves) que el proveedor de wallet marcó como revocadas. */
    val walletRevoked: Int = 0,
    val walletFailed: Int = 0,
)

/**
 * «Perdi mi celular»: permite pedir la revocacion de las credenciales SIN la wallet perdida
 * (CIR 2024/2979 art. 6(3)(b)(c)). El medio independiente es el contacto verificado en el
 * onboarding (OTP). Ver docs del README: en desarrollo el OTP es simulado, asi que la seguridad
 * real depende de conectar un proveedor SMS/correo.
 */
@Service
class LostDeviceService(
    private val sessionRepository: OnboardingSessionRepository,
    private val auditRepository: AuditEventRepository,
    private val otpService: OtpService,
    private val issuerClient: IssuerRevocationClient,
    @Value("\${onboarding.otp.expose-code:false}") private val exposeOtpCode: Boolean,
    @Value("\${onboarding.lost-device.max-per-document-per-hour:3}") maxPerDocument: Int,
    @Value("\${onboarding.lost-device.max-per-ip-per-hour:10}") maxPerIp: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    internal var clock: Clock = Clock.systemUTC()
        set(value) { field = value; byDocument.clock = value; byIp.clock = value }
    private val byDocument = HourlyRateLimiter(maxPerDocument)
    private val byIp = HourlyRateLimiter(maxPerIp)

    val configured: Boolean get() = issuerClient.configured

    /** Siempre termina igual (haya o no cedula); solo el limite de abuso responde 429. */
    fun start(documentNumber: String, clientIp: String): LostDeviceStartResult {
        val doc = normalize(documentNumber)
        // Se cuenta TODA solicitud (exista o no la cedula) para no abrir un canal de enumeracion.
        if (!byIp.tryAcquire(clientIp) || !byDocument.tryAcquire(doc)) {
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes. Intenta de nuevo mas tarde.")
        }
        val session = findSession(doc)
        val destination = session?.let {
            when (it.contactChannel) {
                ContactChannel.SMS -> it.phoneNumber
                ContactChannel.EMAIL -> it.email
                null -> null
            }?.takeIf { d -> d.isNotBlank() }
        }
        if (session == null || destination == null) {
            audit(null, "LOST_DEVICE_UNKNOWN", "Solicitud sin sesion aprobada con contacto verificado (doc ${maskDoc(doc)})")
            return LostDeviceStartResult(null)
        }
        return try {
            val result = otpService.issue(session.id, destination, session.contactChannel!!)
            audit(session.id, "LOST_DEVICE_CODE_SENT", "Codigo enviado a ${result.sendResult.destinationMasked} (doc ${maskDoc(doc)})")
            LostDeviceStartResult(if (exposeOtpCode) result.code else null)
        } catch (e: Exception) {
            log.error("No se pudo enviar el OTP de «Perdi mi celular»: {}", e.javaClass.simpleName)
            LostDeviceStartResult(null)
        }
    }

    fun confirm(documentNumber: String, code: String): LostDeviceConfirmResult {
        val doc = normalize(documentNumber)
        val session = findSession(doc)
        if (session == null) {
            audit(null, "LOST_DEVICE_CONFIRM_FAILED", "Confirmacion rechazada (doc ${maskDoc(doc)})")
            throw invalidCode()
        }
        try {
            otpService.verify(session.id, code.trim())
        } catch (e: ApiException) {
            audit(session.id, "LOST_DEVICE_CONFIRM_FAILED", "Confirmacion rechazada (doc ${maskDoc(doc)})")
            throw invalidCode()
        }
        val result = try {
            issuerClient.revoke(doc)
        } catch (e: Exception) {
            audit(session.id, "LOST_DEVICE_ISSUER_ERROR", "El emisor no completo la revocacion (doc ${maskDoc(doc)})")
            log.error("Fallo al llamar al emisor: {}", e.javaClass.simpleName)
            throw ApiException(
                HttpStatus.BAD_GATEWAY,
                "No pudimos completar la revocacion en este momento. Pide un codigo nuevo e intenta de nuevo.",
            )
        }
        audit(
            session.id, "LOST_DEVICE_REVOKED",
            "Revocacion solicitada (doc ${maskDoc(doc)}): revoked=${result.revoked} failed=${result.failed} wallet=${result.walletRevoked}/${result.walletFailed} blocked=${result.blocked}",
        )
        return result
    }

    private fun findSession(doc: String) =
        sessionRepository.findFirstByDocumentNumberAndStatusOrderByCreatedAtDesc(doc, OnboardingStatus.APPROVED)
            ?.takeIf { it.contactVerified }

    private fun normalize(raw: String): String {
        val doc = raw.filter { it.isDigit() }
        if (doc.length !in 5..10) throw BadRequestException("Numero de documento invalido")
        return doc
    }

    private fun invalidCode() = UnauthorizedException("Codigo invalido o vencido")

    /** Solo los ultimos 3 digitos: la auditoria no guarda la cedula completa. */
    private fun maskDoc(doc: String) = "*".repeat((doc.length - 3).coerceAtLeast(0)) + doc.takeLast(3)

    private fun audit(sessionId: UUID?, type: String, message: String) {
        auditRepository.save(AuditEvent(sessionId = sessionId, type = type, message = message))
    }
}
