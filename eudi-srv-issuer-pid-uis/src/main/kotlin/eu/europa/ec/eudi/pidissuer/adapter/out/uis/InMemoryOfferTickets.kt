/*
 * Copyright (c) 2023-2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.uis

import eu.europa.ec.eudi.pidissuer.port.out.uis.OfferTickets
import eu.europa.ec.eudi.pidissuer.port.out.uis.TicketRedemption
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Tickets de un solo uso (TTL por defecto 10 min), en memoria: un reinicio del issuer los invalida
 * (aceptable: el usuario repite el paso "Bienvenido a la wallet").
 */
class InMemoryOfferTickets(
    private val clock: Clock,
    private val ttl: Duration = 10.minutes,
) : OfferTickets {
    private data class Entry(
        val documentNumber: String,
        val expiresAt: Instant,
    )

    private val tickets = ConcurrentHashMap<String, Entry>()
    private val random = SecureRandom()

    override fun issue(documentNumber: String, ttl: Duration?): String {
        purge()
        val bytes = ByteArray(32).also(random::nextBytes)
        val ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        tickets[ticket] = Entry(documentNumber, clock.now() + (ttl ?: this.ttl))
        return ticket
    }

    override fun redeem(ticket: String): TicketRedemption {
        // remove() es atómico: dos canjes simultáneos no pueden tener éxito ambos.
        val entry = tickets.remove(ticket) ?: return TicketRedemption.NotFound
        return if (clock.now() > entry.expiresAt) TicketRedemption.Expired else TicketRedemption.Redeemed(entry.documentNumber)
    }

    /** Elimina tickets vencidos hace más de una hora (se conservan un rato para poder responder 410). */
    private fun purge() {
        val limit = clock.now() - 1.hours
        tickets.entries.removeIf { it.value.expiresAt < limit }
    }
}
