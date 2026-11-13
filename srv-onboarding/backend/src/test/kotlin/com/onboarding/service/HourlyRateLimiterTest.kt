package com.onboarding.service

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HourlyRateLimiterTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }

    @Test
    fun `bloquea al superar el maximo y libera pasada la hora`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val limiter = HourlyRateLimiter(3, clock)
        repeat(3) { assertTrue(limiter.tryAcquire("a")) }
        assertFalse(limiter.tryAcquire("a"))
        assertTrue(limiter.tryAcquire("b"), "otra clave no se ve afectada")
        clock.now = clock.now.plusSeconds(3601)
        assertTrue(limiter.tryAcquire("a"))
    }
}
