/*
 * Reporte de actividad al centro de auditoría (srv-auditoria). Solo monitoreo: NO altera la emisión.
 * Contrato: srv-auditoria/docs/CONTRATO-EVENTOS-SERVICIOS.md
 *  - Envío asíncrono (HttpClient.sendAsync), timeout 2 s, cualquier error se ignora.
 *  - AUDITORIA_ACTIVA (por defecto true) y AUDITORIA_URL (por defecto http://srv-auditoria:8111).
 *  - Nunca se envían tokens, códigos ni valores de atributos; solo la cédula/nombre del sujeto si el token ya los trae.
 */
package eu.europa.ec.eudi.pidissuer.adapter.input.web

import org.springframework.core.Ordered
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.http.server.reactive.ServerHttpResponseDecorator
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import org.reactivestreams.Publisher
import org.springframework.core.io.buffer.DataBuffer
import reactor.core.publisher.Mono
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

class AuditoriaEmisionWebFilter(
    private val publicUrl: String,
    private val nombre: String,
    private val proyecto: String,
) : WebFilter, Ordered {

    private val activa = (System.getenv("AUDITORIA_ACTIVA") ?: "true").trim().lowercase() !in setOf("false", "0", "no")
    private val destino = (System.getenv("AUDITORIA_URL") ?: "http://srv-auditoria:8111").trimEnd('/') + "/api/v1/servicios/eventos"
    private val clave = System.getenv("AUDITORIA_CLAVE_SERVICIOS")?.takeIf { it.isNotBlank() }
    private val http: HttpClient? = runCatching {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    }.getOrNull()

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (!activa || http == null) return chain.filter(exchange)
        val path = runCatching { exchange.request.path.value() }.getOrDefault("")
        if (exchange.request.method?.name() != "POST" || !path.endsWith(WalletApi.CREDENTIAL_ENDPOINT)) {
            return chain.filter(exchange)
        }
        val inicio = System.nanoTime()
        val cuerpoError = StringBuilder()
        val original = exchange.response
        val espia = object : ServerHttpResponseDecorator(original) {
            override fun writeWith(body: Publisher<out DataBuffer>): Mono<Void> {
                val status = delegate.statusCode?.value() ?: 200
                if (status < 400) return super.writeWith(body)
                val observado = reactor.core.publisher.Flux.from(body).doOnNext { db ->
                    runCatching {
                        if (cuerpoError.length < 2048) {
                            cuerpoError.append(db.toString(db.readPosition(), minOf(db.readableByteCount(), 2048), Charsets.UTF_8))
                        }
                    }
                }
                return super.writeWith(observado)
            }
        }
        val auth = runCatching { exchange.request.headers.getFirst("Authorization") }.getOrNull()
        return chain.filter(exchange.mutate().response(espia as ServerHttpResponse).build())
            .doOnSuccess { reportar(auth, original.statusCode?.value() ?: 200, cuerpoError.toString(), original.headers.getFirst("WWW-Authenticate"), inicio) }
            .doOnError { reportar(auth, original.statusCode?.value()?.takeIf { it >= 400 } ?: 500, "", null, inicio) }
    }

    private fun reportar(auth: String?, status: Int, cuerpo: String, wwwAuth: String?, inicio: Long) {
        try {
            val ms = (System.nanoTime() - inicio) / 1_000_000
            val claims = claimsDe(auth)
            val ok = status in 200..299
            val ev = StringBuilder()
            ev.append("{\"evento_id\":\"").append(UUID.randomUUID()).append("\",")
            ev.append("\"servicio\":{\"rol\":\"emisor\",\"identificador\":").append(js(publicUrl))
                .append(",\"nombre\":").append(js(nombre)).append(",\"proyecto\":").append(js(proyecto)).append("},")
            ev.append("\"evento\":{\"tipo\":\"emision_credencial\",\"resultado\":\"").append(if (ok) "exitoso" else "fallido").append("\",")
            ev.append("\"ocurrido_en\":\"").append(Instant.now()).append("\",\"canal\":\"api\",\"duracion_ms\":").append(ms)
            val scope = claims["scope"]?.split(" ")?.filter { it.isNotBlank() && it !in setOf("openid", "profile", "email", "offline_access") }?.joinToString(" ")
            if (!scope.isNullOrBlank()) ev.append(",\"credencial_tipo\":").append(js(scope))
            // La cédula: un claim propio si el token lo trae; si no, el nombre de usuario de Keycloak, que en estos emisores ES la cédula
            // (solo si parece un documento —corto, sin espacios ni guiones—, para no guardar un identificador interno por error).
            val doc = listOf("numero_documento", "document_number", "personal_administrative_number").firstNotNullOfOrNull { claims[it]?.takeIf { v -> v.isNotBlank() } }
                ?: claims["preferred_username"]?.takeIf { Regex("^[A-Za-z0-9]{4,30}$").matches(it) }
            val nom = listOfNotNull(claims["given_name"], claims["family_name"]).joinToString(" ").ifBlank { claims["name"] ?: "" }
            if (doc != null || nom.isNotBlank()) {
                ev.append(",\"sujeto\":{")
                val partes = mutableListOf<String>()
                if (doc != null) partes += "\"numero_documento\":" + js(doc)
                if (nom.isNotBlank()) partes += "\"nombre\":" + js(nom)
                ev.append(partes.joinToString(",")).append("}")
            }
            if (!ok) {
                val codigo = Regex("\"error\"\\s*:\\s*\"([A-Za-z0-9_.-]{1,60})\"").find(cuerpo)?.groupValues?.get(1)
                    ?: Regex("error=\"([A-Za-z0-9_.-]{1,60})\"").find(wwwAuth ?: "")?.groupValues?.get(1)
                val tipo = when {
                    status == 401 || status == 403 -> "autenticacion"
                    status in 400..499 -> "validacion"
                    else -> "emisor"
                }
                ev.append(",\"tipo_error\":\"").append(tipo).append("\"")
                if (codigo != null) ev.append(",\"codigo_error\":").append(js(codigo))
                ev.append(",\"mensaje_error\":").append(js("El emisor respondió HTTP $status en el endpoint de credencial"))
            }
            ev.append("}}")
            val req = HttpRequest.newBuilder(URI.create(destino)).timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json").apply { if (clave != null) header("X-Api-Key", clave) }
                .POST(HttpRequest.BodyPublishers.ofString(ev.toString())).build()
            http!!.sendAsync(req, HttpResponse.BodyHandlers.discarding()).exceptionally { null }
        } catch (_: Throwable) {
            // reportar nunca debe afectar la emisión
        }
    }

    private fun claimsDe(auth: String?): Map<String, String> = try {
        val jwt = auth?.removePrefix("Bearer ")?.removePrefix("DPoP ")?.trim() ?: return emptyMap()
        val payload = String(Base64.getUrlDecoder().decode(jwt.split(".")[1]), Charsets.UTF_8)
        // Solo claves simples string; sin verificar (únicamente informativo).
        Regex("\"([a-z_]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.){0,200})\"").findAll(payload)
            .associate { it.groupValues[1] to it.groupValues[2] }
            .filterKeys { it in setOf("scope", "numero_documento", "document_number", "personal_administrative_number", "given_name", "family_name", "name", "preferred_username") }
    } catch (_: Throwable) {
        emptyMap()
    }

    private fun js(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c < ' ' -> append(' ')
            else -> append(c)
        }
        append('"')
    }
}
