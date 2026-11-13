/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver CONTEXTO-REGISTRADURIA.md, sección 2 ("Pantalla ciudadana").
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import arrow.core.raise.effect
import arrow.core.raise.fold
import com.eygraber.uri.Uri
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid.PidMsoMdocV1CredentialConfigurationId
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid.PidSdJwtVcScope
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.CedulaDocumentNumber
import eu.europa.ec.eudi.pidissuer.adapter.out.util.maskDocumentNumber
import eu.europa.ec.eudi.pidissuer.domain.CredentialConfigurationId
import eu.europa.ec.eudi.pidissuer.domain.OpenId4VciSpec
import eu.europa.ec.eudi.pidissuer.port.input.CreateCredentialsOffer
import eu.europa.ec.eudi.pidissuer.port.out.persistence.IsSubjectBlocked
import eu.europa.ec.eudi.pidissuer.port.out.qr.Dimensions
import eu.europa.ec.eudi.pidissuer.port.out.qr.Format
import eu.europa.ec.eudi.pidissuer.port.out.qr.GenerateQqCode
import eu.europa.ec.eudi.pidissuer.port.out.qr.Pixels
import eu.europa.ec.eudi.pidissuer.adapter.out.registraduria.CedulaHomologation
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.FaceMatchResult
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.OnboardingSession
import kotlinx.datetime.LocalDate
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.FaceMatcher
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.SelfieCheck
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.FindCedulaByDocumentNumber
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.OfferTickets
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.codec.multipart.FormFieldPart
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.*
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.minutes

/**
 * Pantalla ciudadana "sin login": el ciudadano escribe su número de cédula y
 * recibe un QR para pedir su PID con **cualquier wallet EUDI genérica** (a
 * diferencia del flujo automático de onboarding de [OnboardingOffersApi],
 * pensado para la wallet propia `newwallet-app`).
 *
 * Esta pantalla solo genera y muestra la oferta — la verificación real de
 * "quién es quién" ocurre después, cuando la wallet abre la oferta y
 * Keycloak pide de nuevo el número de cédula (sin contraseña: ver
 * `docker-compose/scripts/configurar-login-sin-password.py`, aplicado solo
 * al cliente OAuth2 que use esa wallet — no a todo el realm).
 */
