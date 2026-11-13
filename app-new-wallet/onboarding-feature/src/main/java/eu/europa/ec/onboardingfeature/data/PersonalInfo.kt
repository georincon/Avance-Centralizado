package eu.europa.ec.onboardingfeature.data

import eu.europa.ec.onboardingfeature.network.BackendResult
import org.json.JSONObject
import java.time.LocalDate
import java.time.Period

/** Resultado de la validación: datos escritos vs. cédula y selfie vs. foto de la cédula. */
data class ValidationReport(
    val decision: String?,
    val documentNumberMatch: Boolean?,
    val birthDateMatch: Boolean?,
    val firstNamesSimilarity: Double?,
    val lastNamesSimilarity: Double?,
    val selfieQualityOk: Boolean?,
    val faceMatchOk: Boolean?,
    val faceMatchScore: Double?,
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("decision", decision)
        put("documentNumberMatch", documentNumberMatch)
        put("birthDateMatch", birthDateMatch)
        put("firstNamesSimilarity", firstNamesSimilarity)
        put("lastNamesSimilarity", lastNamesSimilarity)
        put("selfieQualityOk", selfieQualityOk)
        put("faceMatchOk", faceMatchOk)
        put("faceMatchScore", faceMatchScore)
    }

    companion object {
        fun fromJsonObject(o: JSONObject): ValidationReport {
            fun b(k: String) = if (o.isNull(k)) null else o.optBoolean(k)
            fun d(k: String) = if (o.isNull(k)) null else o.optDouble(k)
            return ValidationReport(
                decision = if (o.isNull("decision")) null else o.optString("decision"),
                documentNumberMatch = b("documentNumberMatch"),
                birthDateMatch = b("birthDateMatch"),
                firstNamesSimilarity = d("firstNamesSimilarity"),
                lastNamesSimilarity = d("lastNamesSimilarity"),
                selfieQualityOk = b("selfieQualityOk"),
                faceMatchOk = b("faceMatchOk"),
                faceMatchScore = d("faceMatchScore"),
            )
        }

        fun fromBackend(r: BackendResult): ValidationReport {
            val m = r.dataMatch
            return ValidationReport(
                decision = r.decision,
                documentNumberMatch = m["documentNumberMatch"]?.toBooleanStrictOrNull(),
                birthDateMatch = m["birthDateMatch"]?.toBooleanStrictOrNull(),
                firstNamesSimilarity = m["firstNamesSimilarity"]?.toDoubleOrNull(),
                lastNamesSimilarity = m["lastNamesSimilarity"]?.toDoubleOrNull(),
                selfieQualityOk = r.livenessStatus?.let { it == "SUCCESS" },
                faceMatchOk = r.faceMatchStatus?.let { it == "SUCCESS" },
                faceMatchScore = r.faceMatchScore,
            )
        }
    }
}

/**
 * Datos de la cédula ya validados en el onboarding (fuente: lectura del PDF417 + OCR verificados).
 * Se guardan cifrados (ver [PersonalInfoStore]) y alimentan la pantalla "Info Personal" y el cálculo
 * de la mayoría de edad.
 */
data class PersonalInfo(
    val documentNumber: String,
    val firstNames: String,
    val lastNames: String,
    val birthDate: String, // AAAA-MM-DD
    val birthPlace: String?,
    val gender: String?,
    val bloodType: String?,
    val heightCm: Int?,
    val issueDate: String?, // AAAA-MM-DD
    val expeditionPlace: String?,
    val validatedAtEpochMs: Long,
    /** Reporte final de la validación (comparaciones y resultado). */
    val report: ValidationReport? = null,
    /** Sesión de onboarding aprobada: permite pedir la PID a la Registraduría más tarde. */
    val sessionId: String? = null,
) {
    /** Edad en años cumplidos a la fecha indicada (null si la fecha de nacimiento no es válida). */
    fun ageOn(today: LocalDate = LocalDate.now()): Int? = runCatching {
        Period.between(LocalDate.parse(birthDate), today).years
    }.getOrNull()

    fun isOver(age: Int, today: LocalDate = LocalDate.now()): Boolean = (ageOn(today) ?: -1) >= age

    fun toJson(): String = JSONObject().apply {
        put("documentNumber", documentNumber)
        put("firstNames", firstNames)
        put("lastNames", lastNames)
        put("birthDate", birthDate)
        put("birthPlace", birthPlace)
        put("gender", gender)
        put("bloodType", bloodType)
        put("heightCm", heightCm)
        put("issueDate", issueDate)
        put("expeditionPlace", expeditionPlace)
        put("validatedAt", validatedAtEpochMs)
        put("report", report?.toJsonObject())
        put("sessionId", sessionId)
    }.toString()

    companion object {
        fun fromJson(json: String): PersonalInfo? = runCatching {
            val o = JSONObject(json)
            fun s(k: String) = if (o.isNull(k)) null else o.optString(k).ifBlank { null }
            PersonalInfo(
                documentNumber = o.getString("documentNumber"),
                firstNames = o.getString("firstNames"),
                lastNames = o.getString("lastNames"),
                birthDate = o.getString("birthDate"),
                birthPlace = s("birthPlace"),
                gender = s("gender"),
                bloodType = s("bloodType"),
                heightCm = if (o.isNull("heightCm")) null else o.optInt("heightCm"),
                issueDate = s("issueDate"),
                expeditionPlace = s("expeditionPlace"),
                validatedAtEpochMs = o.optLong("validatedAt"),
                report = o.optJSONObject("report")?.let { ValidationReport.fromJsonObject(it) },
                sessionId = s("sessionId"),
            )
        }.getOrNull()

        /** Construye los datos validados a partir del resultado APPROVED del backend. */
        fun fromBackend(r: BackendResult, now: Long = System.currentTimeMillis()): PersonalInfo? {
            val x = r.extracted
            val doc = x["documentNumber"] ?: return null
            val birth = x["birthDate"] ?: return null
            fun title(v: String?) = v?.lowercase()?.split(" ")
                ?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }?.ifBlank { null }
            val birthPlace = listOfNotNull(title(x["birthCity"]), title(x["birthDepartment"])?.let { "($it)" })
                .joinToString(" ").ifBlank { null }
            return PersonalInfo(
                documentNumber = doc,
                firstNames = title(listOfNotNull(x["firstName"], x["middleName"]).joinToString(" ")).orEmpty(),
                lastNames = title(listOfNotNull(x["lastName"], x["secondLastName"]).joinToString(" ")).orEmpty(),
                birthDate = birth,
                birthPlace = birthPlace,
                gender = x["gender"],
                bloodType = x["bloodType"],
                heightCm = x["heightCm"]?.toIntOrNull(),
                issueDate = x["issueDate"],
                expeditionPlace = title(x["expeditionPlace"]),
                validatedAtEpochMs = now,
                report = ValidationReport.fromBackend(r),
                sessionId = r.sessionId,
            )
        }
    }
}
