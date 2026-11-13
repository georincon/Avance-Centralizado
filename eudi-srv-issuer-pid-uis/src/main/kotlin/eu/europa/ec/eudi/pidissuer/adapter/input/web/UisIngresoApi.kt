/*
 * Código propio de este fork (emulador de la UIS). No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import arrow.core.raise.effect
import arrow.core.raise.fold
import com.eygraber.uri.Uri
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.academic.AcademicCredentialScope
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.academic.CarnetCredentialScope
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.academic.GetAcademicDataFromDatabase
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.PidIngresos
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.PidResult
import eu.europa.ec.eudi.pidissuer.adapter.out.uis.PidVerifierClient
import eu.europa.ec.eudi.pidissuer.domain.CredentialConfigurationId
import eu.europa.ec.eudi.pidissuer.port.input.CreateCredentialsOffer
import eu.europa.ec.eudi.pidissuer.port.out.uis.OfferTickets
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.*
import kotlin.time.Duration.Companion.minutes

/**
 * API de la wallet para pedir credenciales académicas SIN contraseña ni navegador:
 *  1. `POST /issuer/api/ingreso`: crea una solicitud de PID en el verifier y devuelve su `requestUri` (openid4vp://…).
 *     La wallet lo procesa como cualquier solicitud de presentación: el usuario ve qué comparte y lo confirma.
 *  2. `GET /issuer/api/ingreso/{id}`: la wallet consulta; cuando el verifier validó el PID, devuelve los programas del
 *     estudiante cuyo número de cédula coincide con el del PID.
 *  3. `POST /issuer/api/ofertas`: con el programa elegido, devuelve la oferta de credencial con un ticket (issuer_state) de un
 *     solo uso ligado a esa cédula; Keycloak lo canjea y la wallet obtiene el `code` sin formulario.
 * La identidad sale del PID presentado (firmado por la Registraduría y verificado), nunca de lo que escriba quien llama.
 */
