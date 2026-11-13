/*
 * Código propio de este fork (emulador de la UIS). No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.academic.GetAcademicDataFromDatabase
import eu.europa.ec.eudi.pidissuer.port.out.uis.OfferTickets
import eu.europa.ec.eudi.pidissuer.port.out.uis.TicketRedemption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.*
import java.security.MessageDigest

/**
 * `POST /internal/v1/tickets/redeem` (interno, `X-Api-Key`): lo llama Keycloak para identificar al estudiante a partir del
 * `issuer_state` de la oferta. No se expone fuera de la red docker (haproxy lo bloquea).
 */
class TicketsApi(
    private val tickets: OfferTickets,
    private val academic: GetAcademicDataFromDatabase,
    private val apiKey: String?,
) {
    val router: RouterFunction<ServerResponse> =
        coRouter {
            POST(REDEEM, contentType(MediaType.APPLICATION_JSON), ::handleRedeem)
        }

    private suspend fun handleRedeem(request: ServerRequest): ServerResponse {
        val expected = apiKey
        if (expected.isNullOrBlank()) return error(HttpStatus.SERVICE_UNAVAILABLE, "TICKETS_DISABLED")
        val provided = request.headers().firstHeader("X-Api-Key").orEmpty()
        if (!MessageDigest.isEqual(provided.toByteArray(), expected.toByteArray())) return error(HttpStatus.UNAUTHORIZED, "INVALID_API_KEY")
        val ticket = runCatching { request.awaitBody<RedeemRequest>() }.getOrNull()?.ticket?.trim()
        if (ticket.isNullOrBlank()) return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST")
        return when (val r = tickets.redeem(ticket)) {
            TicketRedemption.NotFound -> error(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND")
            TicketRedemption.Expired -> error(HttpStatus.GONE, "TICKET_EXPIRED")
            is TicketRedemption.Redeemed -> {
                val s = academic.findStudentByDocumentNumber(r.documentNumber) ?: return error(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND")
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                    .bodyValueAndAwait(RedeemResponse(s.numeroIdentificacion, s.nombres, s.apellidos))
            }
        }
    }

    private suspend fun error(status: HttpStatus, code: String) =
        ServerResponse.status(status).contentType(MediaType.APPLICATION_JSON).bodyValueAndAwait(ErrorTO(code))

    companion object {
        const val REDEEM: String = "/internal/v1/tickets/redeem"
    }
}

@Serializable
private data class RedeemRequest(@SerialName("ticket") val ticket: String? = null)

@Serializable
private data class RedeemResponse(
    @SerialName("documentNumber") val documentNumber: String,
    @SerialName("givenName") val givenName: String,
    @SerialName("familyName") val familyName: String,
)

@Serializable
private data class ErrorTO(@SerialName("code") val code: String)
