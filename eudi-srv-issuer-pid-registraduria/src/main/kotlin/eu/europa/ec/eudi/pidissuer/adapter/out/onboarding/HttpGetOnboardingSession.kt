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
package eu.europa.ec.eudi.pidissuer.adapter.out.onboarding

import eu.europa.ec.eudi.pidissuer.port.out.registraduria.GetOnboardingSession
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.OnboardingSession
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.OnboardingSessionResult
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitExchange

private val log = LoggerFactory.getLogger(HttpGetOnboardingSession::class.java)

/**
 * Consulta `GET {baseUrl}/api/v1/onboarding/sessions/{id}` del backend de onboarding.
 */
class HttpGetOnboardingSession(
    private val webClient: WebClient,
    private val baseUrl: String,
) : GetOnboardingSession {
    override suspend fun invoke(sessionId: String): OnboardingSessionResult =
        runCatching {
            webClient
                .get()
                .uri("${baseUrl.trimEnd('/')}/api/v1/onboarding/sessions/{id}", sessionId)
                .awaitExchange { response ->
                    when {
                        response.statusCode() == HttpStatus.NOT_FOUND -> OnboardingSessionResult.NotFound
                        response.statusCode().is2xxSuccessful -> {
                            val body = response.bodyToMono(String::class.java).awaitSingleOrNull().orEmpty()
                            OnboardingSessionResult.Found(parseSession(body))
                        }
                        else -> OnboardingSessionResult.Unavailable("onboarding backend responded ${response.statusCode()}")
                    }
                }
        }.getOrElse {
            log.warn("No se pudo consultar la sesión de onboarding: {}", it.message)
            OnboardingSessionResult.Unavailable(it.message ?: it::class.java.simpleName)
        }

    companion object {
        /** Extrae la evidencia de la respuesta JSON del backend de onboarding. */
        fun parseSession(json: String): OnboardingSession {
            val root = Json.parseToJsonElement(json) as JsonObject
            val data = root["data"] as? JsonObject
            fun JsonObject?.str(key: String): String? =
                (this?.get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.ifBlank { null }
            fun JsonElement?.asObj() = this as? JsonObject

            return OnboardingSession(
                decision = root.str("decision"),
                status = root.str("status"),
                documentNumber = data.str("documentNumber"),
                firstNames = listOfNotNull(data.str("firstName"), data.str("middleName")).joinToString(" "),
                lastNames = listOfNotNull(data.str("lastName"), data.str("secondLastName")).joinToString(" "),
                birthDate = data.str("birthDate")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() },
            )
        }
    }
}
