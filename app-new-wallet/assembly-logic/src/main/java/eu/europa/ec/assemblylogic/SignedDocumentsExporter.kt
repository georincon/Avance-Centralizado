/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.assemblylogic

import android.app.Activity
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import eu.europa.ec.businesslogic.signature.PdfSignatureTimestamper
import eu.europa.ec.resourceslogic.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Guarda los PDF firmados en una carpeta propia del telefono, `Documents/Signed`, para que no queden
 * dispersos.
 *
 * El SDK de firma (eudi-lib-android-rqes-ui) crea cada PDF firmado dentro de la cache privada de la app
 * (`cache/signed_pdfs`) y solo lo ofrece para compartir; no se puede configurar su destino. Por eso, al
 * cerrarse su pantalla (o al volver a la app) se copian a `Documents/Signed` los que todavia no se han
 * copiado. Se usa MediaStore (Android 10+), asi que no hace falta ningun permiso de almacenamiento.
 *
 * El SDK siempre firma en nivel basico (B-B), sin sello de tiempo. Al copiar, si hay una autoridad de sellado
 * configurada, se le pide un sello sobre la firma y se anade al PDF exportado (PAdES B-T): ver
 * [PdfSignatureTimestamper]. Sin conexion con la autoridad, el PDF se copia tal cual, en B-B.
 */
object SignedDocumentsExporter {

    private const val SDK_CACHE_DIR = "signed_pdfs"
    private const val TARGET_DIR = "Documents/Signed"
    private const val PREFS = "signed_documents_export"
    private const val PREFS_DONE = "done"

    // Evita copiar un PDF que el SDK todavia esta escribiendo.
    private const val MIN_AGE_MILLIS = 1_500L

    private val lock = Any()

    // Autoridad de sellado de tiempo (RFC 3161); null desactiva el sello.
    @Volatile
    private var tsaUrl: String? = null

    fun register(application: Application, tsaUrl: String? = null) {
        this.tsaUrl = tsaUrl
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStopped(activity: Activity) {
                if (activity.isRqesContainer()) exportAsync(activity.applicationContext)
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (activity.isRqesContainer()) exportAsync(activity.applicationContext)
            }

            // Respaldo: si la pantalla del SDK se cerro de golpe, se recupera al volver a la app.
            override fun onActivityResumed(activity: Activity) {
                if (!activity.isRqesContainer()) exportAsync(activity.applicationContext)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
    }

    private fun Activity.isRqesContainer() = javaClass.name.contains("EudiRQESContainer")

    private fun exportAsync(context: Context) {
        Thread { export(context) }.start()
    }

    private fun export(context: Context) {
        val saved = synchronized(lock) {
            val dir = File(context.cacheDir, SDK_CACHE_DIR)
            val files = dir.listFiles { f -> f.isFile && f.extension.equals("pdf", ignoreCase = true) }
                ?: return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val done = prefs.getStringSet(PREFS_DONE, emptySet()).orEmpty().toMutableSet()
            val now = System.currentTimeMillis()

            var count = 0
            var sinSello = 0
            for (file in files) {
                val key = "${file.name}:${file.lastModified()}"
                if (key in done || file.length() == 0L || now - file.lastModified() < MIN_AGE_MILLIS) continue
                // El SDK escribe el PDF con el hueco de la firma VACIO antes de pedir la firma al servicio;
                // solo se copia cuando la firma ya esta incrustada.
                if (!hasEmbeddedSignature(file)) continue
                val (bytes, sellado) = withTimestamp(file)
                if (copyToDocuments(context, file.name, bytes)) {
                    done += key
                    count++
                    if (!sellado) sinSello++
                }
            }
            if (count > 0) prefs.edit().putStringSet(PREFS_DONE, done).apply()
            count to sinSello
        }
        val (guardados, sinSelloTotal) = saved
        if (guardados > 0) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    context.getString(
                        if (sinSelloTotal == 0) R.string.signed_documents_saved
                        else R.string.signed_documents_saved_no_timestamp
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * `true` si el PDF tiene una firma incrustada: el campo `/Contents` del ultimo `/ByteRange` trae datos y no
     * solo el relleno de ceros o espacios que deja el SDK al reservar el hueco.
     */
    private fun hasEmbeddedSignature(file: File): Boolean = runCatching {
        val bytes = file.readBytes()
        val g = BYTE_RANGE.findAll(String(bytes, Charsets.ISO_8859_1)).lastOrNull()?.groupValues
            ?: return@runCatching false
        val start = g[1].toInt() + g[2].toInt() // donde empieza "<hex>"
        val end = g[3].toInt()
        if (start !in 0 until end || end > bytes.size) return@runCatching false
        // Con el hueco vacio solo hay '<', '>', ceros o espacios; una firma real trae otros digitos.
        val vacio = "<> 0\r\n".map { it.code.toByte() }.toSet()
        bytes.slice(start until end).any { it !in vacio }
    }.getOrDefault(false)

    private val BYTE_RANGE = Regex("/ByteRange\\s*\\[\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*\\]")

    /** Los bytes a exportar y si llevan sello de tiempo. Si el sello falla, se exporta la firma tal cual. */
    private fun withTimestamp(file: File): Pair<ByteArray, Boolean> {
        val original = file.readBytes()
        val url = tsaUrl
        if (url.isNullOrBlank()) return original to false
        return try {
            PdfSignatureTimestamper.addSignatureTimestamp(original) { consulta -> askTimestamp(url, consulta) } to true
        } catch (_: Throwable) {
            original to false
        }
    }

    private fun askTimestamp(url: String, query: ByteArray): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/timestamp-query")
            connection.outputStream.use { it.write(query) }
            check(connection.responseCode == 200) { "La autoridad de sellado respondio ${connection.responseCode}" }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun copyToDocuments(context: Context, name: String, bytes: ByteArray): Boolean = runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, TARGET_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = checkNotNull(resolver.insert(collection, values)) { "No se pudo crear el archivo" }
        try {
            resolver.openOutputStream(uri)?.use { out -> out.write(bytes) }
                ?: error("No se pudo abrir el archivo")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        true
    }.getOrDefault(false)
}
