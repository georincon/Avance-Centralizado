package com.onboarding.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Reto OTP enviado al usuario (SMS o correo). El codigo se guarda hasheado. */
@Entity
@Table(name = "otp_challenge")
class OtpChallenge(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var channel: ContactChannel,

    @Column(nullable = false, length = 255)
    var destination: String,

    @Column(name = "code_hash", nullable = false, length = 128)
    var codeHash: String,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "max_attempts", nullable = false)
    var maxAttempts: Int = 5,

    @Column(nullable = false)
    var verified: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,
) {
    @PrePersist
    fun onPrePersist() {
        createdAt = Instant.now()
    }

    fun isExpired(now: Instant = Instant.now()): Boolean = now.isAfter(expiresAt)

    val isLocked: Boolean
        get() = attempts >= maxAttempts
}
