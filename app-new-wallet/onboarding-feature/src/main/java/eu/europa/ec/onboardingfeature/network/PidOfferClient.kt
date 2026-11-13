package eu.europa.ec.onboardingfeature.network

import org.json.JSONObject
import org.json.JSONArray
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** La Registraduría rechazó o no pudo crear la oferta de la PID (con el motivo para mostrar al usuario). */
class PidOfferException(message: String) : Exception(message)

/**
 * Pide al issuer de la Registraduría la oferta de la PID (mso_mdoc + dc+sd-jwt) para una sesión de onboarding
 * APPROVED. El issuer verifica la sesión con el backend de onboarding, homologa los datos contra su base y
 * devuelve un `offerUri` de OpenID4VCI con un `issuer_state` de un solo uso (la autenticación posterior en
 * Keycloak es automática, sin contraseña).
 */
class PidOfferClient(private val issuerBaseUrl: String) {

    fun requestOffer(sessionId: String, credentialFormats: List<String>): String {
        require(credentialFormats.isNotEmpty()) { "Selecciona al menos un formato de credencial." }
        val conn = (URL("$issuerBaseUrl/onboarding/offers").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val body = JSONObject()
                .put("sessionId", sessionId)
                .put("credentialFormats", JSONArray(credentialFormats))
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw PidOfferException(describe(code, text))
            return JSONObject(text).optString("offerUri").ifBlank {
                throw PidOfferException("La Registraduría no devolvió la oferta de credencial.")
            }
        } catch (error: UnknownHostException) {
            throw PidOfferException("No se pudo resolver el dominio de la Registraduría (${error.message.orEmpty()}). Verifica el DNS y la conexión del celular al proxy.")
        } catch (error: SocketTimeoutException) {
            throw PidOfferException("Se agotó el tiempo de espera al contactar la Registraduría. Verifica que el proxy esté disponible.")
        } catch (error: IOException) {
            throw PidOfferException("No se pudo contactar la Registraduría: ${error.localizedMessage ?: "error de red"}.")
        } finally {
            conn.disconnect()
        }
    }

    private fun describe(code: Int, body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val reasons = json?.optJSONArray("reasons")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        val serverCode = json?.optString("code")?.takeIf(String::isNotBlank)
        val detail = reasons.takeIf { it.isNotEmpty() }?.joinToString()
        val friendly = when (serverCode) {
            "INVALID_REQUEST" -> "La solicitud enviada al issuer no tiene el formato esperado."
            "SESSION_NOT_FOUND" -> "No se encontró la sesión de validación."
            "ONBOARDING_UNAVAILABLE" -> "El servicio de validación no está disponible."
            "NOT_APPROVED" -> "La validación de identidad no está aprobada."
            "CEDULA_NOT_FOUND" -> "La cédula no aparece en la base de la Registraduría."
            "DATA_MISMATCH" -> "Los datos no coinciden con la Registraduría."
            "CEDULA_INCOMPLETE" -> "El registro de la cédula está incompleto."
            "FACE_MISMATCH" -> "Tu selfie no coincide con la foto que la Registraduría tiene de tu cédula. Repite la validación con buena luz, de frente y sin gafas ni gorra."
            "FACE_CHECK_UNAVAILABLE" -> "No se pudo comparar tu rostro con la Registraduría. Intenta de nuevo en unos minutos."
            "SUBJECT_BLOCKED" -> "La emisión está bloqueada para este titular."
            "INVALID_CREDENTIAL_FORMATS" -> "La solicitud contiene un formato de credencial no válido."
            "OFFER_ERROR" -> "El issuer no pudo construir la oferta de credenciales."
            else -> when (code) {
                404 -> "No se encontró la sesión o la cédula en la Registraduría."
                403 -> "La sesión no está aprobada o los datos no coinciden."
                else -> "La Registraduría respondió HTTP $code${serverCode?.let { " ($it)" }.orEmpty()}."
            }
        }
        return buildString {
            append(friendly)
            if (!detail.isNullOrBlank()) append(" Detalle: ").append(detail)
        }
    }
}
