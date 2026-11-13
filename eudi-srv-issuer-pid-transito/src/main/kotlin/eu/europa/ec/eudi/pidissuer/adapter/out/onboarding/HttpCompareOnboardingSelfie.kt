/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.onboarding

import eu.europa.ec.eudi.pidissuer.port.out.transito.CompareOnboardingSelfie
import eu.europa.ec.eudi.pidissuer.port.out.transito.FaceMatchResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

private val log = LoggerFactory.getLogger(HttpCompareOnboardingSelfie::class.java)

class HttpCompareOnboardingSelfie(
    private val webClient: WebClient,
    private val baseUrl: String,
    private val apiKey: String?,
) : CompareOnboardingSelfie {
    override suspend fun invoke(sessionId: String, reference: ByteArray): FaceMatchResult {
        val key = apiKey?.takeIf { it.isNotBlank() } ?: return FaceMatchResult.Unavailable("issuer.onboarding.internal-api-key no configurada")
        return runCatching {
            val builder = MultipartBodyBuilder()
            builder.part("reference", object : ByteArrayResource(reference) {
                override fun getFilename(): String = "reference.jpg"
            }).contentType(MediaType.IMAGE_JPEG)
            val body =
                webClient
                    .post()
                    .uri("${baseUrl.trimEnd('/')}/api/v1/internal/sessions/{id}/compare-selfie", sessionId)
                    .header("X-Api-Key", key)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .awaitBody<String>()
            val json = Json.parseToJsonElement(body) as JsonObject
            val score = (json["score"] as? JsonPrimitive)?.doubleOrNull
            if ((json["status"] as? JsonPrimitive)?.content == "SUCCESS" && score != null) FaceMatchResult.Match(score) else FaceMatchResult.NoMatch(score)
        }.getOrElse {
            log.warn("No se pudo comparar la selfie del onboarding: {}", it.message)
            FaceMatchResult.Unavailable(it.message ?: it::class.java.simpleName)
        }
    }
}
