package com.onboarding.provider.impl

import com.onboarding.provider.PrintedIdData
import com.onboarding.provider.PrintedTextProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.time.LocalDate

/** Respuesta del endpoint /api/ocr/id-back del servicio de vision. */
data class PrintedOcrResponse(
    val ok: Boolean = false,
    val birthDate: String? = null,
    val birthCity: String? = null,
    val birthDepartment: String? = null,
    val heightCm: Int? = null,
    val bloodType: String? = null,
    val issueDate: String? = null,
    val issuePlace: String? = null,
)

/**
 * OCR del reverso via el servicio de vision self-hosted (RapidOCR). Es complementario:
 * si falla, el onboarding continua con los datos del PDF417.
 */
@Component
class HttpPrintedTextProvider(
    @Value("\${onboarding.biometric.http-base-url}") private val baseUrl: String,
) : PrintedTextProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client: RestClient = RestClient.create()

    override fun extract(backImage: ByteArray): PrintedIdData? = runCatching {
        val body = LinkedMultiValueMap<String, Any>().apply {
            add("image", object : ByteArrayResource(backImage) {
                override fun getFilename() = "back.jpg"
            })
        }
        val r = client.post().uri("$baseUrl/api/ocr/id-back")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .body(PrintedOcrResponse::class.java)
        if (r == null || !r.ok) {
            null
        } else {
            PrintedIdData(
                birthDate = r.birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                birthCity = r.birthCity,
                birthDepartment = r.birthDepartment,
                heightCm = r.heightCm,
                bloodType = r.bloodType,
                issueDate = r.issueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                issuePlace = r.issuePlace,
            )
        }
    }.onFailure { log.warn("OCR del reverso no disponible: {}", it.message) }.getOrNull()
}
