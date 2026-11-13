package com.onboarding.repository

import com.onboarding.domain.AuditEvent
import com.onboarding.domain.BiometricCapture
import com.onboarding.domain.DocumentCapture
import com.onboarding.domain.DocumentSide
import com.onboarding.domain.OnboardingSession
import com.onboarding.domain.OtpChallenge
import com.onboarding.domain.VerificationResult
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OnboardingSessionRepository : JpaRepository<OnboardingSession, UUID> {
    fun findFirstByDocumentNumber(documentNumber: String): OnboardingSession?

    /** Sesion mas reciente de un documento, filtrada por estado (usada por «Perdi mi celular»). */
    fun findFirstByDocumentNumberAndStatusOrderByCreatedAtDesc(
        documentNumber: String,
        status: com.onboarding.domain.OnboardingStatus,
    ): OnboardingSession?
}

interface OtpChallengeRepository : JpaRepository<OtpChallenge, UUID> {
    fun findFirstBySessionIdOrderByCreatedAtDesc(sessionId: UUID): OtpChallenge?
}

interface DocumentCaptureRepository : JpaRepository<DocumentCapture, UUID> {
    fun findBySessionId(sessionId: UUID): List<DocumentCapture>
    fun findBySessionIdAndSide(sessionId: UUID, side: DocumentSide): DocumentCapture?
}

interface BiometricCaptureRepository : JpaRepository<BiometricCapture, UUID> {
    fun findFirstBySessionIdOrderByCreatedAtDesc(sessionId: UUID): BiometricCapture?
}

interface VerificationResultRepository : JpaRepository<VerificationResult, UUID> {
    fun findFirstBySessionIdOrderByCreatedAtDesc(sessionId: UUID): VerificationResult?
}

interface AuditEventRepository : JpaRepository<AuditEvent, UUID> {
    fun findBySessionIdOrderByCreatedAtAsc(sessionId: UUID): List<AuditEvent>
}
