/*
 * Código propio de este fork (emulador de la Dirección de Tránsito de Bucaramanga).
 * No es parte del eudi-srv-pid-issuer original de la Comisión Europea.
 * Ver README.md, sección 5 (Opción A) y 9.4.
 */
package eu.europa.ec.eudi.pidissuer.adapter.out.transito

import com.eygraber.uri.Url
import com.eygraber.uri.toURI
import com.nimbusds.oauth2.sdk.token.AccessToken
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid.AdministrationClient
import eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid.Realm
import eu.europa.ec.eudi.pidissuer.adapter.out.util.maskDocumentNumber
import eu.europa.ec.eudi.pidissuer.port.out.transito.SyncLicenciaToKeycloak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitBodilessEntity

private val log = LoggerFactory.getLogger(SyncLicenciaToKeycloakGhostUser::class.java)

/**
 * Crea o actualiza en Keycloak un usuario "fantasma" por cada [Licencia]
 * cargada en el panel admin: `username` = número de cédula, **sin
 * credenciales** (no se fija contraseña). Para que el login funcione de
 * verdad sin pedir clave hace falta además cambiar el *authentication flow*
 * del realm a solo "Username Form" (pendiente, ver sección 6.1 de
 * README.md — no se puede hacer vía API admin de forma
 * segura/idempotente, se deja como paso manual en la consola de Keycloak).
 *
 * Solo se cablea cuando `issuer.pid.dataSource=transito` (ver
 * [eu.europa.ec.eudi.pidissuer.IssuerContext]); si el admin API de Keycloak
 * falla, el error se registra y se propaga — quien llama decide si bloquea
 * el guardado o solo lo advierte.
 */
class SyncLicenciaToKeycloakGhostUser(
    private val webClient: WebClient,
    private val keyCloak: Url,
    private val administrationClient: AdministrationClient,
    private val users: Realm,
) : SyncLicenciaToKeycloak {
    override suspend fun invoke(licencia: Licencia) {
        val username = licencia.documentNumber.value
        log.info("Sincronizando usuario fantasma '{}' en Keycloak ...", username.maskDocumentNumber())

        val accessToken = getAdminAccessToken()
        val existing = findUserByUsername(username, accessToken)
        val representation =
            KeycloakUserRepresentation(
                username = username,
                enabled = true,
                firstName = licencia.nombres,
                lastName = licencia.apellidos,
                attributes = mapOf("nationality" to listOf(licencia.nacionalidad)),
            )

        if (existing == null) {
            createUser(representation, accessToken)
        } else {
            val existingId = requireNotNull(existing.id) { "Keycloak user '$username' has no id" }
            updateUser(existingId, representation, accessToken)
        }
    }

    private suspend fun findUserByUsername(
        username: String,
        accessToken: AccessToken,
    ): KeycloakUserRepresentation? {
        val url =
            keyCloak
                .buildUpon()
                .appendPath("admin")
                .appendPath("realms")
                .appendPath(users.value)
                .appendPath("users")
                .appendQueryParameter("username", username)
                .appendQueryParameter("exact", "true")
                .build()

        val found =
            webClient
                .get()
                .uri(url.toURI())
                .accept(MediaType.APPLICATION_JSON)
                .headers { it[HttpHeaders.AUTHORIZATION] = accessToken.toAuthorizationHeader() }
                .retrieve()
                .awaitBody<List<KeycloakUserRepresentation>>()
        return found.firstOrNull()
    }

    private suspend fun createUser(
        representation: KeycloakUserRepresentation,
        accessToken: AccessToken,
    ) {
        val url =
            keyCloak
                .buildUpon()
                .appendPath("admin")
                .appendPath("realms")
                .appendPath(users.value)
                .appendPath("users")
                .build()

        webClient
            .post()
            .uri(url.toURI())
            .contentType(MediaType.APPLICATION_JSON)
            .headers { it[HttpHeaders.AUTHORIZATION] = accessToken.toAuthorizationHeader() }
            .bodyValue(representation)
            .retrieve()
            .awaitBodilessEntity()
    }

    private suspend fun updateUser(
        keycloakUserId: String,
        representation: KeycloakUserRepresentation,
        accessToken: AccessToken,
    ) {
        val url =
            keyCloak
                .buildUpon()
                .appendPath("admin")
                .appendPath("realms")
                .appendPath(users.value)
                .appendPath("users")
                .appendPath(keycloakUserId)
                .build()

        webClient
            .put()
            .uri(url.toURI())
            .contentType(MediaType.APPLICATION_JSON)
            .headers { it[HttpHeaders.AUTHORIZATION] = accessToken.toAuthorizationHeader() }
            .bodyValue(representation)
            .retrieve()
            .awaitBodilessEntity()
    }

    /**
     * Igual que `GetPidDataFromKeyCloak.getAdminAccessToken()`, duplicado
     * aquí a propósito para no modificar ese archivo (que no es nuestro,
     * ver README.md).
     */
    private suspend fun getAdminAccessToken(): AccessToken {
        val tokenEndpoint =
            keyCloak
                .buildUpon()
                .appendPath("realms")
                .appendPath(administrationClient.realm.value)
                .appendPath("protocol")
                .appendPath("openid-connect")
                .appendPath("token")
                .build()
        val response =
            webClient
                .post()
                .uri(tokenEndpoint.toURI())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(
                    BodyInserters.fromFormData(
                        LinkedMultiValueMap<String, String>().apply {
                            add("grant_type", "password")
                            add("client_id", administrationClient.client.username)
                            administrationClient.client.password?.let { add("client_secret", it) }
                            add("username", administrationClient.admin.username)
                            administrationClient.admin.password?.let { add("password", it) }
                        },
                    ),
                ).retrieve()
                .awaitBody<String>()

        return withContext(Dispatchers.Default) {
            AccessToken.parse(JSONObjectUtils.parse(response))
        }
    }
}

@Serializable
@JsonIgnoreUnknownKeys
private data class KeycloakUserRepresentation(
    val id: String? = null,
    val username: String,
    val enabled: Boolean,
    val firstName: String,
    val lastName: String,
    // El perfil de usuario del realm exige `nationality`; sin él Keycloak responde 400 al crear el usuario.
    val attributes: Map<String, List<String>> = emptyMap(),
)
