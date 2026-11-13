package com.onboarding.provider

import com.onboarding.domain.CheckStatus
import com.onboarding.domain.ContactChannel
import java.time.LocalDate

/** Resultado del envio de un OTP por un proveedor. */
data class OtpSendResult(
    val delivered: Boolean,
    val provider: String,
    val destinationMasked: String,
)

/** Resultado de un sub-paso biometrico (liveness o face match). */
data class BiometricCheckResult(
    val status: CheckStatus,
    val score: Double,
    val details: String? = null,
)

/** Datos extraidos de la cedula de ciudadania. */
data class ExtractedIdData(
    val documentNumber: String? = null,
    val firstName: String? = null,
    val middleName: String? = null,
    val lastName: String? = null,
    val secondLastName: String? = null,
    val birthDate: LocalDate? = null,
    val gender: String? = null,
    val bloodType: String? = null,
    val expeditionPlace: String? = null,
    val issueDate: LocalDate? = null,
    val rawText: String? = null,
)

/** Datos impresos en el reverso de la cedula, leidos por OCR (no estan en el PDF417). */
data class PrintedIdData(
    val birthDate: LocalDate? = null,
    val birthCity: String? = null,
    val birthDepartment: String? = null,
    val heightCm: Int? = null,
    val bloodType: String? = null,
    val issueDate: LocalDate? = null,
    val issuePlace: String? = null,
)

/** Lee el texto impreso del reverso. Devuelve null si el servicio no esta disponible. */
interface PrintedTextProvider {
    fun extract(backImage: ByteArray): PrintedIdData?
}

/** Resultado del OCR + validacion documental. */
data class OcrResult(
    val data: ExtractedIdData,
    val confidence: Double,
    val documentValid: Boolean,
    val details: String? = null,
)

/** Contrato de envio de codigos OTP (SMS o correo). */
interface OtpProvider {
    val name: String
    fun send(destination: String, channel: ContactChannel, code: String): OtpSendResult
}

/**
 * Contrato de lectura de la cedula: recibe la foto del frente (texto) y la
 * del reverso (codigo PDF417/QR) y devuelve los campos estructurados.
 */
interface DocumentOcrProvider {
    val name: String
    fun extract(frontImage: ByteArray, backImage: ByteArray?, contentType: String?): OcrResult
}

/** Contrato de comparacion facial 1:1 (foto de la cedula vs selfie). */
interface FaceMatchProvider {
    val name: String
    fun compare(referenceImage: ByteArray, selfieImage: ByteArray): BiometricCheckResult
}

/** Contrato de prueba de vida (deteccion de persona real). */
interface LivenessProvider {
    val name: String
    fun verify(selfieImage: ByteArray): BiometricCheckResult
}
