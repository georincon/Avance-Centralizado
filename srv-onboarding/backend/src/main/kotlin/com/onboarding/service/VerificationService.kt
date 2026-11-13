package com.onboarding.service

import com.onboarding.domain.CheckStatus
import com.onboarding.domain.VerificationDecision
import com.onboarding.provider.BiometricCheckResult
import com.onboarding.provider.DocumentOcrProvider
import com.onboarding.provider.FaceMatchProvider
import com.onboarding.provider.LivenessProvider
import com.onboarding.provider.OcrResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

data class DecisionResult(
    val decision: VerificationDecision,
    val reason: String,
)

/**
 * Orquesta las validaciones: OCR del documento, prueba de vida y comparacion
 * facial. Concentra la logica de decision para que sea facil de auditar.
 */
@Service
class VerificationService(
    private val ocrProvider: DocumentOcrProvider,
    private val faceMatchProvider: FaceMatchProvider,
    private val livenessProvider: LivenessProvider,
    private val printedTextProvider: com.onboarding.provider.PrintedTextProvider,
    @Value("\${onboarding.biometric.face-match-review-below}") private val faceReviewBelow: Double,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Nombre del proveedor biometrico en uso, para trazabilidad. */
    val biometricProviderName: String
        get() = listOf(livenessProvider.name, faceMatchProvider.name).distinct().joinToString("+")

    fun runOcr(frontImage: ByteArray, backImage: ByteArray?, contentType: String?): OcrResult =
        ocrProvider.extract(frontImage, backImage, contentType)

    fun runPrintedText(backImage: ByteArray) = printedTextProvider.extract(backImage)

    fun runLiveness(selfieImage: ByteArray): BiometricCheckResult =
        livenessProvider.verify(selfieImage)

    fun runFaceMatch(referenceImage: ByteArray, selfieImage: ByteArray): BiometricCheckResult =
        faceMatchProvider.compare(referenceImage, selfieImage)

    /**
     * Regla de decision. Se rechaza ante evidencia clara de inconsistencia (documento
     * ilegible, numero o fecha distintos, rostro distinto) y se envia a revision
     * manual cuando hay dudas (nombres parecidos, rostro en zona gris, OCR debil).
     */
    fun decide(
        documentValid: Boolean,
        ocrConfidence: Double,
        liveness: BiometricCheckResult,
        faceMatch: BiometricCheckResult,
        dataMatch: DataMatchResult?,
    ): DecisionResult {
        val decision = when {
            !documentValid -> DecisionResult(
                VerificationDecision.REJECTED,
                "No se pudo leer ni validar el documento",
            )
            dataMatch == null -> DecisionResult(
                VerificationDecision.REJECTED,
                "Faltan los datos declarados para compararlos con el documento",
            )
            !dataMatch.documentNumberMatch -> DecisionResult(
                VerificationDecision.REJECTED,
                "El numero de cedula escrito no coincide con el del documento",
            )
            !dataMatch.birthDateMatch -> DecisionResult(
                VerificationDecision.REJECTED,
                "La fecha de nacimiento escrita no coincide con la del documento",
            )
            liveness.status != CheckStatus.SUCCESS -> DecisionResult(
                VerificationDecision.REJECTED,
                "La selfie no cumple los requisitos de calidad: ${liveness.details ?: "sin detalle"}",
            )
            faceMatch.status != CheckStatus.SUCCESS -> DecisionResult(
                VerificationDecision.REJECTED,
                "El rostro de la selfie no coincide con la foto del documento",
            )
            !dataMatch.namesMatch -> DecisionResult(
                VerificationDecision.MANUAL_REVIEW,
                "Los nombres escritos difieren de los del documento, requiere revision manual",
            )
            faceMatch.score < faceReviewBelow -> DecisionResult(
                VerificationDecision.MANUAL_REVIEW,
                "La coincidencia facial es baja (${"%.2f".format(faceMatch.score)}), requiere revision manual",
            )
            ocrConfidence < 0.50 -> DecisionResult(
                VerificationDecision.MANUAL_REVIEW,
                "Baja confianza en la lectura del documento, requiere revision manual",
            )
            else -> DecisionResult(
                VerificationDecision.APPROVED,
                "Datos coherentes con el documento y rostro coincidente",
            )
        }
        log.info(
            "Decision={} motivo='{}' (liveness={}, faceMatch={}/{}, ocr={}, datos={})",
            decision.decision, decision.reason, liveness.status, faceMatch.status, faceMatch.score, ocrConfidence, dataMatch,
        )
        return decision
    }
}
