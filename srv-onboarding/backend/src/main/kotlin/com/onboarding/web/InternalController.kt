package com.onboarding.web

import com.onboarding.common.ApiException
import com.onboarding.common.NotFoundException
import com.onboarding.service.OnboardingService
import com.onboarding.service.VerificationService
import com.onboarding.storage.FileStorage
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest
import java.util.UUID

data class CompareSelfieResponse(val status: String, val score: Double, val details: String?)

/**
 * Endpoints server-to-server (los llama el issuer de la Registraduria con X-Api-Key). La selfie del onboarding NUNCA sale
 * de este servicio: es el issuer quien manda la foto de referencia del registro y aqui se compara con la selfie guardada.
 * Sin `onboarding.internal.api-key` configurada responden 503.
 */
@RestController
@RequestMapping("/api/v1/internal")
class InternalController(
    private val service: OnboardingService,
    private val verification: VerificationService,
    private val fileStorage: FileStorage,
    @Value("\${onboarding.internal.api-key:}") private val apiKey: String,
) {
    @PostMapping("/sessions/{id}/compare-selfie", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun compareSelfie(
        @PathVariable id: UUID,
        @RequestHeader(name = "X-Api-Key", required = false) provided: String?,
        @RequestPart("reference") reference: MultipartFile,
    ): CompareSelfieResponse {
        if (apiKey.isBlank()) throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No disponible")
        val keyOk = MessageDigest.isEqual((provided ?: "").toByteArray(), apiKey.toByteArray())
        if (!keyOk) throw ApiException(HttpStatus.UNAUTHORIZED, "Clave invalida")
        val capture = service.getBiometric(id) ?: throw NotFoundException("La sesion no tiene selfie")
        val selfie = fileStorage.load(capture.storageKey) ?: throw NotFoundException("No se pudo recuperar la selfie")
        val result = verification.runFaceMatch(reference.bytes, selfie)
        return CompareSelfieResponse(result.status.name, result.score, result.details)
    }
}
