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
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.ServerRequest
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class MutableClock(
    var current: Instant = Instant.parse("2026-10-29T10:00:00Z"),
) : Clock {
    override fun now(): Instant = current

    fun advance(by: Duration) {
        current += by
    }
}

private fun mockRequest(
    header: Pair<String, String>? = null,
    remote: String = "10.0.0.9",
): ServerRequest {
    val http =
        MockServerHttpRequest
            .post("/test")
            .apply { header?.let { header(it.first, it.second) } }
            .remoteAddress(InetSocketAddress(InetAddress.getByName(remote), 4000))
            .build()
    return ServerRequest.create(MockServerWebExchange.from(http), HandlerStrategies.withDefaults().messageReaders())
}

class AttemptLimiterTest {
    private val clock = MutableClock()

    private fun limiter(
        max: Int = 3,
        maxKeys: Int = AttemptLimiter.DEFAULT_MAX_TRACKED_KEYS,
    ) = AttemptLimiter(max, 15.minutes, clock, maxKeys)

    @Test
    fun `allows attempts until the maximum and then blocks`() {
        val limiter = limiter(max = 3)
        repeat(3) {
            assertEquals(AttemptLimiter.Status.Allowed, limiter.status("k"))
            limiter.record("k")
        }
        assertIs<AttemptLimiter.Status.Blocked>(limiter.status("k"))
    }

    @Test
    fun `blocked status reports the time left in the window`() {
        val limiter = limiter(max = 1)
        limiter.record("k")
        clock.advance(5.minutes)

        val blocked = assertIs<AttemptLimiter.Status.Blocked>(limiter.status("k"))
        assertEquals(10.minutes, blocked.retryAfter)
        assertEquals(601, blocked.retryAfterSeconds)
    }

    @Test
    fun `the window does not slide with later attempts`() {
        val limiter = limiter(max = 2)
        limiter.record("k")
        clock.advance(10.minutes)
        limiter.record("k")

        // La ventana arrancó en el primer intento: quedan 5 minutos, no 15.
        val blocked = assertIs<AttemptLimiter.Status.Blocked>(limiter.status("k"))
        assertEquals(5.minutes, blocked.retryAfter)
    }

    @Test
    fun `a key is allowed again once its window ends`() {
        val limiter = limiter(max = 1)
        limiter.record("k")
        assertIs<AttemptLimiter.Status.Blocked>(limiter.status("k"))

        clock.advance(15.minutes)
        assertEquals(AttemptLimiter.Status.Allowed, limiter.status("k"))

        limiter.record("k")
        assertIs<AttemptLimiter.Status.Blocked>(limiter.status("k"))
    }

    @Test
    fun `reset clears a key without affecting the others`() {
        val limiter = limiter(max = 1)
        limiter.record("a")
        limiter.record("b")

        limiter.reset("a")

        assertEquals(AttemptLimiter.Status.Allowed, limiter.status("a"))
        assertIs<AttemptLimiter.Status.Blocked>(limiter.status("b"))
    }

    @Test
    fun `keys are independent`() {
        val limiter = limiter(max = 1)
        limiter.record("a")
        assertIs<AttemptLimiter.Status.Blocked>(limiter.status("a"))
        assertEquals(AttemptLimiter.Status.Allowed, limiter.status("b"))
    }

    @Test
    fun `a maximum of zero disables the limiter`() {
        val limiter = limiter(max = 0)
        repeat(100) { limiter.record("k") }
        assertEquals(AttemptLimiter.Status.Allowed, limiter.status("k"))
    }

    @Test
    fun `only the most recent keys are remembered`() {
        val limiter = limiter(max = 1, maxKeys = 2)
        limiter.record("a")
        limiter.record("b")
        limiter.record("c") // desplaza a "a"

        assertEquals(AttemptLimiter.Status.Allowed, limiter.status("a"))
        assertIs<AttemptLimiter.Status.Blocked>(limiter.status("b"))
        assertIs<AttemptLimiter.Status.Blocked>(limiter.status("c"))
    }
}

class ClientIpTest {
    private fun request(
        header: Pair<String, String>? = null,
        remote: String = "10.0.0.9",
    ): ServerRequest = mockRequest(header, remote)

