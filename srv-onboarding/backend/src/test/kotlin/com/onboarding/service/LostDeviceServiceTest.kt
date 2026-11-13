package com.onboarding.service

import com.onboarding.common.ApiException
import com.onboarding.common.BadRequestException
import com.onboarding.common.ConflictException
import com.onboarding.common.UnauthorizedException
import com.onboarding.domain.ContactChannel
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LostDeviceServiceTest {
    private val approved = approvedSession()
    private val sessions = FakeSessions(approved)
    private val audits = FakeAudits()
    private val otp = FakeOtp()
    private val issuer = FakeIssuer()

    private fun service(expose: Boolean = true, perDoc: Int = 3, perIp: Int = 10) =
        LostDeviceService(sessions.repo, audits.repo, otp, issuer, expose, perDoc, perIp)

    @Test
    fun `start emite OTP al contacto verificado y devuelve devCode en desarrollo`() {
        assertEquals("654321", service().start("1000000001", "1.1.1.1").devCode)
        assertEquals(Triple(approved.id, "persona.prueba@example.org", ContactChannel.EMAIL), otp.issued.single())
    }

    @Test
    fun `start no devuelve devCode fuera de desarrollo`() {
        assertNull(service(expose = false).start("1000000001", "1.1.1.1").devCode)
        assertEquals(1, otp.issued.size)
    }

    @Test
    fun `start con cedula desconocida no emite nada y no falla`() {
        assertNull(service().start("1000000002", "1.1.1.1").devCode)
        assertTrue(otp.issued.isEmpty())
    }

    @Test
    fun `start ignora sesiones con contacto no verificado`() {
        approved.contactVerified = false
        service().start("1000000001", "1.1.1.1")
        assertTrue(otp.issued.isEmpty())
    }

    @Test
    fun `start rechaza documento mal formado`() {
        assertFailsWith<BadRequestException> { service().start("12", "1.1.1.1") }
    }

    @Test
    fun `start limita 3 por documento y cuenta tambien documentos inexistentes`() {
        val s = service()
        repeat(3) { s.start("1000000002", "1.1.1.$it") }
        val e = assertFailsWith<ApiException> { s.start("1000000002", "9.9.9.9") }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.status)
    }

    @Test
    fun `start limita por IP`() {
        val s = service(perIp = 2)
        s.start("1000000003", "1.1.1.1"); s.start("1000000004", "1.1.1.1")
        assertFailsWith<ApiException> { s.start("1000000005", "1.1.1.1") }
        s.start("1000000005", "2.2.2.2")
    }

    @Test
    fun `confirm con OTP valido llama al emisor`() {
        val r = service().confirm("1000000001", " 123456 ")
        assertEquals(listOf("123456"), otp.verified)
        assertEquals(listOf("1000000001"), issuer.calls)
        assertEquals(2, r.revoked)
        assertTrue(r.blocked)
    }

    @Test
    fun `confirm devuelve el mismo error generico para cualquier fallo de OTP`() {
        for (c in listOf(ConflictException("vencido"), UnauthorizedException("incorrecto"))) {
            otp.verifyError = c
            val e = assertFailsWith<UnauthorizedException> { service().confirm("1000000001", "000000") }
            assertEquals("Codigo invalido o vencido", e.message)
        }
        otp.verifyError = null
        val e = assertFailsWith<UnauthorizedException> { service().confirm("1000000009", "000000") }
        assertEquals("Codigo invalido o vencido", e.message)
        assertTrue(issuer.calls.isEmpty())
    }

    @Test
    fun `confirm responde 502 si el emisor falla`() {
        issuer.error = IllegalStateException("boom")
        val e = assertFailsWith<ApiException> { service().confirm("1000000001", "123456") }
        assertEquals(HttpStatus.BAD_GATEWAY, e.status)
    }

    @Test
    fun `la auditoria no guarda codigo, contacto ni cedula completos`() {
        val s = service()
        s.start("1000000001", "1.1.1.1"); s.confirm("1000000001", "654321")
        assertTrue(audits.saved.size >= 2)
        for (ev in audits.saved) {
            val m = ev.message ?: ""
            assertFalse(m.contains("654321") || m.contains("persona.prueba") || m.contains("1000000001"), m)
        }
    }
}
