package com.onboarding.web

import com.onboarding.common.UnauthorizedException
import com.onboarding.service.LostDeviceConfirmResult
import com.onboarding.service.LostDeviceService
import com.onboarding.service.FakeAudits
import com.onboarding.service.FakeIssuer
import com.onboarding.service.FakeOtp
import com.onboarding.service.FakeSessions
import com.onboarding.service.approvedSession
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

class LostDeviceControllerTest {
    private val issuer = FakeIssuer()
    private val otp = FakeOtp()
    private val service = LostDeviceService(FakeSessions(approvedSession()).repo, FakeAudits().repo, otp, issuer, true, 3, 10)
    private val mvc = MockMvcBuilders.standaloneSetup(LostDeviceController(service))
        .setControllerAdvice(GlobalExceptionHandler()).build()

    private fun json(body: String) = post("/api/v1/lost-device/" + body.substringBefore('|'))
        .contentType(MediaType.APPLICATION_JSON).content(body.substringAfter('|'))

    @Test
    fun `start responde 202 con mensaje generico`() {
        mvc.perform(json("start|{\"documentNumber\":\"1000000001\"}"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.message").value("Si el documento tiene un contacto verificado, enviamos un codigo."))
            .andExpect(jsonPath("$.devCode").value("654321"))
    }

    @Test
    fun `sin configurar responde 503`() {
        val off = LostDeviceService(FakeSessions().repo, FakeAudits().repo, otp, FakeIssuer(false), true, 3, 10)
        val mvc = MockMvcBuilders.standaloneSetup(LostDeviceController(off)).setControllerAdvice(GlobalExceptionHandler()).build()
        mvc.perform(json("start|{\"documentNumber\":\"1000000001\"}")).andExpect(status().isServiceUnavailable)
        mvc.perform(json("confirm|{\"documentNumber\":\"1000000001\",\"code\":\"1\"}")).andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `confirm devuelve revocadas y mensaje claro`() {
        issuer.result = LostDeviceConfirmResult(3, 0, 0, true)
        mvc.perform(json("confirm|{\"documentNumber\":\"1000000001\",\"code\":\"123456\"}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.revoked").value(3))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("celular nuevo")))
    }

    @Test
    fun `confirm informa de la wallet revocada aunque no haya credenciales con lista de estado`() {
        issuer.result = LostDeviceConfirmResult(0, 0, 1, false, walletRevoked = 2)
        mvc.perform(json("confirm|{\"documentNumber\":\"1000000001\",\"code\":\"123456\"}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.revoked").value(0))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("la wallet de tu celular anterior")))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("No encontramos"))))
    }

    @Test
    fun `confirm sin nada que revocar lo dice`() {
        issuer.result = LostDeviceConfirmResult(0, 0, 0, false)
        mvc.perform(json("confirm|{\"documentNumber\":\"1000000001\",\"code\":\"123456\"}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("No encontramos")))
    }

    @Test
    fun `confirm con codigo invalido responde 401 generico`() {
        otp.verifyError = UnauthorizedException("x")
        mvc.perform(json("confirm|{\"documentNumber\":\"1000000001\",\"code\":\"0\"}"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Codigo invalido o vencido"))
    }
}
