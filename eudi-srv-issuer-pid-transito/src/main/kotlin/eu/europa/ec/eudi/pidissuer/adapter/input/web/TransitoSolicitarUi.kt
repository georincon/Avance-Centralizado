/*
 * Código propio de este fork (emulador de la Dirección de Tránsito de Bucaramanga).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver README.md, sección 2 ("Pantalla ciudadana").
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import eu.europa.ec.eudi.pidissuer.adapter.out.transito.CategoriaLicencia
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.Licencia
import arrow.core.raise.effect
import arrow.core.raise.fold
import com.eygraber.uri.Uri
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.mdl.mdlConfigurationId
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.LicenciaDocumentNumber
import eu.europa.ec.eudi.pidissuer.adapter.out.util.maskDocumentNumber
import eu.europa.ec.eudi.pidissuer.domain.CredentialConfigurationId
import eu.europa.ec.eudi.pidissuer.domain.OpenId4VciSpec
import eu.europa.ec.eudi.pidissuer.port.input.CreateCredentialsOffer
import eu.europa.ec.eudi.pidissuer.port.out.persistence.IsSubjectBlocked
import eu.europa.ec.eudi.pidissuer.port.out.qr.Dimensions
import eu.europa.ec.eudi.pidissuer.port.out.qr.Format
import eu.europa.ec.eudi.pidissuer.port.out.qr.GenerateQqCode
import eu.europa.ec.eudi.pidissuer.port.out.qr.Pixels
import eu.europa.ec.eudi.pidissuer.port.out.transito.FindLicenciaByDocumentNumber
import eu.europa.ec.eudi.pidissuer.adapter.out.transito.LicenciaHomologation
import eu.europa.ec.eudi.pidissuer.port.out.transito.FaceMatchResult
import eu.europa.ec.eudi.pidissuer.port.out.transito.FaceMatcher
import eu.europa.ec.eudi.pidissuer.port.out.transito.OfferTickets
import eu.europa.ec.eudi.pidissuer.port.out.transito.OnboardingSession
import eu.europa.ec.eudi.pidissuer.port.out.transito.SelfieCheck
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.datetime.LocalDate
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.codec.multipart.FormFieldPart
import kotlin.time.Duration.Companion.minutes
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.*
import kotlin.io.encoding.Base64

/**
 * Pantalla ciudadana "sin login": el ciudadano escribe su número de cédula y recibe un QR para pedir su
 * licencia de conducción móvil (mDL, ISO 18013-5) con **cualquier wallet EUDI genérica** (a diferencia del flujo
 * automático de onboarding de [OnboardingOffersApi], pensado para la wallet propia `newwallet-app`).
 *
 * Esta pantalla solo genera y muestra la oferta — la verificación real de "quién es quién" ocurre después, cuando
 * la wallet abre la oferta y Keycloak pide de nuevo el número de cédula (sin contraseña: ver
 * `docker-compose/scripts/configurar-login-sin-password.py`).
 *
 * Solo se ofrece a titulares con licencia ACTIVA y al menos una categoría vigente (ver [Licencia.esEmisible]).
 */
