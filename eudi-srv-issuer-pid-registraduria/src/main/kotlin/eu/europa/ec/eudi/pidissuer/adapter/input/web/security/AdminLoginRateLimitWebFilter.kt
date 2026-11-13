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
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

private val log = LoggerFactory.getLogger(AdminLoginRateLimitWebFilter::class.java)

/**
 * Limita los intentos fallidos del login del panel de administración, por usuario (no por IP: la IP depende del
 * proxy). Va delante del formulario de login: si el usuario ya acumuló demasiados fallos responde 429 sin llegar a
 * comprobar la clave. Un login correcto reinicia el contador.
 *
 * El resultado se deduce de la respuesta del login: Spring redirige a `login?error` cuando falla.
 */
class AdminLoginRateLimitWebFilter(
    private val limiter: AttemptLimiter,
    private val loginPath: String,
) : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val request = exchange.request
        if (!limiter.enabled || request.method != HttpMethod.POST || request.path.pathWithinApplication().value() != loginPath) {
            return chain.filter(exchange)
        }
        return exchange.formData.flatMap { form ->
            val username = form.getFirst("username")?.trim()?.lowercase()?.take(MAX_KEY_LENGTH).orEmpty()
            if (username.isBlank()) {
                return@flatMap chain.filter(exchange)
            }
            val blocked = limiter.status(username) as? AttemptLimiter.Status.Blocked
            if (blocked != null) {
                log.warn("Admin login blocked for user '{}' for {} more seconds", username, blocked.retryAfterSeconds)
                val response = exchange.response
                response.statusCode = HttpStatus.TOO_MANY_REQUESTS
                response.headers.set("Retry-After", blocked.retryAfterSeconds.toString())
                return@flatMap response.setComplete()
            }
            chain.filter(exchange).then(
                Mono.fromRunnable {
                    val location = exchange.response.headers.location
                    val failed = location?.query?.contains("error") == true
                    if (failed) limiter.record(username) else limiter.reset(username)
                },
            )
        }
    }

    private companion object {
        const val MAX_KEY_LENGTH = 100
    }
}
