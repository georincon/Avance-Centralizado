package eu.europa.ec.eudi.pidissuer.adapter.input.web

import eu.europa.ec.eudi.pidissuer.port.out.registraduria.FaceMatchResult
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.FaceMatcher
import eu.europa.ec.eudi.pidissuer.port.out.registraduria.SelfieCheck
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.util.MultiValueMap

/** Apoyo para probar `/registraduria/solicitar`: formulario multipart con los datos del titular de prueba y una selfie. */
object SolicitarTestSupport {
    fun face(
        quality: SelfieCheck = SelfieCheck.Ok,
        match: FaceMatchResult = FaceMatchResult.Match(0.9),
    ): FaceMatcher =
        object : FaceMatcher {
            override suspend fun checkSelfie(selfie: ByteArray) = quality

            override suspend fun match(reference: ByteArray, selfie: ByteArray) = match
        }

    /** Datos del `Cedula` de prueba (JOSE GREGORIO / MUNOZ NAVAS / 1973-09-08) más una selfie cualquiera. */
    fun parts(
        documentNumber: String,
        formats: List<String>? = null,
        firstNames: String = "JOSE GREGORIO",
        lastNames: String = "MUNOZ NAVAS",
        birthDate: String = "1973-09-08",
        withSelfie: Boolean = true,
    ): MultiValueMap<String, *> {
        val builder = MultipartBodyBuilder()
        builder.part("documentNumber", documentNumber)
        builder.part("firstNames", firstNames)
        builder.part("lastNames", lastNames)
        builder.part("birthDate", birthDate)
        if (formats != null) {
            builder.part("formatsChosen", "1")
            formats.forEach { builder.part("formats", it) }
        }
        if (withSelfie) {
            builder
                .part(
                    "selfie",
                    object : ByteArrayResource(byteArrayOf(1, 2, 3)) {
                        override fun getFilename() = "selfie.jpg"
                    },
                ).contentType(MediaType.IMAGE_JPEG)
        }
        return builder.build()
    }
}
