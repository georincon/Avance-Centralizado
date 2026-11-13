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
package eu.europa.ec.eudi.pidissuer.adapter.out.registraduria

import eu.europa.ec.eudi.pidissuer.port.out.registraduria.TicketRedemption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class InMemoryOfferTicketsTest {
    private class MutableClock(
        var now: Instant = Instant.parse("2026-10-16T12:00:00Z"),
    ) : Clock {
        override fun now(): Instant = now
    }

    @Test
    fun `a ticket can be redeemed only once`() {
        val tickets = InMemoryOfferTickets(MutableClock())
        val ticket = tickets.issue("12345678")
        assertEquals(TicketRedemption.Redeemed("12345678"), tickets.redeem(ticket))
        assertEquals(TicketRedemption.NotFound, tickets.redeem(ticket))
    }

    @Test
    fun `unknown tickets are not found`() {
        assertEquals(TicketRedemption.NotFound, InMemoryOfferTickets(MutableClock()).redeem("nope"))
    }

    @Test
    fun `tickets expire after the ttl`() {
        val clock = MutableClock()
        val tickets = InMemoryOfferTickets(clock, ttl = 10.minutes)
        val ticket = tickets.issue("12345678")
        clock.now += 10.minutes + 1.minutes
        assertEquals(TicketRedemption.Expired, tickets.redeem(ticket))
    }

    @Test
    fun `each issued ticket is unique`() {
        val tickets = InMemoryOfferTickets(MutableClock())
        assertNotEquals(tickets.issue("1"), tickets.issue("1"))
    }
}