class TransitoSolicitarUi(
    private val createCredentialsOffer: CreateCredentialsOffer,
    private val findLicenciaByDocumentNumber: FindLicenciaByDocumentNumber,
    private val generateQrCode: GenerateQqCode,
    private val rateLimit: IpRateLimit = IpRateLimit.disabled(),
    private val isSubjectBlocked: IsSubjectBlocked = IsSubjectBlocked { false },
    private val tickets: OfferTickets,
    private val faceMatcher: FaceMatcher,
) {
    private val attempts = FaceAttempts()

    val router: RouterFunction<ServerResponse> =
        coRouter {
            GET(SOLICITAR, contentType(MediaType.ALL) and accept(MediaType.TEXT_HTML)) { handleForm(null, emptySet()) }
            POST(
                SOLICITAR,
                contentType(MediaType.MULTIPART_FORM_DATA) and accept(MediaType.TEXT_HTML),
                ::handleSubmit,
            )
        }

    /** [selected]: códigos de las categorías marcadas (se conservan al volver a mostrar el formulario con un error). */
    private suspend fun handleForm(
        error: String?,
        selected: Set<String>,
    ): ServerResponse =
        ServerResponse
            .ok()
            .contentType(MediaType.TEXT_HTML)
            .renderAndAwait(
                "transito-solicitar-form",
                mapOf(
                    "error" to error,
                    "categorias" to
                        CategoriaLicencia.entries.map {
                            mapOf("codigo" to it.codigo, "descripcion" to it.descripcion, "marcada" to (it.codigo in selected))
                        },
                ),
            )

    private suspend fun handleSubmit(request: ServerRequest): ServerResponse {
        // Pantalla pública sin autenticación: cada intento cuenta, y un cliente bloqueado no llega a consultar cédulas.
        rateLimit.blocked(request)?.let { blocked ->
            log.warn("Solicitud de mDL bloqueada por exceso de intentos ({} s restantes)", blocked.retryAfterSeconds)
            return ServerResponse
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", blocked.retryAfterSeconds.toString())
                .contentType(MediaType.TEXT_HTML)
                .renderAndAwait(
                    "transito-solicitar-form",
                    mapOf(
                        "error" to "Demasiados intentos. Intenta de nuevo en ${(blocked.retryAfterSeconds + 59) / 60} minuto(s).",
                        "categorias" to CategoriaLicencia.entries.map { mapOf("codigo" to it.codigo, "descripcion" to it.descripcion, "marcada" to false) },
                    ),
                )
        }
        rateLimit.record(request)

        val parts = request.awaitMultipartData()
        val formData = parts.mapValues { (_, list) -> list.filterIsInstance<FormFieldPart>().map { it.value() } }
        val documentNumber = formData["documentNumber"]?.firstOrNull()?.trim().orEmpty()
        val firstNames = formData["firstNames"]?.firstOrNull()?.trim().orEmpty()
        val lastNames = formData["lastNames"]?.firstOrNull()?.trim().orEmpty()
        val birthDate = formData["birthDate"]?.firstOrNull()?.trim()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        // Categorías elegidas con las casillas. Sin la marca `categoriesChosen` (un cliente que no usa el formulario) se
        // ofrecen todas las vigentes, como antes. Con la marca y ninguna casilla, no hay nada que generar.
        val chosenMark = formData["categoriesChosen"] != null
        val selected = formData["categories"].orEmpty().map { it.trim().uppercase() }.toSet()
        val wanted: Set<CategoriaLicencia>? =
            if (!chosenMark) null else CategoriaLicencia.entries.filterTo(linkedSetOf()) { it.codigo in selected }
        if (documentNumber.isBlank()) return handleForm("Debes ingresar tu número de cédula", selected)
        if (wanted != null && wanted.isEmpty()) return handleForm("Elige al menos una categoría de licencia.", selected)
        if (firstNames.isBlank() || lastNames.isBlank() || birthDate == null) {
            return handleForm("Escribe tus nombres, apellidos y fecha de nacimiento como aparecen en tu cédula.", selected)
        }
        val selfie = (parts.getFirst("selfie") as? FilePart)?.readBytes(MAX_SELFIE_BYTES)
        if (selfie == null || selfie.isEmpty()) return handleForm("Toma o sube una selfie para verificar tu identidad.", selected)
        // Calidad de la selfie: no depende de la cédula, así que su mensaje no revela si la cédula existe.
        when (val quality = faceMatcher.checkSelfie(selfie)) {
            SelfieCheck.Ok -> Unit
            is SelfieCheck.Bad -> return handleForm("La selfie no sirve: ${quality.reason}. Inténtalo de nuevo con buena luz y de frente.", selected)
            is SelfieCheck.Unavailable -> return handleForm("El servicio de verificación no está disponible. Inténtalo más tarde.", selected)
        }
        // Tope de intentos por cédula (además del límite por IP): frena a quien prueba fotos de otra persona.
        if (!attempts.allow(documentNumber)) {
            log.warn("Demasiados intentos de verificación facial para la cédula {}", documentNumber.maskDocumentNumber())
            return handleForm(GENERIC_ERROR, selected)
        }

        val licencia = findLicenciaByDocumentNumber(LicenciaDocumentNumber(documentNumber))
        // Misma respuesta para "no existe", "no activa", "sin vigencia" y "registro incompleto": el motivo solo queda
        // en el log (enmascarado).
        if (licencia == null) {
            log.info("Solicitud de mDL para cédula no encontrada")
            return handleForm(GENERIC_ERROR, selected)
        }
        if (isSubjectBlocked(licencia.documentNumber.value)) {
            log.info("Solicitud de mDL con emisión bloqueada: {}", documentNumber.maskDocumentNumber())
            return handleForm(GENERIC_ERROR, selected)
        }
        if (!licencia.esEmisible()) {
            log.info(
                "Solicitud de mDL para licencia no emisible ({}): {}",
                licencia.estado,
                documentNumber.maskDocumentNumber(),
            )
            return handleForm(GENERIC_ERROR, selected)
        }

        if (licencia.fotoReferencia == null) {
            log.info("Solicitud de mDL para licencia sin foto de referencia: {}", documentNumber.maskDocumentNumber())
            return handleForm(GENERIC_ERROR, selected)
        }
        // Primer factor: los datos escritos deben coincidir con los de la licencia (la misma homologación del onboarding).
        val declared = OnboardingSession("APPROVED", null, documentNumber, firstNames, lastNames, birthDate)
        if (!LicenciaHomologation.check(declared, licencia).ok) {
            attempts.fail(documentNumber)
            log.info("Datos declarados no coinciden para la cédula {}", documentNumber.maskDocumentNumber())
            return handleForm(GENERIC_ERROR, selected)
        }
        // Segundo factor: la selfie se compara con la foto de referencia de la licencia. OJO: no es prueba de vida.
        when (val result = faceMatcher.match(licencia.fotoReferencia.bytes, selfie)) {
            is FaceMatchResult.Match -> {
                attempts.reset(documentNumber)
                log.info("Verificación facial aprobada para la cédula {} (score {})", documentNumber.maskDocumentNumber(), result.score)
            }
            is FaceMatchResult.NoMatch -> {
                attempts.fail(documentNumber)
                log.info("Verificación facial NO coincide para la cédula {} (score {})", documentNumber.maskDocumentNumber(), result.score)
                return handleForm(GENERIC_ERROR, selected)
            }
            is FaceMatchResult.Unavailable -> return handleForm("El servicio de verificación no está disponible. Inténtalo más tarde.", selected)
        }
        // Solo las categorías elegidas que el titular tiene vigentes. Aquí ya se verificó su identidad (datos y selfie),
        // así que se puede decir con claridad cuáles no tiene.
        val configurationIds = configurationIdsDe(licencia, wanted)
        if (configurationIds.isEmpty()) {
            log.info("Solicitud de mDL sin categorías vigentes entre las elegidas: {}", documentNumber.maskDocumentNumber())
            return handleForm(
                "No tienes licencia vigente en las categorías elegidas. Tus categorías vigentes: " +
                    licencia.categoriasVigentes(java.time.LocalDate.now()).joinToString(", ") { it.categoria.codigo } + ".",
                selected,
            )
        }
        // El QR lleva un ticket (issuer_state) ligado a ESTA cédula: Keycloak solo autentica con ticket, sin formulario.
        val ticket = tickets.issue(licencia.documentNumber.value, TICKET_TTL)
        return effect<CreateCredentialsOffer.Error, Uri> {
            createCredentialsOffer(CreateCredentialsOffer.Request(configurationIds, issuerState = ticket))
        }.fold(
            transform = { offerUri ->
                log.info("Oferta de mDL generada para solicitud manual (cédula {})", documentNumber.maskDocumentNumber())
                context(generateQrCode) { offerUri.qrResponse(licencia.nombres, configurationIds.joinToString(", ") { it.value.substringAfterLast('.') }) }
            },
            recover = { error ->
                log.warn("No se pudo generar la oferta para la cédula {}: {}", documentNumber.maskDocumentNumber(), error)
                handleForm("No se pudo generar la oferta de credencial. Intenta de nuevo.", selected)
            },
        )
    }

    companion object {
        const val SOLICITAR: String = "/transito/solicitar"
        /** Vigencia del ticket del QR: lo justo para escanearlo. */
        val TICKET_TTL = 5.minutes
        const val MAX_SELFIE_BYTES: Int = 4 * 1024 * 1024
        /** El Tránsito ofrece un mDL (mso_mdoc) por cada categoría vigente de la licencia. */
        fun configurationIdsDe(
            licencia: Licencia,
            elegidas: Set<CategoriaLicencia>? = null,
        ): Set<CredentialConfigurationId> =
            licencia.categoriasVigentes(java.time.LocalDate.now())
                .filter { elegidas == null || it.categoria in elegidas }
                .mapTo(linkedSetOf()) { mdlConfigurationId(it.categoria) }

        /**
         * Propiedad que enciende o apaga esta pantalla (por defecto encendida). Apagada, la ruta no se registra y
         * responde 404. Pensada para apagarla en cualquier entorno expuesto: no pide autenticación.
         */
        const val ENABLED_PROPERTY: String = "issuer.transito.solicitar.enabled"

        const val GENERIC_ERROR: String =
            "No fue posible generar la solicitud para esa cédula. Verifica el número o comunícate con la Dirección de Tránsito."
        private val log = LoggerFactory.getLogger(TransitoSolicitarUi::class.java)
    }
}

context(generateQrCode: GenerateQqCode)
private suspend fun Uri.qrResponse(
    nombres: String,
    categorias: String,
): ServerResponse {
    val uri = this@qrResponse
    val qrCode = generateQrCode(uri, Format.PNG, Dimensions(Pixels(300u), Pixels(300u)))
    return ServerResponse
        .ok()
        .contentType(MediaType.TEXT_HTML)
        .renderAndAwait(
            "transito-solicitar-qr",
            mapOf(
                "nombres" to nombres,
                "categorias" to categorias,
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
