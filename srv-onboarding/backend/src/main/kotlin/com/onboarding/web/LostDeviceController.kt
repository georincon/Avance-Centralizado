package com.onboarding.web

import com.onboarding.common.ApiException
import com.onboarding.service.LostDeviceService
import com.onboarding.web.dto.LostDeviceConfirmRequest
import com.onboarding.web.dto.LostDeviceConfirmResponse
import com.onboarding.web.dto.LostDeviceStartRequest
import com.onboarding.web.dto.LostDeviceStartResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/lost-device")
class LostDeviceController(private val service: LostDeviceService) {

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun start(@RequestBody request: LostDeviceStartRequest, http: HttpServletRequest): LostDeviceStartResponse {
        requireConfigured()
        val result = service.start(request.documentNumber, clientIp(http))
        return LostDeviceStartResponse(
            message = "Si el documento tiene un contacto verificado, enviamos un codigo.",
            devCode = result.devCode,
        )
    }

    @PostMapping("/confirm")
    fun confirm(@RequestBody request: LostDeviceConfirmRequest): LostDeviceConfirmResponse {
        requireConfigured()
        val r = service.confirm(request.documentNumber, request.code)
        val hechos = listOfNotNull(
            if (r.revoked > 0) "revocamos ${r.revoked} credencial(es) asociadas a tu documento" else null,
            if (r.walletRevoked > 0) "revocamos la wallet de tu celular anterior" else null,
        )
        val what = if (hechos.isEmpty()) "No encontramos credenciales ni una wallet activa para revocar." else hechos.joinToString(" y ").replaceFirstChar { it.uppercase() } + "."
        val fallos = r.failed + r.walletFailed
        val extra = if (fallos > 0) " $fallos no se pudieron revocar; contacta a soporte." else ""
        return LostDeviceConfirmResponse(
            revoked = r.revoked,
            message = "$what$extra Cuando tengas tu celular nuevo, instala la wallet y vuelve a pedir tus documentos.",
        )
    }

    private fun requireConfigured() {
        if (!service.configured) {
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Esta funcion no esta disponible por ahora")
        }
    }

    /** Detras de nw-nginx, la ultima entrada de X-Forwarded-For es la que anade el proxy. */
    private fun clientIp(http: HttpServletRequest): String =
        http.getHeader("X-Forwarded-For")?.split(",")?.lastOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: http.remoteAddr
}
