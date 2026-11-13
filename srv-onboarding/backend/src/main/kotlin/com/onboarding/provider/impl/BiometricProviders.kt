package com.onboarding.provider.impl

import com.onboarding.domain.CheckStatus
import com.onboarding.provider.BiometricCheckResult
import com.onboarding.provider.FaceMatchProvider
import com.onboarding.provider.LivenessProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

/** Respuesta estandar esperada de un motor biometrico self-hosted. */
data class BiometricResponse(
    val status: String,
    val score: Double,
    val details: String? = null,
)

private fun BiometricResponse.toResult(): BiometricCheckResult = BiometricCheckResult(
    status = if (status.equals("success", ignoreCase = true)) CheckStatus.SUCCESS else CheckStatus.FAILURE,
    score = score,
    details = details,
)

/**
 * Proveedor que delega en un microservicio de biometria self-hosted (por
 * OpenCV YuNet + SFace). Ver carpeta /biometrics.
 */
@Component
@ConditionalOnProperty(name = ["onboarding.biometric.provider"], havingValue = "http", matchIfMissing = true)
class HttpBiometricProvider(
    @Value("\${onboarding.biometric.http-base-url}") private val baseUrl: String,
    @Value("\${onboarding.biometric.face-match-threshold}") private val faceMatchThreshold: Double,
    @Value("\${onboarding.biometric.liveness-threshold}") private val livenessThreshold: Double,
) : FaceMatchProvider, LivenessProvider {
    override val name = "http"
    private val client: RestClient = RestClient.create()

    override fun compare(referenceImage: ByteArray, selfieImage: ByteArray): BiometricCheckResult {
        val body = LinkedMultiValueMap<String, Any>().apply {
            add("reference", object : ByteArrayResource(referenceImage) {
                override fun getFilename() = "reference.jpg"
            })
            add("selfie", object : ByteArrayResource(selfieImage) {
                override fun getFilename() = "selfie.jpg"
            })
        }
        val response = client.post()
            .uri("$baseUrl/api/verify/face-match")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .body(BiometricResponse::class.java)
            ?: error("respuesta vacia del motor biometrico")
        val result = response.toResult()
        return if (result.status == CheckStatus.SUCCESS && result.score < faceMatchThreshold) {
            BiometricCheckResult(CheckStatus.FAILURE, result.score, "Score por debajo del umbral $faceMatchThreshold")
        } else {
            result
        }
    }

    override fun verify(selfieImage: ByteArray): BiometricCheckResult {
        val body = LinkedMultiValueMap<String, Any>().apply {
            add("selfie", object : ByteArrayResource(selfieImage) {
                override fun getFilename() = "selfie.jpg"
            })
        }
        val response = client.post()
            .uri("$baseUrl/api/verify/liveness")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .body(BiometricResponse::class.java)
            ?: error("respuesta vacia del motor biometrico")
        val result = response.toResult()
        return if (result.status == CheckStatus.SUCCESS && result.score < livenessThreshold) {
            BiometricCheckResult(CheckStatus.FAILURE, result.score, "Score por debajo del umbral $livenessThreshold")
        } else {
            result
        }
    }
}
