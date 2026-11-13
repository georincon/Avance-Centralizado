package eu.europa.ec.onboardingfeature.network

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Un programa del estudiante, tal como lo devuelve el issuer de la UIS. */
data class UisPrograma(val historialId: Long, val nombre: String, val nivel: String, val estado: String, val titulo: String)

sealed interface UisIngresoEstado {
    data object Esperando : UisIngresoEstado
    data class Listo(val nombre: String, val programas: List<UisPrograma>) : UisIngresoEstado
    data class Error(val motivo: String) : UisIngresoEstado
}

class UisIngresoException(message: String) : Exception(message)

/**
 * Ingreso del estudiante a la UIS con la cédula digital (PID), sin contraseña ni navegador:
 *  1. [iniciar] crea la solicitud de PID en el verifier (devuelve un enlace `haip-vp://…` que la wallet procesa como cualquier
 *     solicitud de presentación: el usuario ve qué comparte y lo confirma);
 *  2. [estado] espera a que el PID se verifique y devuelve los programas del estudiante con esa cédula;
 *  3. [oferta] devuelve la oferta de la credencial elegida, con un ticket de un solo uso.
 */
class UisIngresoClient(private val issuerBaseUrl: String) {

    fun iniciar(): Pair<String, String> {
        val j = JSONObject(call("POST", "/issuer/api/ingreso", null))
        return j.getString("id") to j.getString("requestUri")
    }

    fun estado(id: String): UisIngresoEstado {
        val j = JSONObject(call("GET", "/issuer/api/ingreso/$id", null))
        return when (j.getString("estado")) {
            "esperando" -> UisIngresoEstado.Esperando
            "listo" -> UisIngresoEstado.Listo(j.optString("nombre"), j.optJSONArray("programas").toProgramas())
            "no_estudiante" -> UisIngresoEstado.Error("No hay un estudiante de la UIS con tu cédula.")
            else -> UisIngresoEstado.Error(j.optString("motivo").ifBlank { "No se pudo verificar tu cédula digital." })
        }
    }

    fun oferta(id: String, historialId: Long, credencial: String): String =
        JSONObject(call("POST", "/issuer/api/ofertas", JSONObject().put("id", id).put("historialId", historialId).put("credencial", credencial).toString()))
            .getString("offerUri")

    private fun JSONArray?.toProgramas(): List<UisPrograma> =
        (0 until (this?.length() ?: 0)).map {
            val p = this!!.getJSONObject(it)
            UisPrograma(p.getLong("historialId"), p.getString("nombre"), p.optString("nivel"), p.optString("estado"), p.optString("titulo"))
        }

    private fun call(method: String, path: String, body: String?): String {
        val conn = (URL(issuerBaseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toByteArray()) }
            }
        }
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw UisIngresoException(describe(code, text))
            return text
        } catch (e: java.io.IOException) {
            throw UisIngresoException("No se pudo contactar a la UIS: ${e.localizedMessage ?: "error de red"}.")
        } finally {
            conn.disconnect()
        }
    }

    private fun describe(code: Int, body: String): String =
        when (runCatching { JSONObject(body).optString("code") }.getOrNull()) {
            "VERIFIER_UNAVAILABLE" -> "El servicio de verificación de la UIS no está disponible."
            "INGRESO_NOT_FOUND" -> "La solicitud venció. Vuelve a empezar."
            "NOT_VERIFIED" -> "Primero debes compartir tu cédula digital."
            "CARNET_ONLY_ACTIVE" -> "El carné solo se emite para programas activos."
            "PROGRAM_NOT_FOUND" -> "Ese programa no es tuyo."
            else -> "La UIS respondió HTTP $code."
        }
}
