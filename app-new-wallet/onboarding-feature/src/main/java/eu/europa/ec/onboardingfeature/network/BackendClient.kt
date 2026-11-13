package eu.europa.ec.onboardingfeature.network

import eu.europa.ec.onboardingfeature.BuildConfig
import eu.europa.ec.onboardingfeature.data.OnboardingData
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class BackendResult(
    val sessionId: String,
    val decision: String?,
    val status: String,
    val failureReason: String?,
    val documentNumber: String?,
    val fullName: String?,
    val birthDate: String?,
    val gender: String?,
    val livenessStatus: String?,
    val faceMatchStatus: String?,
    val provider: String?,
    val faceMatchScore: Double? = null,
    val dataMatch: Map<String, String> = emptyMap(),
    val extracted: Map<String, String?> = emptyMap(),
)

/** El servidor no pudo leer la cédula (reverso/PDF417 ilegible): hay que volver a tomar las fotos. */
class DocumentUnreadableException(message: String) : Exception(message)

/**
 * Cliente HTTP minimo (sin dependencias) hacia el backend Spring Boot.
 * Usa HttpURLConnection y org.json. Pensado para la red local de desarrollo.
 */
class BackendClient(private val baseUrl: String = BuildConfig.BACKEND_BASE_URL) {

    fun runOnboarding(
        data: OnboardingData,
        frontImage: File,
        backImage: File,
        selfie: File,
    ): BackendResult {
        val sessionId = createSession()
        val code = startContact(sessionId, data.email, data.phone)
        verifyContact(sessionId, code)
        consent(sessionId)
        declareData(sessionId, data)
        uploadDocument(sessionId, "FRONT", frontImage)
        val back = uploadDocument(sessionId, "BACK", backImage)
        if (back.optString("sessionStatus") != "DOCUMENTS_CAPTURED") {
            val reason = getJson("/api/v1/onboarding/sessions/$sessionId").optStringOrNull("failureReason")
            throw DocumentUnreadableException("No pudimos leer tu cédula (${reason ?: "documento ilegible"}). Vuelve a tomar la foto del reverso con buena luz, sin reflejos y con el código de barras completo.")
        }
        uploadSelfie(sessionId, selfie)
        return getStatus(sessionId)
    }

    // ------------------------------------------------------------------ pasos

    private fun createSession(): String {
        val json = postJson("/api/v1/onboarding/sessions", null)
        return json.getString("id")
    }

    private fun startContact(sessionId: String, email: String, phone: String): String {
        val body = JSONObject().put("channel", "EMAIL").put("email", email).put("phoneNumber", phone)
        val json = postJson("/api/v1/onboarding/sessions/$sessionId/contact", body)
        return json.optString("devCode", "").ifBlank {
            error("El backend no devolvio el codigo (OTP_EXPOSE_CODE debe estar activo en dev)")
        }
    }

    private fun verifyContact(sessionId: String, code: String) {
        val body = JSONObject().put("code", code)
        postJson("/api/v1/onboarding/sessions/$sessionId/contact/verify", body)
    }

    private fun consent(sessionId: String) {
        val body = JSONObject().put("accepted", true).put("policyVersion", "v1")
        postJson("/api/v1/onboarding/sessions/$sessionId/consent", body)
    }

    /** Envia lo que escribio el usuario; el backend lo compara con el PDF417. */
    private fun declareData(sessionId: String, d: OnboardingData) {
        val body = JSONObject()
            .put("documentNumber", d.cedula)
            .put("firstNames", d.nombres)
            .put("lastNames", d.apellidos)
            .put("birthDate", toIsoDate(d.birthDate))
        postJson("/api/v1/onboarding/sessions/$sessionId/declared-data", body)
    }

    /** DD-MM-AAAA (como se escribe en la app) -> AAAA-MM-DD (API). */
    private fun toIsoDate(ddmmyyyy: String): String {
        val parts = ddmmyyyy.trim().split("-", "/", ".")
        require(parts.size == 3 && parts[2].length == 4) { "Fecha de nacimiento invalida, usa DD-MM-AAAA" }
        return "%s-%02d-%02d".format(parts[2], parts[1].toInt(), parts[0].toInt())
    }

    private fun uploadDocument(sessionId: String, side: String, file: File): JSONObject {
        return uploadMultipart(
            "/api/v1/onboarding/sessions/$sessionId/documents?side=$side",
            file,
            "file",
            contentTypeFor(file),
        )
    }

