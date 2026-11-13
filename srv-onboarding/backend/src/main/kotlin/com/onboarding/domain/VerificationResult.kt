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

/** Resultado final e inmutable de la verificacion de una sesion. */
@Entity
@Table(name = "verification_result")
class VerificationResult(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    var decision: VerificationDecision,

    @Column(length = 500)
    var reason: String? = null,

    @Column
    var details: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    @PrePersist
    fun onPrePersist() {
        createdAt = Instant.now()
    }
}
