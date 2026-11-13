/*
 * Copyright (c) 2023-2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web.security

import eu.europa.ec.eudi.pidissuer.adapter.input.web.AttemptLimiter
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class AdminLoginRateLimitWebFilterTest {
    private val loginPath = "/transito/admin/login"

    private fun filter(max: Int = 2) = AdminLoginRateLimitWebFilter(AttemptLimiter(max, 15.minutes, Clock.System), loginPath)

    /** Simula el formulario de login de Spring: redirige a `login?error` si falla y a la lista si acierta. */
    private class FakeLogin(
        private val succeeds: Boolean,
    ) : WebFilterChain {
        var calls = 0

        override fun filter(exchange: ServerWebExchange): Mono<Void> {
            calls++
            exchange.response.statusCode = HttpStatus.SEE_OTHER
            exchange.response.headers.location =
                URI.create(if (succeeds) "/transito/admin/ciudadanos" else "/transito/admin/login?error")
            return exchange.response.setComplete()
        }
    }

    private fun exchange(
        user: String,
        path: String = loginPath,
        post: Boolean = true,
    ): MockServerWebExchange =
        MockServerWebExchange.from(
            if (post) {
                MockServerHttpRequest
                    .post(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("username=$user&password=x")
            } else {
                MockServerHttpRequest.get(path).build()
            },
        )

    private fun run(
        filter: AdminLoginRateLimitWebFilter,
        chain: FakeLogin,
        exchange: MockServerWebExchange,
    ): MockServerWebExchange {
        filter.filter(exchange, chain).block()
        return exchange
    }

    @Test
    fun `blocks a user after too many failed logins without reaching the login form`() {
        val filter = filter(max = 2)
        val wrong = FakeLogin(succeeds = false)
        repeat(2) { assertEquals(HttpStatus.SEE_OTHER, run(filter, wrong, exchange("admin")).response.statusCode) }
        assertEquals(2, wrong.calls)

        val blocked = run(filter, wrong, exchange("admin"))

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.response.statusCode)
        assertNotNull(blocked.response.headers.getFirst("Retry-After"))
        assertEquals(2, wrong.calls, "a blocked request must not reach the login form")
    }

    @Test
    fun `usernames are compared ignoring case`() {
        val filter = filter(max = 1)
        val wrong = FakeLogin(succeeds = false)
        run(filter, wrong, exchange("Admin"))
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, run(filter, wrong, exchange("aDMIN")).response.statusCode)
    }

    @Test
    fun `another user is not affected`() {
        val filter = filter(max = 1)
        val wrong = FakeLogin(succeeds = false)
        run(filter, wrong, exchange("admin"))
        assertEquals(HttpStatus.SEE_OTHER, run(filter, wrong, exchange("otro")).response.statusCode)
    }

    @Test
    fun `a successful login clears the counter`() {
        val filter = filter(max = 2)
        val wrong = FakeLogin(succeeds = false)
        val right = FakeLogin(succeeds = true)
        run(filter, wrong, exchange("admin"))
        run(filter, right, exchange("admin"))
        run(filter, wrong, exchange("admin"))

        // Sin el reinicio, este tercer intento ya estaría bloqueado.
        assertEquals(HttpStatus.SEE_OTHER, run(filter, wrong, exchange("admin")).response.statusCode)
    }

    @Test
    fun `other paths and methods are ignored`() {
        val filter = filter(max = 1)
        val wrong = FakeLogin(succeeds = false)
        repeat(3) {
            run(filter, wrong, exchange("admin", path = "/transito/admin/ciudadanos"))
            run(filter, wrong, exchange("admin", post = false))
        }
        assertEquals(6, wrong.calls)
        assertEquals(HttpStatus.SEE_OTHER, run(filter, wrong, exchange("admin")).response.statusCode)
    }

    @Test
    fun `a disabled limiter lets everything through`() {
        val filter = filter(max = 0)
        val wrong = FakeLogin(succeeds = false)
        repeat(5) { assertEquals(HttpStatus.SEE_OTHER, run(filter, wrong, exchange("admin")).response.statusCode) }
        assertEquals(5, wrong.calls)
    }
}
