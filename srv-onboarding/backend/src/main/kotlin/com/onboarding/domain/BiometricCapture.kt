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

/** Selfie/video del usuario con el resultado de liveness y comparacion facial. */
@Entity
@Table(name = "biometric_capture")
class BiometricCapture(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var kind: BiometricKind = BiometricKind.SELFIE,

    @Column(name = "storage_key", nullable = false, length = 500)
    var storageKey: String,

    @Column(name = "content_type", length = 100)
    var contentType: String? = null,

    @Column(name = "size_bytes")
    var sizeBytes: Long? = null,

    @Column(length = 40)
    var provider: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "liveness_status", length = 40)
    var livenessStatus: CheckStatus? = null,

    @Column(name = "liveness_score")
    var livenessScore: Double? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "face_match_status", length = 40)
    var faceMatchStatus: CheckStatus? = null,

    @Column(name = "face_match_score")
    var faceMatchScore: Double? = null,

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