class RegistraduriaSolicitarUi(
    private val createCredentialsOffer: CreateCredentialsOffer,
    private val findCedulaByDocumentNumber: FindCedulaByDocumentNumber,
    private val generateQrCode: GenerateQqCode,
    private val rateLimit: IpRateLimit = IpRateLimit.disabled(),
    private val isSubjectBlocked: IsSubjectBlocked = IsSubjectBlocked { false },
    private val tickets: OfferTickets,
    private val faceMatcher: FaceMatcher,
) {
    private val attempts = FaceAttempts()

    val router: RouterFunction<ServerResponse> =
        coRouter {
            // Al entrar no hay ningún formato marcado: la persona tiene que elegir.
            GET(SOLICITAR, contentType(MediaType.ALL) and accept(MediaType.TEXT_HTML)) { handleForm(null, mdoc = false, sdjwt = false) }
            POST(
                SOLICITAR,
                contentType(MediaType.MULTIPART_FORM_DATA) and accept(MediaType.TEXT_HTML),
                ::handleSubmit,
            )
        }

    private suspend fun handleForm(
        error: String?,
        mdoc: Boolean = true,
        sdjwt: Boolean = true,
    ): ServerResponse =
        ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait("registraduria-solicitar-form", mapOf("error" to error, "mdoc" to mdoc, "sdjwt" to sdjwt))

    private suspend fun handleSubmit(request: ServerRequest): ServerResponse {
        // Pantalla pública sin autenticación: cada intento cuenta, y un cliente bloqueado no llega a consultar cédulas.
        rateLimit.blocked(request)?.let { blocked ->
            log.warn("Solicitud de PID bloqueada por exceso de intentos ({} s restantes)", blocked.retryAfterSeconds)
            return ServerResponse
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", blocked.retryAfterSeconds.toString())
                .contentType(MediaType.TEXT_HTML)
                .renderAndAwait(
                    "registraduria-solicitar-form",
                    mapOf("error" to "Demasiados intentos. Intenta de nuevo en ${(blocked.retryAfterSeconds + 59) / 60} minuto(s)."),
                )
        }
        rateLimit.record(request)

        val parts = request.awaitMultipartData()
        val formData = parts.mapValues { (_, list) -> list.filterIsInstance<FormFieldPart>().map { it.value() } }
        val documentNumber = formData["documentNumber"]?.firstOrNull()?.trim().orEmpty()
        // Formatos elegidos con las casillas. Sin la marca `formatsChosen` (un cliente que no usa el formulario) se
        // ofrecen los dos, como antes. Con la marca y ninguna casilla, no hay nada que generar.
        val firstNames = formData["firstNames"]?.firstOrNull()?.trim().orEmpty()
        val lastNames = formData["lastNames"]?.firstOrNull()?.trim().orEmpty()
        val birthDate = formData["birthDate"]?.firstOrNull()?.trim()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val chosen = formData["formatsChosen"] != null
        val wantsMdoc = !chosen || formData["formats"].orEmpty().contains(FORMAT_MDOC)
        val wantsSdJwt = !chosen || formData["formats"].orEmpty().contains(FORMAT_SD_JWT)
        if (documentNumber.isBlank()) {
            return handleForm("Debes ingresar tu número de cédula", wantsMdoc, wantsSdJwt)
        }
        if (!wantsMdoc && !wantsSdJwt) {
            return handleForm("Elige al menos un formato: mDoc o SD-JWT VC.", wantsMdoc, wantsSdJwt)
        }

        if (firstNames.isBlank() || lastNames.isBlank() || birthDate == null) {
            return handleForm("Escribe tus nombres, apellidos y fecha de nacimiento como aparecen en tu cédula.", wantsMdoc, wantsSdJwt)
        }
        val selfie = (parts.getFirst("selfie") as? FilePart)?.readBytes(MAX_SELFIE_BYTES)
        if (selfie == null || selfie.isEmpty()) {
            return handleForm("Toma o sube una selfie para verificar tu identidad.", wantsMdoc, wantsSdJwt)
        }
        // Calidad de la selfie: no depende de la cédula, así que su mensaje no revela si la cédula existe.
        when (val quality = faceMatcher.checkSelfie(selfie)) {
            SelfieCheck.Ok -> Unit
            is SelfieCheck.Bad -> return handleForm("La selfie no sirve: ${quality.reason}. Inténtalo de nuevo con buena luz y de frente.", wantsMdoc, wantsSdJwt)
            is SelfieCheck.Unavailable -> return handleForm("El servicio de verificación no está disponible. Inténtalo más tarde.", wantsMdoc, wantsSdJwt)
        }
        // Tope de intentos por cédula (además del límite por IP): frena a quien prueba fotos de otra persona.
        if (!attempts.allow(documentNumber)) {
            log.warn("Demasiados intentos de verificación facial para la cédula {}", documentNumber.maskDocumentNumber())
            return handleForm(GENERIC_ERROR, wantsMdoc, wantsSdJwt)
        }

        val cedula = findCedulaByDocumentNumber(CedulaDocumentNumber(documentNumber))
        // Misma respuesta para "no existe" y "registro incompleto": el motivo solo queda en el log (enmascarado).
        if (cedula == null) {
            log.info("Solicitud de PID para cédula no encontrada")
            return handleForm(GENERIC_ERROR, wantsMdoc, wantsSdJwt)
        }
        if (isSubjectBlocked(cedula.documentNumber.value)) {
            log.info("Solicitud de PID para cédula con emisión bloqueada: {}", documentNumber.maskDocumentNumber())
            return handleForm(GENERIC_ERROR, wantsMdoc, wantsSdJwt)
        }
        if (cedula.fotoReferencia == null) {
            log.info("Solicitud de PID para cédula sin foto de referencia: {}", documentNumber.maskDocumentNumber())
            return handleForm(GENERIC_ERROR, wantsMdoc, wantsSdJwt)
        }

        // Primer factor: los datos escritos deben coincidir con los de la Registraduría (la misma homologación del onboarding).
        val declared = OnboardingSession("APPROVED", null, documentNumber, firstNames, lastNames, birthDate)
        if (!CedulaHomologation.check(declared, cedula).ok) {
            attempts.fail(documentNumber)
            log.info("Datos declarados no coinciden para la cédula {}", documentNumber.maskDocumentNumber())
            return handleForm(GENERIC_ERROR, wantsMdoc, wantsSdJwt)
        }
        // Segundo factor: la identidad se prueba comparando la selfie con la foto de referencia de la Registraduría. Misma respuesta
        // genérica si no coincide, para no dar pistas. OJO: no es prueba de vida.
        when (val result = faceMatcher.match(requireNotNull(cedula.fotoReferencia).bytes, selfie)) {
            is FaceMatchResult.Match -> {
                attempts.reset(documentNumber)
                log.info("Verificación facial aprobada para la cédula {} (score {})", documentNumber.maskDocumentNumber(), result.score)
            }
            is FaceMatchResult.NoMatch -> {
                attempts.fail(documentNumber)
                log.info("Verificación facial NO coincide para la cédula {} (score {})", documentNumber.maskDocumentNumber(), result.score)
                return handleForm(GENERIC_ERROR, wantsMdoc, wantsSdJwt)
            }
            is FaceMatchResult.Unavailable ->
                return handleForm("El servicio de verificación no está disponible. Inténtalo más tarde.", wantsMdoc, wantsSdJwt)
        }
        // El QR lleva un ticket (issuer_state) ligado a ESTA cédula: Keycloak solo autentica con ticket, sin formulario.
        val ticket = tickets.issue(cedula.documentNumber.value, TICKET_TTL)
        return effect<CreateCredentialsOffer.Error, Uri> {
            createCredentialsOffer(CreateCredentialsOffer.Request(configurationIds(wantsMdoc, wantsSdJwt), issuerState = ticket))
        }.fold(
            transform = { offerUri ->
                log.info("Oferta de PID generada para solicitud manual (cédula {})", documentNumber.maskDocumentNumber())
                context(generateQrCode) { offerUri.qrResponse(cedula.nombres, formatLabels(wantsMdoc, wantsSdJwt)) }
            },
            recover = { error ->
                log.warn("No se pudo generar la oferta para la cédula {}: {}", documentNumber.maskDocumentNumber(), error)
                handleForm("No se pudo generar la oferta de credencial. Intenta de nuevo.", wantsMdoc, wantsSdJwt)
            },
        )
    }

    companion object {
        const val SOLICITAR: String = "/registraduria/solicitar"
        /** Vigencia del ticket del QR: lo justo para escanearlo. */
        val TICKET_TTL = 5.minutes
        const val MAX_SELFIE_BYTES: Int = 4 * 1024 * 1024
        const val FORMAT_MDOC: String = "mdoc"
        const val FORMAT_SD_JWT: String = "sdjwt"

        /** Las configuraciones de PID que se ofrecen, en el orden de siempre (mDoc primero). */
        fun configurationIds(
            mdoc: Boolean,
            sdjwt: Boolean,
        ): Set<CredentialConfigurationId> =
            linkedSetOf<CredentialConfigurationId>().apply {
                if (mdoc) add(PidMsoMdocV1CredentialConfigurationId)
                if (sdjwt) add(CredentialConfigurationId(PidSdJwtVcScope.value))
            }

        fun formatLabels(
            mdoc: Boolean,
            sdjwt: Boolean,
        ): String = listOfNotNull(if (mdoc) "mDoc" else null, if (sdjwt) "SD-JWT VC" else null).joinToString(" y ")


        /**
         * Propiedad que enciende o apaga esta pantalla (por defecto encendida). Apagada, la ruta no se registra y
         * responde 404. Pensada para apagarla en cualquier entorno expuesto: no pide autenticación.
         */
        const val ENABLED_PROPERTY: String = "issuer.registraduria.solicitar.enabled"

        const val GENERIC_ERROR: String =
            "No fue posible generar la solicitud para esa cédula. Verifica el número o comunícate con la Registraduría."
        private val log = LoggerFactory.getLogger(RegistraduriaSolicitarUi::class.java)
    }
}