class UisIngresoApi(
    private val verifier: PidVerifierClient,
    private val ingresos: PidIngresos,
    private val academic: GetAcademicDataFromDatabase,
    private val createCredentialsOffer: CreateCredentialsOffer,
    private val tickets: OfferTickets,
) {
    val router: RouterFunction<ServerResponse> =
        coRouter {
            POST(INGRESO, ::handleStart)
            GET(INGRESO_ID, ::handleStatus)
            POST(OFERTAS, ::handleOffer)
        }

    private suspend fun handleStart(request: ServerRequest): ServerResponse =
        runCatching { verifier.start() }.fold(
            onSuccess = {
                val ing = ingresos.crear(it.transactionId, it.requestUri)
                json(HttpStatus.OK, StartTO(ing.id, ing.requestUri))
            },
            onFailure = {
                log.error("No se pudo crear la solicitud de PID", it)
                json(HttpStatus.BAD_GATEWAY, IngresoErrorTO("VERIFIER_UNAVAILABLE"))
            },
        )

    private suspend fun handleStatus(request: ServerRequest): ServerResponse {
        val ing = ingresos.obtener(request.pathVariable("id")) ?: return json(HttpStatus.NOT_FOUND, IngresoErrorTO("INGRESO_NOT_FOUND"))
        if (ing.documentNumber == null) {
            when (val r = verifier.result(ing.transactionId)) {
                PidResult.Pending -> return json(HttpStatus.OK, StatusTO("esperando"))
                is PidResult.Failed -> {
                    ingresos.cerrar(ing.id)
                    return json(HttpStatus.OK, StatusTO("error", motivo = r.reason))
                }
                is PidResult.Ok -> ing.documentNumber = r.documentNumber
            }
        }
        val student = academic.findStudentByDocumentNumber(ing.documentNumber!!)
        if (student == null) {
            ingresos.cerrar(ing.id)
            return json(HttpStatus.OK, StatusTO("no_estudiante", motivo = "No hay un estudiante de la UIS con esa cédula."))
        }
        val programas = academic.findStudentPrograms(student.usuarioAutenticacion).map {
            ProgramTO(it.historialId, it.programaId, it.nombrePrograma, it.nivelAcademico, it.estadoAcademico, it.tituloOtorgado)
        }
        return json(HttpStatus.OK, StatusTO("listo", nombre = "${student.nombres} ${student.apellidos}", programas = programas))
    }

    private suspend fun handleOffer(request: ServerRequest): ServerResponse {
        val body = runCatching { request.awaitBody<OfferRequestTO>() }.getOrNull() ?: return json(HttpStatus.BAD_REQUEST, IngresoErrorTO("INVALID_REQUEST"))
        val ing = body.id?.let(ingresos::obtener) ?: return json(HttpStatus.NOT_FOUND, IngresoErrorTO("INGRESO_NOT_FOUND"))
        val doc = ing.documentNumber ?: return json(HttpStatus.FORBIDDEN, IngresoErrorTO("NOT_VERIFIED"))
        val student = academic.findStudentByDocumentNumber(doc) ?: return json(HttpStatus.NOT_FOUND, IngresoErrorTO("STUDENT_NOT_FOUND"))
        val programa = academic.findStudentPrograms(student.usuarioAutenticacion).firstOrNull { it.historialId == body.historialId }
            ?: return json(HttpStatus.NOT_FOUND, IngresoErrorTO("PROGRAM_NOT_FOUND"))
        val carnet = body.credencial == "carnet"
        if (carnet && programa.estadoAcademico != "ACTIVO") return json(HttpStatus.FORBIDDEN, IngresoErrorTO("CARNET_ONLY_ACTIVE"))

        academic.saveSelection(student.usuarioAutenticacion, student.numeroIdentificacion, programa.programaId, programa.historialId)
        val scope = if (carnet) CarnetCredentialScope.value else AcademicCredentialScope.value
        val ticket = tickets.issue(student.numeroIdentificacion, 10.minutes)
        return effect<CreateCredentialsOffer.Error, Uri> {
            createCredentialsOffer(CreateCredentialsOffer.Request(setOf(CredentialConfigurationId(scope)), null, issuerState = ticket))
        }.fold(
            transform = { json(HttpStatus.OK, OfferTO(it.toString())) },
            recover = { log.warn("No se pudo crear la oferta: {}", it); json(HttpStatus.INTERNAL_SERVER_ERROR, IngresoErrorTO("OFFER_ERROR")) },
        )
    }

    private suspend inline fun <reified T> json(status: HttpStatus, body: T): ServerResponse =
        ServerResponse.status(status).contentType(MediaType.APPLICATION_JSON).bodyValueAndAwait(body as Any)

    companion object {
        private val log = LoggerFactory.getLogger(UisIngresoApi::class.java)
        const val INGRESO: String = "/issuer/api/ingreso"
        const val INGRESO_ID: String = "/issuer/api/ingreso/{id}"
        const val OFERTAS: String = "/issuer/api/ofertas"
    }
}

@Serializable private data class StartTO(@SerialName("id") val id: String, @SerialName("requestUri") val requestUri: String)

@Serializable
private data class ProgramTO(
    @SerialName("historialId") val historialId: Long,
    @SerialName("programaId") val programaId: Long,
    @SerialName("nombre") val nombre: String,
    @SerialName("nivel") val nivel: String,
    @SerialName("estado") val estado: String,
    @SerialName("titulo") val titulo: String,
)

@Serializable
private data class StatusTO(
    @SerialName("estado") val estado: String,
    @SerialName("motivo") val motivo: String? = null,
    @SerialName("nombre") val nombre: String? = null,
    @SerialName("programas") val programas: List<ProgramTO>? = null,
)

@Serializable private data class OfferRequestTO(@SerialName("id") val id: String? = null, @SerialName("historialId") val historialId: Long = 0, @SerialName("credencial") val credencial: String? = null)

@Serializable private data class OfferTO(@SerialName("offerUri") val offerUri: String)

@Serializable private data class IngresoErrorTO(@SerialName("code") val code: String)