    @Test
    fun `uses the trusted header when it holds an ip`() {
        assertEquals("203.0.113.7", request("X-Real-IP" to "203.0.113.7").clientIp("X-Real-IP"))
        assertEquals("2001:db8::1", request("X-Real-IP" to "2001:db8::1").clientIp("X-Real-IP"))
    }

    @Test
    fun `takes only the first element of a list`() {
        assertEquals("203.0.113.7", request("X-Real-IP" to "203.0.113.7, 198.51.100.2").clientIp("X-Real-IP"))
    }

    @Test
    fun `ignores a header that is not an ip`() {
        assertEquals("10.0.0.9", request("X-Real-IP" to "<script>alert(1)</script>").clientIp("X-Real-IP"))
    }

    @Test
    fun `falls back to the remote address when the header is missing or not configured`() {
        assertEquals("10.0.0.9", request().clientIp("X-Real-IP"))
        assertEquals("10.0.0.9", request("X-Real-IP" to "203.0.113.7").clientIp(null))
    }

    @Test
    fun `X-Forwarded-For is not used unless explicitly configured`() {
        assertEquals("10.0.0.9", request("X-Forwarded-For" to "1.2.3.4").clientIp("X-Real-IP"))
    }
}

class LoginRateLimitTest {
    private val clock = MutableClock()

    private fun request(ip: String = "203.0.113.7"): ServerRequest = mockRequest("X-Real-IP" to ip)

    private fun limit(
        perUser: Int = 3,
        perIp: Int = 5,
    ) = LoginRateLimit(
        AttemptLimiter(perUser, 15.minutes, clock),
        AttemptLimiter(perIp, 15.minutes, clock),
        "X-Real-IP",
    )

    @Test
    fun `blocks a user after too many failures even from another ip`() {
        val limit = limit(perUser = 3)
        repeat(3) { i -> limit.recordFailure(request(ip = "198.51.100.$i"), "jperez") }

        assertIs<AttemptLimiter.Status.Blocked>(limit.blocked(request(ip = "192.0.2.1"), "jperez"))
        assertNull(limit.blocked(request(ip = "192.0.2.1"), "mgomez"))
    }

    @Test
    fun `usernames are compared ignoring case and surrounding spaces`() {
        val limit = limit(perUser = 1)
        limit.recordFailure(request(), "JPerez ")
        assertIs<AttemptLimiter.Status.Blocked>(limit.blocked(request(ip = "192.0.2.1"), "jperez"))
    }

    @Test
    fun `blocks an ip that fails with many different users`() {
        val limit = limit(perUser = 100, perIp = 3)
        repeat(3) { i -> limit.recordFailure(request(), "user$i") }

        assertIs<AttemptLimiter.Status.Blocked>(limit.blocked(request(), "otro"))
        assertNull(limit.blocked(request(ip = "192.0.2.1"), "otro"))
    }

    @Test
    fun `a successful login clears the user counter`() {
        val limit = limit(perUser = 3)
        repeat(2) { limit.recordFailure(request(), "jperez") }

        limit.recordSuccess("jperez")
        repeat(2) { limit.recordFailure(request(), "jperez") }

        assertNull(limit.blocked(request(ip = "192.0.2.1"), "jperez"))
    }

    @Test
    fun `reports the longest of the two blocks`() {
        val limit = limit(perUser = 1, perIp = 1)
        limit.recordFailure(request(), "jperez")
        clock.advance(5.minutes)
        limit.recordFailure(request(ip = "198.51.100.1"), "mgomez")

        // mgomez queda bloqueado 15 min por usuario; la IP de la petición, 10 min. Gana el más largo.
        val blocked = assertIs<AttemptLimiter.Status.Blocked>(limit.blocked(request(), "mgomez"))
        assertEquals(15.minutes, blocked.retryAfter)
    }

    @Test
    fun `disabled never blocks`() {
        val limit = LoginRateLimit.disabled(clock)
        repeat(50) { limit.recordFailure(request(), "jperez") }
        assertNull(limit.blocked(request(), "jperez"))
    }

    @Test
    fun `window is honoured`() {
        val limit = limit(perUser = 1)
        limit.recordFailure(request(), "jperez")
        clock.advance(15.minutes + 1.seconds)
        assertNull(limit.blocked(request(), "jperez"))
    }
}
