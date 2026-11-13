package com.onboarding.service

import com.onboarding.common.ConflictException
import com.onboarding.common.NotFoundException
import com.onboarding.common.UnauthorizedException
import com.onboarding.domain.ContactChannel
import com.onboarding.domain.OtpChallenge
import com.onboarding.provider.OtpProvider
import com.onboarding.provider.OtpSendResult
import com.onboarding.repository.OtpChallengeRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class OtpIssueResult(
    val challenge: OtpChallenge,
    val sendResult: OtpSendResult,
    val code: String,
)

@Service
class OtpService(
    private val otpRepository: OtpChallengeRepository,
    private val otpProvider: OtpProvider,
    @Value("\${onboarding.otp.length}") private val codeLength: Int,
    @Value("\${onboarding.otp.ttl-minutes}") private val ttlMinutes: Long,
    @Value("\${onboarding.otp.max-attempts}") private val maxAttempts: Int,
    @Value("\${onboarding.otp.hash-pepper}") private val pepper: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    @Transactional
    fun issue(sessionId: UUID, destination: String, channel: ContactChannel): OtpIssueResult {
        val code = generateCode()
        val challenge = OtpChallenge(
            sessionId = sessionId,
            channel = channel,
            destination = destination,
            codeHash = hashCode(code),
            maxAttempts = maxAttempts,
            expiresAt = Instant.now().plusSeconds(ttlMinutes * 60),
        )
        otpRepository.save(challenge)

        val sendResult = otpProvider.send(destination, channel, code)
        log.info("OTP emitido para sesion {} via {} ({})", sessionId, channel, sendResult.provider)
        return OtpIssueResult(challenge, sendResult, code)
    }

    /**
     * Verifica el ultimo OTP de la sesion. Lanza excepcion si es invalido o expiro.
     * noRollbackFor: el intento fallido debe quedar registrado aunque se lance la excepcion.
     */
    @Transactional(noRollbackFor = [UnauthorizedException::class])
    fun verify(sessionId: UUID, code: String) {
        val challenge = otpRepository.findFirstBySessionIdOrderByCreatedAtDesc(sessionId)
            ?: throw NotFoundException("No hay un codigo OTP emitido para esta sesion")
        val now = Instant.now()
        when {
            challenge.verified -> throw ConflictException("El codigo ya fue verificado")
            challenge.isExpired(now) -> throw ConflictException("El codigo OTP expiro, solicita uno nuevo")
            challenge.isLocked -> throw ConflictException("Demasiados intentos fallidos, solicita un nuevo codigo")
        }
        if (!constantTimeEquals(challenge.codeHash, hashCode(code))) {
            challenge.attempts += 1
            otpRepository.save(challenge)
            val remaining = (challenge.maxAttempts - challenge.attempts).coerceAtLeast(0)
            throw UnauthorizedException("Codigo OTP incorrecto. Intentos restantes: $remaining")
        }
        challenge.verified = true
        challenge.verifiedAt = now
        otpRepository.save(challenge)
    }

    private fun generateCode(): String {
        val upper = Math.pow(10.0, codeLength.toDouble()).toLong()
        return String.format("%0${codeLength}d", random.nextLong(upper))
    }

    private fun hashCode(code: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pepper.toByteArray(), "HmacSHA256"))
        return mac.doFinal(code.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
