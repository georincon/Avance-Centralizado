/*
 * Código propio de este fork (emulador de la Registraduría Nacional del Estado Civil).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.onboarding

import eu.europa.ec.eudi.pidissuer.port.out.transito.FaceMatchResult
import eu.europa.ec.eudi.pidissuer.port.out.transito.FaceMatcher
import eu.europa.ec.eudi.pidissuer.port.out.transito.SelfieCheck
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

private val log = LoggerFactory.getLogger(HttpFaceMatcher::class.java)

/** Cliente del servicio de biometría del onboarding (`/api/verify/liveness` y `/api/verify/face-match`). */
class HttpFaceMatcher(
    private val webClient: WebClient,
    private val baseUrl: String,
) : FaceMatcher {
    override suspend fun checkSelfie(selfie: ByteArray): SelfieCheck =
        runCatching {
            val json = post("/api/verify/liveness", "selfie" to selfie)
            if (json.str("status") == "success") {
                SelfieCheck.Ok
            } else {
                SelfieCheck.Bad(json.str("details") ?: "La selfie no es válida.")
            }
        }.getOrElse {
            log.warn("Servicio de biometría no disponible: {}", it.message)
            SelfieCheck.Unavailable(it.message ?: it::class.java.simpleName)
        }

    override suspend fun match(
        reference: ByteArray,
        selfie: ByteArray,
    ): FaceMatchResult =
        runCatching {
            val json = post("/api/verify/face-match", "reference" to reference, "selfie" to selfie)
            val score = (json["score"] as? JsonPrimitive)?.doubleOrNull
            if (json.str("status") == "success" && score != null) FaceMatchResult.Match(score) else FaceMatchResult.NoMatch(score)
        }.getOrElse {
            log.warn("Servicio de biometría no disponible: {}", it.message)
            FaceMatchResult.Unavailable(it.message ?: it::class.java.simpleName)
        }

    private suspend fun post(
        path: String,
        vararg files: Pair<String, ByteArray>,
    ): JsonObject {
        val builder = MultipartBodyBuilder()
        files.forEach { (name, bytes) ->
            builder
                .part(
                    name,
                    object : ByteArrayResource(bytes) {
                        override fun getFilename(): String = "$name.jpg"
                    },
                ).contentType(MediaType.IMAGE_JPEG)
        }
        val body =
            webClient
                .post()
                .uri(baseUrl.trimEnd('/') + path)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .awaitBody<String>()
        return Json.parseToJsonElement(body) as JsonObject
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content
}
