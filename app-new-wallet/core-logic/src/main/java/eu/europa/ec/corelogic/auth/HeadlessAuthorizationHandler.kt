package eu.europa.ec.corelogic.auth

import android.net.Uri
import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationHandler
import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Autorizacion OpenID4VCI sin abrir el navegador. La oferta de la Registraduria lleva un `issuer_state` (ticket de un
 * solo uso ligado a la cedula ya validada en el onboarding); la libreria lo envia en la peticion de autorizacion y
 * Keycloak autentica al titular sin mostrar ningun formulario. Este manejador solo recorre ese flujo por HTTP
 * (cookies en memoria, redirecciones a mano) hasta que Keycloak redirige al esquema de la wallet con el `code`.
 *
 * La wallet NO envia usuario ni contrasena: no existen. Si Keycloak niega el acceso (ticket vencido, ya usado o
 * ausente) la autorizacion falla con un mensaje claro; no hay formulario al que recurrir.
 */
class HeadlessAuthorizationHandler(
    private val redirectScheme: String,
) : AuthorizationHandler {

    override suspend fun authorize(authorizationUrl: String): Result<AuthorizationResponse> =
        withContext(Dispatchers.IO) { runCatching { follow(authorizationUrl) } }

    private fun follow(startUrl: String): AuthorizationResponse {
        val cookies = linkedMapOf<String, String>()
        var url = startUrl
        repeat(MAX_STEPS) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                if (cookies.isNotEmpty()) setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            try {
                val code = conn.responseCode
                conn.headerFields["Set-Cookie"]?.forEach { c ->
                    val pair = c.substringBefore(';')
                    val name = pair.substringBefore('=')
                    val value = pair.substringAfter('=', "")
                    if (value.isEmpty() || c.contains("Max-Age=0", true)) cookies.remove(name) else cookies[name] = value
                }
                when (code) {
                    in 300..399 -> {
                        val location = conn.getHeaderField("Location") ?: error("Redireccion sin Location")
                        if (location.startsWith("$redirectScheme:")) return parseCallback(location)
                        url = URL(URL(url), location).toString()
                    }
                    403 -> error("La Registraduria nego el acceso: la solicitud vencio o ya se uso. Vuelve a pedir tu documento.")
                    else -> error("Keycloak respondio HTTP $code sin completar la autorizacion")
                }
            } finally {
                conn.disconnect()
            }
        }
        error("Demasiadas redirecciones")
    }

    private fun parseCallback(callback: String): AuthorizationResponse {
        val uri = Uri.parse(callback)
        val code = requireNotNull(uri.getQueryParameter("code")) { "La redireccion no trae code (${uri.getQueryParameter("error")})" }
        val state = requireNotNull(uri.getQueryParameter("state")) { "La redireccion no trae state" }
        return AuthorizationResponse(code, state)
    }

    private companion object {
        const val MAX_STEPS = 12
    }
}