context(generateQrCode: GenerateQqCode)
private suspend fun Uri.qrResponse(
    nombres: String,
    formatos: String,
): ServerResponse {
    val uri = this@qrResponse
    val qrCode = generateQrCode(uri, Format.PNG, Dimensions(Pixels(300u), Pixels(300u)))
    return ServerResponse
        .ok()
        .contentType(MediaType.TEXT_HTML)
        .renderAndAwait(
            "registraduria-solicitar-qr",
            mapOf(
                "nombres" to nombres,
                "formatos" to formatos,
                "uri" to uri.toString(),
                "qrCode" to Base64.encode(qrCode),
                "qrCodeMediaType" to "image/png",
                "openid4VciVersion" to OpenId4VciSpec.VERSION,
            ),
        )
}


/** Intentos fallidos de verificación facial por cédula: [max] por ventana; al superarlos se bloquea hasta que pase. */
private class FaceAttempts(
    private val max: Int = 5,
    private val windowMillis: Long = 60 * 60 * 1000L,
) {
    private class Entry(var failures: Int, var since: Long)

    private val map = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    fun allow(key: String): Boolean {
        val e = map[key] ?: return true
        synchronized(e) {
            if (System.currentTimeMillis() - e.since > windowMillis) {
                map.remove(key, e)
                return true
            }
            return e.failures < max
        }
    }

    fun fail(key: String) {
        val now = System.currentTimeMillis()
        val e = map.computeIfAbsent(key) { Entry(0, now) }
        synchronized(e) { e.failures++ }
    }

    fun reset(key: String) {
        map.remove(key)
    }
}

/** Lee el archivo subido; `null` si supera [limit] bytes. */
private suspend fun FilePart.readBytes(limit: Int): ByteArray? =
    runCatching {
        org.springframework.core.io.buffer.DataBufferUtils.join(content(), limit).awaitSingleOrNull()?.let { buffer ->
            try {
                ByteArray(buffer.readableByteCount()).also { buffer.read(it) }
            } finally {
                org.springframework.core.io.buffer.DataBufferUtils.release(buffer)
            }
        } ?: ByteArray(0)
    }.getOrNull()
