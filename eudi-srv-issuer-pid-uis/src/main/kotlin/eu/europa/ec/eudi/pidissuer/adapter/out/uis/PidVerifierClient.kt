/*
 * Código propio de este fork (emulador de la UIS). No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.uis

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.security.SecureRandom
import java.util.Base64

private val log = LoggerFactory.getLogger(PidVerifierClient::class.java)

sealed interface PidResult {
    data object Pending : PidResult

    data class Ok(val documentNumber: String, val givenName: String, val familyName: String) : PidResult

    data class Failed(val reason: String) : PidResult
}

data class PidRequest(val transactionId: String, val requestUri: String)

/**
 * Cliente del verifier del ecosistema (instancia `uis` de `verifiers/_comun`, el endpoint oficial EUDI). El verifier ya valida
 * la firma del PID, que su emisor esté en la lista de confianza (PID), su estado y que lo presenta su titular (KB-JWT con el
 * nonce de la solicitud). Aquí solo se pide el número de cédula y el nombre, y se lee el resultado. Nada se guarda.
 */
class PidVerifierClient(
    private val webClient: WebClient,
    apiBase: String,
) {
    private val api = apiBase.trimEnd('/')
    private val random = SecureRandom()

    @Volatile
    private var intendedUseId: String? = null

    suspend fun start(): PidRequest {
        val body =
            buildJsonObject {
                putJsonObject("dcql_query") {
                    putJsonArray("credentials") {
                        add(
                            buildJsonObject {
                                put("id", "pid")
                                put("format", "dc+sd-jwt")
                                putJsonObject("meta") { putJsonArray("vct_values") { add(JsonPrimitive(VCT_PID)) } }
                                putJsonArray("claims") {
                                    CLAIMS.forEach { c -> add(buildJsonObject { putJsonArray("path") { add(JsonPrimitive(c)) } }) }
                                }
                            },
                        )
                    }
                }
                put("nonce", ByteArray(16).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) })
                put("profile", "haip")
                intendedUse()?.let { put("intended_use_id", it) }
                put("request_uri_method", "get")
            }
        val response =
            webClient.post().uri("$api/ui/presentations/v2").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .bodyValue(body.toString()).retrieve().bodyToMono(String::class.java).awaitSingle()
        val json = Json.parseToJsonElement(response) as JsonObject
        return PidRequest(json.str("transaction_id")!!, json.str("authorization_request_uri")!!)
    }

    suspend fun result(transactionId: String): PidResult {
        val t = java.net.URLEncoder.encode(transactionId, "UTF-8")
        val resp =
            webClient.get().uri("$api/ui/presentations/$t").accept(MediaType.APPLICATION_JSON)
                .exchangeToMono { r -> r.bodyToMono(String::class.java).defaultIfEmpty("").map { r.statusCode() to it } }
                .awaitSingleOrNull() ?: return PidResult.Pending
        val (status, text) = resp
        if (status == HttpStatus.NOT_FOUND) return PidResult.Failed("la solicitud ya no existe")
        if (status == HttpStatus.BAD_REQUEST) return failureOrPending(t)
        val json = runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: return PidResult.Pending
        json.str("error")?.let { return PidResult.Failed(if (it == "access_denied") "no aceptaste compartir los datos" else "la wallet devolvió un error") }
        val presented = ((json["vp_token"] as? JsonObject)?.get("pid"))?.let { v -> (v as? JsonArray)?.firstOrNull() ?: v }
            ?.let { (it as? JsonPrimitive)?.content } ?: return PidResult.Failed("la respuesta no trae el PID")
        return runCatching { readSdJwt(presented) }.getOrElse {
            log.warn("No se pudo leer el PID presentado: {}", it.message)
            PidResult.Failed("no se pudo leer el PID")
        }
    }

    private suspend fun failureOrPending(t: String): PidResult {
        val ev = runCatching {
            webClient.get().uri("$api/ui/presentations/$t/events").accept(MediaType.APPLICATION_JSON).retrieve()
                .bodyToMono(String::class.java).awaitSingle()
        }.getOrNull() ?: return PidResult.Pending
        val events = ((Json.parseToJsonElement(ev) as? JsonObject)?.get("events") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("event") }
        FALLOS.entries.firstOrNull { it.key in events }?.let { return PidResult.Failed(it.value) }
        return PidResult.Pending
    }

    private suspend fun intendedUse(): String? {
        intendedUseId?.let { return it }
        val r = runCatching {
            webClient.get().uri("$api/ui/intended-uses").accept(MediaType.APPLICATION_JSON).retrieve().bodyToMono(String::class.java).awaitSingle()
        }.getOrNull() ?: return null
        val id = (((Json.parseToJsonElement(r) as? JsonObject)?.get("intended_uses") as? JsonArray)?.firstOrNull() as? JsonObject)?.str("intended_use_id")
        intendedUseId = id
        return id
    }

    /** Lee un SD-JWT ya validado por el verifier: los datos divulgados (cédula, nombres, apellidos). */
    private fun readSdJwt(presented: String): PidResult {
        val parts = presented.split('~')
        val decode = { b64: String -> String(Base64.getUrlDecoder().decode(b64)) }
        val data = mutableMapOf<String, String>()
        (Json.parseToJsonElement(decode(parts[0].split('.')[1])) as JsonObject).forEach { (k, v) -> (v as? JsonPrimitive)?.let { data[k] = it.content } }
        parts.drop(1).dropLast(1).filter { it.isNotEmpty() }.forEach { d ->
            val arr = Json.parseToJsonElement(decode(d)) as? JsonArray ?: return@forEach
            if (arr.size == 3) (arr[2] as? JsonPrimitive)?.let { data[(arr[1] as JsonPrimitive).content] = it.content }
        }
        val doc = data["personal_administrative_number"]?.trim().orEmpty()
        if (doc.isEmpty()) return PidResult.Failed("el PID no trae el número de cédula")
        return PidResult.Ok(doc, data["given_name"].orEmpty(), data["family_name"].orEmpty())
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.content

    companion object {
        const val VCT_PID = "urn:eudi:pid:1"
        private val CLAIMS = listOf("personal_administrative_number", "given_name", "family_name")
        private val FALLOS = linkedMapOf(
            "FailedToRetrieve request" to "la wallet no pudo leer la solicitud",
            "Wallet failed to post response" to "el PID no pasó la verificación",
            "Presentation expired" to "la solicitud venció",
            "Attestation status check failed" to "el PID fue revocado o suspendido",
        )
    }
}
