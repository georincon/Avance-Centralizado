/*
 * Código propio de este fork (emulador de la UIS). No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.uis

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient

private val log = LoggerFactory.getLogger(SyncEstudianteToKeycloak::class.java)

/**
 * Mantiene en Keycloak (`uis-issuer-realm`) un usuario por estudiante: `username` = número de identificación, SIN contraseña
 * (cualquiera que tuviera se borra). La identidad se prueba con el PID o con un código de activación, nunca con una clave.
 */
class SyncEstudianteToKeycloak(
    private val webClient: WebClient,
    private val serverUrl: String,
    private val adminRealm: String,
    private val adminClientId: String,
    private val adminUser: String,
    private val adminPassword: String,
    private val userRealm: String,
) {
    suspend fun sync(e: Estudiante) {
        val token = adminToken()
        val base = serverUrl.trimEnd('/')
        val body = """{"username":${Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(e.numeroIdentificacion))},"enabled":true,""" +
            """"firstName":${Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(e.nombres))},""" +
            """"lastName":${Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(e.apellidos))}}"""
        var id = findId(base, token, e.numeroIdentificacion)
        if (id == null) {
            webClient.post().uri("$base/admin/realms/$userRealm/users").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).retrieve().toBodilessEntity().awaitSingle()
            id = findId(base, token, e.numeroIdentificacion) ?: error("Keycloak no devolvió el usuario recién creado")
        } else {
            webClient.put().uri("$base/admin/realms/$userRealm/users/$id").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).retrieve().toBodilessEntity().awaitSingle()
        }
        // Sin contraseña: se borra cualquier credencial que hubiera.
        val creds = webClient.get().uri("$base/admin/realms/$userRealm/users/$id/credentials").header("Authorization", "Bearer $token")
            .retrieve().bodyToMono(String::class.java).awaitSingle()
        (Json.parseToJsonElement(creds) as JsonArray).forEach { c ->
            val cid = ((c as JsonObject)["id"] as JsonPrimitive).content
            webClient.delete().uri("$base/admin/realms/$userRealm/users/$id/credentials/$cid").header("Authorization", "Bearer $token")
                .retrieve().toBodilessEntity().awaitSingle()
        }
        log.info("Estudiante {} sincronizado con Keycloak", e.numeroIdentificacion.take(3) + "***")
    }

    suspend fun delete(numero: String) {
        val token = adminToken()
        val base = serverUrl.trimEnd('/')
        val id = findId(base, token, numero) ?: return
        webClient.delete().uri("$base/admin/realms/$userRealm/users/$id").header("Authorization", "Bearer $token")
            .retrieve().toBodilessEntity().awaitSingle()
    }

    private suspend fun findId(base: String, token: String, username: String): String? {
        val r = webClient.get().uri("$base/admin/realms/$userRealm/users?username={u}&exact=true", username)
            .header("Authorization", "Bearer $token").retrieve().bodyToMono(String::class.java).awaitSingleOrNull() ?: return null
        return ((Json.parseToJsonElement(r) as JsonArray).firstOrNull() as? JsonObject)?.get("id")?.let { (it as JsonPrimitive).content }
    }

    private suspend fun adminToken(): String {
        val r = webClient.post().uri("${serverUrl.trimEnd('/')}/realms/$adminRealm/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("grant_type", "password").with("client_id", adminClientId)
                    .with("username", adminUser).with("password", adminPassword),
            ).retrieve().bodyToMono(String::class.java).awaitSingle()
        return ((Json.parseToJsonElement(r) as JsonObject)["access_token"] as JsonPrimitive).content
    }
}
