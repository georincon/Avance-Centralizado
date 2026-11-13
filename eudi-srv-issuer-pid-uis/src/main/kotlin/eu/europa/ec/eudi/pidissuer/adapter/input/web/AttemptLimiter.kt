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

import eu.europa.ec.eudi.pidissuer.duration
import org.springframework.core.env.Environment
import org.springframework.web.reactive.function.server.ServerRequest
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Límite de intentos en memoria, por clave: ventana fija que arranca en el primer intento registrado.
 * Con [maxAttempts] intentos registrados dentro de la ventana, la clave queda bloqueada hasta que la ventana termina.
 *
 * Limitaciones asumidas (es un límite de mejor esfuerzo, no un control de acceso):
 *  - El estado vive en la memoria de una instancia: se pierde al reiniciar y no se comparte entre réplicas.
 *  - Revisar y registrar son dos pasos: peticiones simultáneas pueden pasar unas pocas veces de más.
 *  - Se recuerdan como máximo [maxTrackedKeys] claves; las más antiguas se olvidan primero.
 *
 * Con `maxAttempts = 0` el límite está desactivado.
 */
class AttemptLimiter(
    val maxAttempts: Int,
    private val window: Duration,
    private val clock: Clock,
    private val maxTrackedKeys: Int = DEFAULT_MAX_TRACKED_KEYS,
) {
    init {
        require(maxAttempts >= 0) { "maxAttempts must not be negative" }
        require(window.isPositive()) { "window must be positive" }
        require(maxTrackedKeys > 0) { "maxTrackedKeys must be positive" }
    }

    sealed interface Status {
        data object Allowed : Status

        data class Blocked(
            val retryAfter: Duration,
        ) : Status {
            /** Segundos para la cabecera `Retry-After`, redondeados hacia arriba. */
            val retryAfterSeconds: Long get() = retryAfter.inWholeSeconds + 1
        }
    }

    private data class Entry(
        val count: Int,
        val windowEnd: Instant,
    )

    private val entries =
        object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > maxTrackedKeys
        }

    val enabled: Boolean get() = maxAttempts > 0

    fun status(key: String): Status {
        if (!enabled) return Status.Allowed
        val now = clock.now()
        synchronized(entries) {
            val entry = entries[key] ?: return Status.Allowed
            if (now >= entry.windowEnd) {
                entries.remove(key)
                return Status.Allowed
            }
            return if (entry.count >= maxAttempts) Status.Blocked(entry.windowEnd - now) else Status.Allowed
        }
    }

    fun record(key: String) {
        if (!enabled) return
        val now = clock.now()
        synchronized(entries) {
            val current = entries[key]
            entries[key] =
                if (current == null || now >= current.windowEnd) {
                    Entry(1, now + window)
                } else {
                    current.copy(count = current.count + 1)
                }
        }
    }

    fun reset(key: String) {
        synchronized(entries) { entries.remove(key) }
    }

    companion object {
        const val DEFAULT_MAX_TRACKED_KEYS: Int = 10_000

        val DEFAULT_WINDOW: Duration = 15.minutes
    }
}

private val IP_LIKE = Regex("^[0-9A-Fa-f:.]{2,45}$")

/**
 * IP del cliente. Si se indica [trustedHeader] (en este despliegue, `X-Real-IP`, que nginx fija con la IP de la
 * conexión y no con lo que envíe el cliente), se usa su valor; si falta o no parece una IP, se usa la dirección remota.
 *
 * Solo debe indicarse una cabecera que el proxy delantero sobrescriba siempre. `X-Forwarded-For` no sirve: nginx
 * añade su valor al que mande el cliente, así que el primer elemento se puede falsificar.
 */
fun ServerRequest.clientIp(trustedHeader: String?): String {
    val fromHeader =
        trustedHeader
            ?.takeIf { it.isNotBlank() }
            ?.let { headers().firstHeader(it) }
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { IP_LIKE.matches(it) }
    return fromHeader ?: remoteAddress().getOrNull()?.address?.hostAddress ?: "unknown"
}

/**
 * Límite de intentos fallidos del login de estudiantes: por usuario (defensa principal, no depende de la IP) y por IP
 * (mejor esfuerzo: la IP depende del proxy). Solo cuentan los intentos fallidos; un login correcto reinicia el
 * contador del usuario.
 */
class LoginRateLimit(
    private val perUser: AttemptLimiter,
    private val perIp: AttemptLimiter,
    private val clientIpHeader: String?,
) {
    private fun userKey(usuario: String) = usuario.trim().lowercase().take(MAX_USER_KEY_LENGTH)

    /** El bloqueo más largo entre el del usuario y el de la IP, o `null` si puede intentar. */
    fun blocked(
        request: ServerRequest,
        usuario: String,
    ): AttemptLimiter.Status.Blocked? =
        listOf(perUser.status(userKey(usuario)), perIp.status(request.clientIp(clientIpHeader)))
            .filterIsInstance<AttemptLimiter.Status.Blocked>()
            .maxByOrNull { it.retryAfter }

    fun recordFailure(
        request: ServerRequest,
        usuario: String,
    ) {
        perUser.record(userKey(usuario))
        perIp.record(request.clientIp(clientIpHeader))
    }

    fun recordSuccess(usuario: String) = perUser.reset(userKey(usuario))

    companion object {
        private const val MAX_USER_KEY_LENGTH = 100
        const val DEFAULT_CLIENT_IP_HEADER: String = "X-Real-IP"

        /** Sin límites: para pruebas y para construir [IssuerUi] sin configuración. */
        fun disabled(clock: Clock = Clock.System): LoginRateLimit =
            LoginRateLimit(
                AttemptLimiter(0, AttemptLimiter.DEFAULT_WINDOW, clock),
                AttemptLimiter(0, AttemptLimiter.DEFAULT_WINDOW, clock),
                null,
            )

        /**
         * Propiedades (todas opcionales; un máximo de 0 desactiva ese límite):
         *  - `issuer.rateLimit.clientIpHeader` (por defecto `X-Real-IP`)
         *  - `issuer.rateLimit.login.window` (por defecto `PT15M`)
         *  - `issuer.rateLimit.login.maxFailuresPerUser` (por defecto 5)
         *  - `issuer.rateLimit.login.maxFailuresPerIp` (por defecto 0: desactivado)
         */
        fun fromEnvironment(
            env: Environment,
            clock: Clock,
        ): LoginRateLimit {
            val window = env.duration("issuer.rateLimit.login.window") ?: AttemptLimiter.DEFAULT_WINDOW
            val maxPerUser = env.getProperty("issuer.rateLimit.login.maxFailuresPerUser", Int::class.javaObjectType, 5)
            val maxPerIp = env.getProperty("issuer.rateLimit.login.maxFailuresPerIp", Int::class.javaObjectType, 0)
            val header = env.getProperty("issuer.rateLimit.clientIpHeader", DEFAULT_CLIENT_IP_HEADER)
            return LoginRateLimit(AttemptLimiter(maxPerUser, window, clock), AttemptLimiter(maxPerIp, window, clock), header)
        }
    }
}
