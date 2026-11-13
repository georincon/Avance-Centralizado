package eu.europa.ec.businesslogic.auditoria

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import eu.europa.ec.businesslogic.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Reporte de actividad de la wallet al centro de auditoría centralizado (srv-auditoria).
 *
 * Cada acción (ingreso, salida, solicitud/recepción de credenciales, presentaciones, errores) se convierte en un
 * evento con identificador propio y se guarda en una cola en disco; un hilo la envía al centro y, si no hay red,
 * la conserva y reintenta. El centro ignora los eventos repetidos, así que reintentar es seguro.
 *
 * Solo se envían NOMBRES de atributos (qué se compartió), nunca sus valores.
 * Nunca lanza excepciones hacia la app: la auditoría no puede romper un trámite.
 */
object Auditoria {

    private const val ARCHIVO_COLA = "auditoria_cola.jsonl"
    private const val MAX_COLA = 2000
    private const val PREFS = "auditoria"

    private val ejecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "auditoria").apply { isDaemon = true } }
    private val ui = Handler(Looper.getMainLooper())
    private val cola = ArrayDeque<String>()

    private var contexto: Context? = null
    private var walletId: String = ""
    @Volatile private var sesionId: String? = null
    @Volatile private var sesionMetodo: String? = null
    @Volatile private var actividadesIniciadas = 0
    private var cierrePendiente: Runnable? = null

    private val urlBase: String get() = BuildConfig.NW_AUDITORIA_URL.trimEnd('/')
    private val activo: Boolean get() = contexto != null && BuildConfig.NW_AUDITORIA_CLAVE.isNotBlank() && !urlBase.contains(".invalid")

    /** Se llama una vez, desde la Application. */
    fun iniciar(app: Application) = runCatching {
        contexto = app.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        walletId = prefs.getString("wallet_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("wallet_id", it).apply() }
        ejecutor.execute { cargarCola(); vaciarCola() }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) {
                actividadesIniciadas++
                cierrePendiente?.let { ui.removeCallbacks(it) }
                cierrePendiente = null
                ejecutor.execute { vaciarCola() }
            }
            override fun onActivityStopped(a: Activity) {
                actividadesIniciadas = (actividadesIniciadas - 1).coerceAtLeast(0)
                // Si la app se queda sin pantallas visibles unos segundos, la persona se desconectó.
                if (actividadesIniciadas == 0 && !a.isChangingConfigurations) {
                    val r = Runnable { if (actividadesIniciadas == 0) cierreSesion("segundo_plano") }
                    cierrePendiente = r
                    ui.postDelayed(r, 4000)
                }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    /** Alta automática de la wallet en el centro: se llama al aprobar el onboarding, con los datos del usuario y del móvil. */
    fun altaWallet(usuario: JSONObject, contactos: JSONArray) {
        guardarPerfil(usuario, contactos)
        registrar(tipo = "registro_usuario", resultado = "exitoso", detalle = JSONObject().put("metodo", "cédula + selfie"), conPerfil = true)
    }

    /** El registro no terminó (validación rechazada, error de red...). */
    fun altaFallida(mensaje: String?, codigo: String? = null) =
        registrar(tipo = "registro_usuario", resultado = "fallido", mensaje = mensaje, codigoError = codigo, detalle = JSONObject().put("metodo", "cédula + selfie"), conPerfil = false)

    private fun datosDispositivo(): JSONObject {
        val ctx = contexto!!
        val dm = ctx.resources.displayMetrics
        val pi = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
        val ram = runCatching {
            val i = android.app.ActivityManager.MemoryInfo()
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(i); (i.totalMem / 1048576L).toInt()
        }.getOrNull()
        val km = runCatching { (ctx.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure }.getOrNull()
        val bio = runCatching { androidx.biometric.BiometricManager.from(ctx).canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS }.getOrNull()
        return JSONObject().apply {
            put("identificador_android", runCatching { android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID) }.getOrNull())
            put("fabricante", Build.MANUFACTURER); put("marca", Build.BRAND); put("modelo", Build.MODEL); put("producto", Build.PRODUCT); put("hardware", Build.HARDWARE)
            put("version_android", Build.VERSION.RELEASE); put("nivel_api", Build.VERSION.SDK_INT); put("parche_seguridad", Build.VERSION.SECURITY_PATCH)
            put("version_app", pi?.versionName); put("codigo_version_app", pi?.longVersionCode?.toInt())
            put("arquitectura", Build.SUPPORTED_ABIS.firstOrNull()); put("memoria_ram_mb", ram)
            put("pantalla", "${dm.widthPixels}x${dm.heightPixels} @ ${dm.densityDpi} dpi")
            put("idioma", java.util.Locale.getDefault().toLanguageTag()); put("zona_horaria", java.util.TimeZone.getDefault().id)
            bio?.let { put("biometria_disponible", it) }; km?.let { put("bloqueo_pantalla", it) }
        }
    }

    // ───────────────────────── sesión ─────────────────────────

    /** Ingreso a la wallet (PIN o biometría). */
    fun inicioSesion(metodo: String, exitoso: Boolean = true, tipoError: String? = null, codigoError: String? = null, mensaje: String? = null) {
        if (exitoso) { sesionId = UUID.randomUUID().toString(); sesionMetodo = metodo }
        registrar(
            tipo = "inicio_sesion", resultado = if (exitoso) "exitoso" else "fallido",
            tipoError = tipoError, codigoError = codigoError, mensaje = mensaje,
            detalle = JSONObject().put("metodo", metodo), conPerfil = exitoso,
            sesionEfimera = !exitoso,
        )
    }

    /** Salida de la wallet: el usuario sale o la app pasa a segundo plano. */
    fun cierreSesion(motivo: String) {
        if (sesionId == null) return
        registrar(tipo = "cierre_sesion", resultado = "exitoso", detalle = JSONObject().put("motivo", motivo))
        sesionId = null
        sesionMetodo = null
    }

    // ───────────────────────── perfil ─────────────────────────

    /** Datos personales validados y medios de contacto del usuario; se adjuntan al reportar ingresos. */
    fun guardarPerfil(usuario: JSONObject, contactos: JSONArray) {
        runCatching {
            contexto?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
                ?.putString("perfil_usuario", usuario.toString())?.putString("perfil_contactos", contactos.toString())?.apply()
        }
    }

    // ───────────────────────── eventos ─────────────────────────

    fun registrar(
        tipo: String,
        resultado: String = "exitoso",
        contraparteTipo: String? = null,
        contraparteId: String? = null,
        contraparteNombre: String? = null,
        credencialTipo: String? = null,
        canal: String? = null,
        duracionMs: Long? = null,
        tipoError: String? = null,
        codigoError: String? = null,
        mensaje: String? = null,
        elementos: List<Triple<String, String, String>> = emptyList(), // (sentido, tipoCredencial, elemento)
        detalle: JSONObject? = null,
        conPerfil: Boolean = false,
        sesionEfimera: Boolean = false,
    ) {
        if (!activo) return
        val ocurrido = Instant.now().toString()
        val sesion = sesionId
        val metodo = sesionMetodo
        runCatching {
            val e = JSONObject()
            e.put("evento_id", UUID.randomUUID().toString())
            val prefs = contexto!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val completo = conPerfil || tipo == "registro_usuario" || tipo == "actualizacion_perfil"
            e.put("wallet", JSONObject().apply {
                put("wallet_id", walletId)
                // Los datos del teléfono viajan en el alta y en cada ingreso (así el centro detecta cambios de versión).
                if (completo) put("dispositivo", datosDispositivo())
            })
            if (completo) {
                prefs.getString("perfil_usuario", null)?.let { e.put("usuario", JSONObject(it)) }
                prefs.getString("perfil_contactos", null)?.let { e.put("contactos", JSONArray(it)) }
            }
            if (sesion != null && !sesionEfimera) e.put("sesion", JSONObject().put("sesion_id", sesion).put("metodo_autenticacion", metodo))
            val a = JSONObject().apply {
                put("tipo", tipo); put("ocurrido_en", ocurrido); put("resultado", resultado)
                canal?.let { put("canal", it) }; duracionMs?.let { put("duracion_ms", it) }
                credencialTipo?.let { put("credencial_tipo", it) }
                if (resultado != "exitoso") {
                    put("tipo_error", tipoError ?: clasificarError(mensaje)); codigoError?.let { put("codigo_error", it) }
                    mensaje?.let { put("mensaje_error", it.take(900)) }
                }
                if (!contraparteId.isNullOrBlank()) put("contraparte", JSONObject().put("tipo", contraparteTipo ?: "desconocida").put("identificador", contraparteId).put("nombre", contraparteNombre))
                if (elementos.isNotEmpty()) put("elementos", JSONArray().also { arr -> elementos.forEach { (s, t, el) -> arr.put(JSONObject().put("sentido", s).put("tipo_credencial", t).put("elemento", el)) } })
                detalle?.let { put("detalle", it) }
            }
            e.put("actividad", a)
            ejecutor.execute { encolar(e.toString()); vaciarCola() }
        }
    }

    /** Clasifica un mensaje de error en las categorías del centro. */
    fun clasificarError(mensaje: String?): String {
        val m = mensaje.orEmpty().lowercase()
        return when {
            listOf("timeout", "timed out", "unknownhost", "unable to resolve", "connect", "network", "ssl", "socket", "unreachable").any { m.contains(it) } -> "red"
            listOf("pin", "biometr", "authentic", "unlock", "fingerprint").any { m.contains(it) } -> "autenticacion"
            listOf("untrusted", "trust", "no es de confianza", "verifier", "verificador").any { m.contains(it) } -> "verificador"
            listOf("cancel", "denied", "rechaz").any { m.contains(it) } -> "usuario"
            listOf("issuer", "emisor", "invalid_grant", "invalid_request", "credential").any { m.contains(it) } -> "emisor"
            listOf("invalid", "attestation", "validation", "signature", "expired").any { m.contains(it) } -> "validacion"
            else -> "interno"
        }
    }

    // ───────────────────────── cola y envío ─────────────────────────

    private fun archivo() = File(contexto!!.filesDir, ARCHIVO_COLA)

    private fun cargarCola() = runCatching {
        val f = archivo(); if (f.exists()) f.readLines().filter { it.isNotBlank() }.forEach { cola.addLast(it) }
    }

    private fun encolar(linea: String) {
        cola.addLast(linea)
        while (cola.size > MAX_COLA) cola.removeFirst()
        runCatching { archivo().appendText(linea + "\n") }
    }

    private fun persistirCola() = runCatching { archivo().writeText(cola.joinToString("\n") + if (cola.isEmpty()) "" else "\n") }

    /** Envía en orden lo pendiente; se detiene en el primer fallo de red y lo reintenta en el siguiente evento o al volver a la app. */
    private fun vaciarCola() {
        if (!activo) return
        var enviados = 0
        while (cola.isNotEmpty()) {
            when (enviar(cola.first())) {
                Envio.OK, Envio.DESCARTAR -> { cola.removeFirst(); enviados++ }
                Envio.REINTENTAR -> break
            }
        }
        if (enviados > 0) persistirCola()
    }

    private enum class Envio { OK, REINTENTAR, DESCARTAR }

    private fun enviar(linea: String): Envio = runCatching {
        val c = (URL("$urlBase/api/v1/eventos").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 8000; readTimeout = 8000; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Api-Key", BuildConfig.NW_AUDITORIA_CLAVE)
        }
        c.outputStream.use { it.write(linea.toByteArray()) }
        val code = c.responseCode
        c.disconnect()
        when {
            code in 200..299 -> Envio.OK
            code in 400..499 && code != 401 && code != 429 -> Envio.DESCARTAR   // evento inválido: reintentar no lo arregla
            else -> Envio.REINTENTAR
        }
    }.getOrDefault(Envio.REINTENTAR)
}
