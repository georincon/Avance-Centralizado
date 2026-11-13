package eu.europa.ec.businesslogic.banco

import eu.europa.ec.businesslogic.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Credenciales de cuenta por recibir del banco. Cuando el titular abre una cuenta (ahorros, corriente, etc.) el banco deja
 * una oferta de su credencial; la wallet la consulta con la cédula del titular y la recibe sola, sin QR ni botón.
 * Cada oferta se entrega una sola vez. Nunca lanza excepciones: sin red o sin banco configurado devuelve una lista vacía.
 */
object BancoOfertas {

    private val urlBase: String get() = BuildConfig.NW_BANCO_URL.trimEnd('/')
    val activo: Boolean get() = !urlBase.contains(".invalid")

    /** Enlaces `openid-credential-offer://…` pendientes para esa cédula. Bloquea: llamar fuera del hilo principal. */
    fun pendientes(cedula: String): List<String> = runCatching {
        if (!activo || cedula.isBlank()) return emptyList()
        val conn = (URL("$urlBase/api/wallet/pendientes?doc=${URLEncoder.encode(cedula, "UTF-8")}").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) return emptyList()
            val ofertas = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("ofertas")
            (0 until (ofertas?.length() ?: 0)).map { ofertas!!.getString(it) }
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(emptyList())
}
