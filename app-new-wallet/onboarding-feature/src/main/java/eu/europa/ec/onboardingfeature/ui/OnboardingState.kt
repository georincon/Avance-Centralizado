package eu.europa.ec.onboardingfeature.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.europa.ec.onboardingfeature.data.OnboardingData
import eu.europa.ec.onboardingfeature.data.OnboardingStatus
import eu.europa.ec.onboardingfeature.data.OtherData
import eu.europa.ec.onboardingfeature.data.OtherDataStore
import eu.europa.ec.onboardingfeature.data.PersonalInfo
import eu.europa.ec.businesslogic.auditoria.Auditoria
import eu.europa.ec.onboardingfeature.data.PersonalInfoStore
import org.json.JSONArray
import org.json.JSONObject
import eu.europa.ec.onboardingfeature.data.OnboardingStore
import eu.europa.ec.onboardingfeature.navigation.Step
import eu.europa.ec.onboardingfeature.network.BackendClient
import eu.europa.ec.onboardingfeature.network.BackendResult
import eu.europa.ec.onboardingfeature.network.DocumentUnreadableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class OnboardingState(
    private val store: OnboardingStore,
    private val status: OnboardingStatus,
    private val personalInfoStore: PersonalInfoStore,
    private val otherDataStore: OtherDataStore,
) {

    /**
     * Reanudación híbrida: se conserva lo ya verificado (celular, correo, datos declarados) pero las
     * capturas biométricas/documentales nunca se reutilizan: se descartan al abrir el flujo.
     */
    var data by mutableStateOf(store.load())
        private set

    /**
     * Descarta capturas de una sesión anterior. Se llama UNA sola vez al entrar al flujo (no en el
     * constructor: si el estado se recrea a mitad del flujo no debe borrar las fotos recién tomadas).
     */
    fun discardStaleCaptures() {
        data = discardCaptures(data)
    }

    val hasAllCaptures: Boolean
        get() = listOf(data.frontImagePath, data.backImagePath, data.selfieImagePath)
            .all { it != null && File(it).exists() }

    private fun discardCaptures(d: OnboardingData): OnboardingData {
        listOf(d.frontImagePath, d.backImagePath, d.selfieImagePath)
            .forEach { p -> p?.let { runCatching { File(it).delete() } } }
        return d.copy(frontImagePath = null, backImagePath = null, selfieImagePath = null)
            .also { store.save(it) }
    }

    /** Primer paso pendiente según lo ya verificado. */
    fun resumeStep(): Step = when {
        !data.phoneVerified -> Step.Phone
        data.cedula.isBlank() || data.nombres.isBlank() || data.apellidos.isBlank() || data.birthDate.isBlank() -> Step.Cedula
        !data.emailVerified -> Step.Email
        !data.consentAccepted -> Step.Summary
        else -> Step.DocumentFront
    }

    /** Validación negativa: conserva contacto verificado, borra capturas y resultado. */
    fun prepareRetry() {
        data = discardCaptures(data).copy(consentAccepted = false).also { store.save(it) }
        resetBackend()
    }

    /** Validación aprobada: se limpian los datos temporales y se marca el onboarding como completado. */
    suspend fun markCompleted() {
        // Se conservan (cifrados) los datos validados de la cédula para "Info Personal" y la mayoría de edad.
        val info = backendResult?.let { PersonalInfo.fromBackend(it) }
        info?.let { personalInfoStore.save(it) }
        // ALTA AUTOMÁTICA en el centro de auditoría: la wallet se registra con los datos del usuario, sus medios de
        // contacto y los del teléfono, y desde aquí queda su historial de actividad.
        info?.let { i ->
            Auditoria.altaWallet(
                usuario = JSONObject().apply {
                    put("tipo_documento", "CC"); put("numero_documento", i.documentNumber)
                    put("nombres", i.firstNames); put("apellidos", i.lastNames); put("fecha_nacimiento", i.birthDate)
                    put("lugar_nacimiento", i.birthPlace); put("genero", i.gender); put("grupo_sanguineo", i.bloodType)
                    put("estatura_cm", i.heightCm); put("fecha_expedicion", i.issueDate); put("lugar_expedicion", i.expeditionPlace)
                    put("validacion_decision", i.report?.decision); put("validacion_puntaje_rostro", i.report?.faceMatchScore)
                },
                contactos = JSONArray().apply {
                    if (data.phone.isNotBlank()) put(JSONObject().put("tipo", "celular").put("valor", data.phone).put("verificado", data.phoneVerified))
                    if (data.email.isNotBlank()) put(JSONObject().put("tipo", "correo").put("valor", data.email).put("verificado", data.emailVerified))
                }
            )
        }
        // El celular y el correo van aparte ("Otros datos"); se guardan aqui porque el archivo temporal
        // del onboarding se borra abajo.
        if (data.phone.isNotBlank() || data.email.isNotBlank()) {
            otherDataStore.save(OtherData(phone = data.phone.ifBlank { null }, email = data.email.ifBlank { null }))
        }
        listOf(data.frontImagePath, data.backImagePath, data.selfieImagePath)
            .forEach { p -> p?.let { runCatching { File(it).delete() } } }
        store.clear()
        status.markCompleted()
    }

    var backendResult by mutableStateOf<BackendResult?>(null)
        private set

    var backendError by mutableStateOf<String?>(null)
        private set

    var backendRunning by mutableStateOf(false)
        private set

    /** El último intento falló porque la cédula no se pudo leer: hay que volver a tomar las fotos. */
    var retakeNeeded by mutableStateOf(false)
        private set

    private fun commit(updated: OnboardingData) {
        data = updated
        store.save(updated)
    }

    private fun newCode(): String = (100000..999999).random().toString()

    fun updatePhone(value: String) = commit(data.copy(phone = value.filter { it.isDigit() }.take(10)))

    /** Emula el envio del codigo por SMS: se genera en la app y se muestra en pantalla. */
    fun sendPhoneOtp() {
        commit(data.copy(phoneOtpGenerated = newCode(), phoneVerified = false))
    }

    fun verifyPhoneOtp(input: String): Boolean {
        val ok = input.isNotBlank() && input == data.phoneOtpGenerated
        if (ok) commit(data.copy(phoneVerified = true))
        return ok
    }

    /** Emula el envio del codigo por correo. */
    fun sendEmailOtp() {
        commit(data.copy(emailOtpGenerated = newCode(), emailVerified = false))
    }

    fun verifyEmailOtp(input: String): Boolean {
        val ok = input.isNotBlank() && input == data.emailOtpGenerated
        if (ok) commit(data.copy(emailVerified = true))
        return ok
    }

    fun updateCedula(value: String) = commit(data.copy(cedula = value.filter { it.isDigit() }.take(10)))

    fun updateNombres(value: String) = commit(data.copy(nombres = value))

    fun updateApellidos(value: String) = commit(data.copy(apellidos = value))

    fun updateBirthDate(value: String) = commit(data.copy(birthDate = value))

    fun updateEmail(value: String) = commit(data.copy(email = value.trim()))

    fun updateConsent(value: Boolean) = commit(data.copy(consentAccepted = value))

    fun updateFrontImage(path: String?) = commit(data.copy(frontImagePath = path))

    fun updateBackImage(path: String?) = commit(data.copy(backImagePath = path))

    fun updateSelfie(path: String?) = commit(data.copy(selfieImagePath = path))

    /**
     * Ejecuta el flujo completo contra el backend (red local): crea la sesion,
     * verifica contacto/consentimiento, sube las imagenes (frente, reverso, selfie)
     * y consulta el resultado (OCR + biometria).
     */
    suspend fun runBackend() {
        val d = data
        val front = d.frontImagePath?.let { File(it) }?.takeIf { it.exists() }
        val back = d.backImagePath?.let { File(it) }?.takeIf { it.exists() }
        val selfie = d.selfieImagePath?.let { File(it) }?.takeIf { it.exists() }
        if (front == null || back == null || selfie == null) {
            backendError = "Faltan imágenes capturadas (cédula frente/reverso y selfie)."
            return
        }
        backendRunning = true
        backendError = null
        backendResult = null
        try {
            val result = withContext(Dispatchers.IO) {
                BackendClient().runOnboarding(d, front, back, selfie)
            }
            backendResult = result
            // En cuanto la validación sale APPROVED, los datos y el reporte se guardan (cifrados) y el onboarding
            // queda completado: así no se pierden aunque se cierre la app o falle la emisión de la credencial.
            if (result.decision == "APPROVED" && !completedMarked) {
                markCompleted()
                completedMarked = true
            } else if (result.decision != "APPROVED") {
                Auditoria.altaFallida(result.failureReason ?: "Validación de identidad no aprobada: ${result.decision}", "VALIDACION_${result.decision ?: "SIN_DECISION"}")
            }
        } catch (e: DocumentUnreadableException) {
            backendError = e.message
            Auditoria.altaFallida(e.message, "DOCUMENTO_ILEGIBLE")
            retakeNeeded = true
            // Las fotos que no sirvieron se eliminan: se vuelven a tomar desde cero.
            data = discardCaptures(data)
        } catch (e: Exception) {
            backendError = e.message ?: "No se pudo conectar con el backend."
            Auditoria.altaFallida(backendError, "ERROR_BACKEND_ONBOARDING")
        } finally {
            backendRunning = false
        }
    }

    /** Borra todo lo capturado (datos e imagenes) para empezar una validacion nueva. */
    fun resetAll() {
        listOf(data.frontImagePath, data.backImagePath, data.selfieImagePath)
            .forEach { p -> p?.let { runCatching { File(it).delete() } } }
        store.clear()
        data = OnboardingData()
        resetBackend()
    }

    /** Decisión no aprobada por datos que no coinciden con la cédula (vs. otras causas: rostro, calidad). */
    fun hasDataMismatch(): Boolean {
        val m = backendResult?.dataMatch.orEmpty()
        return m["documentNumberMatch"] == "false" || m["birthDateMatch"] == "false" ||
            (m["firstNamesSimilarity"]?.toDoubleOrNull() ?: 1.0) < 0.75 ||
            (m["lastNamesSimilarity"]?.toDoubleOrNull() ?: 1.0) < 0.75
    }

    private var completedMarked = false

    /** Descarta solo las capturas (fotos) y el resultado; conserva los datos verificados. */
    fun prepareRetakePhotos() {
        data = discardCaptures(data)
        resetBackend()
    }

    fun resetBackend() {
        retakeNeeded = false
        backendRunning = false
        backendError = null
        backendResult = null
    }
}
