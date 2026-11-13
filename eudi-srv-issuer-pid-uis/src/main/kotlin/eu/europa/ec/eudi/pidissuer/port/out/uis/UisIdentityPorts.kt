/*
 * Código propio de este fork (emulador de la UIS). No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.port.out.uis

import kotlin.time.Duration
import kotlin.time.Instant

/** Ticket de un solo uso que liga una oferta de credenciales a UN estudiante (su número de identificación). */
sealed interface TicketRedemption {
    data class Redeemed(val documentNumber: String) : TicketRedemption

    data object NotFound : TicketRedemption

    data object Expired : TicketRedemption
}

interface OfferTickets {
    /** [ttl] opcional: vigencia propia del ticket (por defecto, la del almacén). */
    fun issue(documentNumber: String, ttl: Duration? = null): String

    fun redeem(ticket: String): TicketRedemption
}

/** Código de activación recién generado: se muestra una sola vez al funcionario, no se guarda en claro. */
data class IssuedActivationCode(val code: String, val expiresAt: Instant)

/**
 * Código de activación de un solo uso que entrega el registro de la UIS (en ventanilla o por un canal verificado) a un
 * estudiante que NO puede probar su identidad con un PID (extranjeros: CE, PAS, PPT, PEP). Sirve solo para su número de
 * identificación, vence, se consume al usarse y se invalida tras varios intentos fallidos.
 */
interface ActivationCodes {
    fun issue(documentNumber: String): IssuedActivationCode

    fun verifyAndConsume(documentNumber: String, code: String): Boolean
}
