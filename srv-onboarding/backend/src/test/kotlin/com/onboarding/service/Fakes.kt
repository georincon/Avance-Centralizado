package com.onboarding.service

import com.onboarding.domain.AuditEvent
import com.onboarding.domain.ContactChannel
import com.onboarding.domain.OnboardingSession
import com.onboarding.domain.OnboardingStatus
import com.onboarding.domain.OtpChallenge
import com.onboarding.provider.OtpProvider
import com.onboarding.provider.OtpSendResult
import com.onboarding.repository.AuditEventRepository
import com.onboarding.repository.OnboardingSessionRepository
import com.onboarding.repository.OtpChallengeRepository
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.UUID

/** Dobles de prueba escritos a mano (sin Mockito) para el servicio «Perdi mi celular». */
class FakeSessions(var session: OnboardingSession? = null) {
    val repo: OnboardingSessionRepository = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(OnboardingSessionRepository::class.java),
    ) { _, m, args ->
        if (m.name == "findFirstByDocumentNumberAndStatusOrderByCreatedAtDesc") {
            session?.takeIf { it.documentNumber == args[0] && it.status == args[1] }
        } else error("no soportado: ${m.name}")
    } as OnboardingSessionRepository
}

class FakeAudits {
    val saved = mutableListOf<AuditEvent>()
    val repo: AuditEventRepository = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(AuditEventRepository::class.java),
    ) { _, m, args -> if (m.name == "save") { saved += args[0] as AuditEvent; args[0] } else error("no soportado: ${m.name}") } as AuditEventRepository
}

class FakeOtp : OtpService(
    Proxy.newProxyInstance(FakeOtp::class.java.classLoader, arrayOf(OtpChallengeRepository::class.java)) { _, _, _ -> null } as OtpChallengeRepository,
    object : OtpProvider {
        override val name = "fake"
        override fun send(destination: String, channel: ContactChannel, code: String) = OtpSendResult(true, "fake", "****")
    },
    6, 5, 5, "pepper",
) {
    val issued = mutableListOf<Triple<UUID, String, ContactChannel>>()
    var verifyError: RuntimeException? = null
    var verified = mutableListOf<String>()

    override fun issue(sessionId: UUID, destination: String, channel: ContactChannel): OtpIssueResult {
        issued += Triple(sessionId, destination, channel)
        return OtpIssueResult(
            OtpChallenge(sessionId = sessionId, channel = channel, destination = destination, codeHash = "h", expiresAt = Instant.now()),
            OtpSendResult(true, "fake", "****************.org"), "654321",
        )
    }

    override fun verify(sessionId: UUID, code: String) {
        verified += code
        verifyError?.let { throw it }
    }
}

class FakeIssuer(override val configured: Boolean = true) : IssuerRevocationClient {
    var result = LostDeviceConfirmResult(2, 0, 0, true)
    var error: Exception? = null
    val calls = mutableListOf<String>()
    override fun revoke(subject: String): LostDeviceConfirmResult {
        calls += subject
        error?.let { throw it }
        return result
    }
}

fun approvedSession() = OnboardingSession(
    status = OnboardingStatus.APPROVED, documentNumber = "1000000001", contactVerified = true,
    contactChannel = ContactChannel.EMAIL, email = "persona.prueba@example.org",
    expiresAt = Instant.now().plusSeconds(60),
)
