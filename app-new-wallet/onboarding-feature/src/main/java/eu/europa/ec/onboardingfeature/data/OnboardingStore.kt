package eu.europa.ec.onboardingfeature.data

import android.content.Context
import org.json.JSONObject
import java.io.File

class OnboardingStore(private val context: Context) {

    private val file: File get() = File(context.filesDir, "onboarding.json")

    fun load(): OnboardingData = runCatching {
        if (!file.exists()) return OnboardingData()
        // La sesión del servidor y los códigos caducan: pasadas 24 h se empieza de cero.
        if (System.currentTimeMillis() - file.lastModified() > SESSION_TTL_MS) {
            file.delete()
            return OnboardingData()
        }
        fromJson(JSONObject(file.readText()))
    }.getOrDefault(OnboardingData())

    fun save(data: OnboardingData) {
        runCatching { file.writeText(toJson(data).toString(2)) }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    private fun toJson(d: OnboardingData): JSONObject = JSONObject().apply {
        put("phone", d.phone)
        put("phoneOtpGenerated", d.phoneOtpGenerated)
        put("phoneVerified", d.phoneVerified)
        put("cedula", d.cedula)
        put("nombres", d.nombres)
        put("apellidos", d.apellidos)
        put("birthDate", d.birthDate)
        put("email", d.email)
        put("emailOtpGenerated", d.emailOtpGenerated)
        put("emailVerified", d.emailVerified)
        put("consentAccepted", d.consentAccepted)
        put("frontImagePath", d.frontImagePath ?: JSONObject.NULL)
        put("backImagePath", d.backImagePath ?: JSONObject.NULL)
        put("selfieImagePath", d.selfieImagePath ?: JSONObject.NULL)
    }

    private fun fromJson(o: JSONObject): OnboardingData = OnboardingData(
        phone = o.optString("phone", ""),
        phoneOtpGenerated = o.optString("phoneOtpGenerated", ""),
        phoneVerified = o.optBoolean("phoneVerified", false),
        cedula = o.optString("cedula", ""),
        nombres = o.optString("nombres", ""),
        apellidos = o.optString("apellidos", ""),
        birthDate = o.optString("birthDate", ""),
        email = o.optString("email", ""),
        emailOtpGenerated = o.optString("emailOtpGenerated", ""),
        emailVerified = o.optBoolean("emailVerified", false),
        consentAccepted = o.optBoolean("consentAccepted", false),
        frontImagePath = o.optString("frontImagePath").takeIf { it.isNotBlank() && it != "null" },
        backImagePath = o.optString("backImagePath").takeIf { it.isNotBlank() && it != "null" },
        selfieImagePath = o.optString("selfieImagePath").takeIf { it.isNotBlank() && it != "null" }
    )

    private companion object {
        const val SESSION_TTL_MS = 24L * 60 * 60 * 1000
    }
}