    private fun uploadSelfie(sessionId: String, file: File) {
        uploadMultipart(
            "/api/v1/onboarding/sessions/$sessionId/selfie",
            file,
            "file",
            contentTypeFor(file),
        )
    }

    private fun getStatus(sessionId: String): BackendResult {
        val json = getJson("/api/v1/onboarding/sessions/$sessionId")
        val data = json.optJSONObject("data")
        val biometric = json.optJSONObject("biometric")
        val fullName = listOfNotNull(
            data?.optString("firstName")?.takeIf { it.isNotBlank() },
            data?.optString("middleName")?.takeIf { it.isNotBlank() },
            data?.optString("lastName")?.takeIf { it.isNotBlank() },
            data?.optString("secondLastName")?.takeIf { it.isNotBlank() },
        ).joinToString(" ")
        val extracted = buildMap {
            put("documentNumber", data?.optStringOrNull("documentNumber"))
            put("firstName", data?.optStringOrNull("firstName"))
            put("middleName", data?.optStringOrNull("middleName"))
            put("lastName", data?.optStringOrNull("lastName"))
            put("secondLastName", data?.optStringOrNull("secondLastName"))
            put("birthDate", data?.optStringOrNull("birthDate"))
            put("gender", data?.optStringOrNull("gender"))
            put("bloodType", data?.optStringOrNull("bloodType"))
            put("birthCity", data?.optStringOrNull("birthCity"))
            put("birthDepartment", data?.optStringOrNull("birthDepartment"))
            put("heightCm", data?.optStringOrNull("heightCm"))
            put("expeditionPlace", data?.optStringOrNull("expeditionPlace"))
            put("issueDate", data?.optStringOrNull("issueDate"))
        }
        return BackendResult(
            sessionId = sessionId,
            decision = json.optStringOrNull("decision"),
            status = json.optString("status"),
            failureReason = json.optStringOrNull("failureReason"),
            documentNumber = data?.optStringOrNull("documentNumber"),
            fullName = fullName.ifBlank { null },
            birthDate = data?.optStringOrNull("birthDate"),
            gender = data?.optStringOrNull("gender"),
            livenessStatus = biometric?.optStringOrNull("livenessStatus"),
            faceMatchStatus = biometric?.optStringOrNull("faceMatchStatus"),
            provider = biometric?.optStringOrNull("provider"),
            faceMatchScore = biometric?.takeIf { it.has("faceMatchScore") && !it.isNull("faceMatchScore") }?.optDouble("faceMatchScore"),
            dataMatch = json.optJSONObject("dataMatch")?.let { m ->
                m.keys().asSequence().associateWith { k -> m.opt(k).toString() }
            } ?: emptyMap(),
            extracted = extracted,
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun postJson(path: String, body: JSONObject?): JSONObject =
        request("POST", path, body?.toString(), "application/json")

    private fun getJson(path: String): JSONObject =
        request("GET", path, null, null)

    private fun request(method: String, path: String, body: String?, contentType: String?): JSONObject {
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 60000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/json")
                setRequestProperty("Accept", "application/json")
            }
            if (method == "POST" && body == null) {
                doOutput = true
                setRequestProperty("Content-Length", "0")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                error("Backend respondio $code en $path: ${text.take(300)}")
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun uploadMultipart(path: String, file: File, field: String, contentType: String): JSONObject {
        val boundary = "----OnboardingBoundary" + UUID.randomUUID().toString().replace("-", "")
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 120000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        try {
            conn.outputStream.use { out ->
                writeMultipart(out, boundary, field, file.name, contentType, file.readBytes())
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                error("Backend respondio $code al subir ${file.name}: ${text.take(300)}")
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun writeMultipart(
        out: OutputStream,
        boundary: String,
        field: String,
        filename: String,
        contentType: String,
        bytes: ByteArray,
    ) {
        val header = buildString {
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"$field\"; filename=\"$filename\"\r\n")
            append("Content-Type: $contentType\r\n\r\n")
        }
        out.write(header.toByteArray())
        out.write(bytes)
        out.write("\r\n--$boundary--\r\n".toByteArray())
        out.flush()
    }

    private fun contentTypeFor(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (isNull(key)) return null
    val v = optString(key, "")
    return v.ifBlank { null }
}
