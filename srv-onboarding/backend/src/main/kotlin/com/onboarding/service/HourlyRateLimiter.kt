package com.onboarding.service

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/** Limite en memoria de N eventos por clave en una ventana deslizante de una hora (se pierde al reiniciar). */
class HourlyRateLimiter(private val max: Int, @Volatile var clock: Clock = Clock.systemUTC()) {
    private val hits = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val windowMs = 3_600_000L

    fun tryAcquire(key: String): Boolean {
        val now = clock.millis()
        val q = hits.computeIfAbsent(key) { ArrayDeque() }
        synchronized(q) {
            while (q.isNotEmpty() && now - q.first() >= windowMs) q.removeFirst()
            if (q.size >= max) return false
            q.addLast(now)
        }
        if (hits.size > 10_000) purge(now)
        return true
    }

    private fun purge(now: Long) {
        hits.entries.removeIf { (_, q) -> synchronized(q) { q.isEmpty() || now - q.last() >= windowMs } }
    }
}
