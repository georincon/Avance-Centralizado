package com.onboarding.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Bitacora de eventos del onboarding (trazabilidad / auditoria). */
@Entity
@Table(name = "audit_event")
class AuditEvent(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "session_id")
    var sessionId: UUID? = null,

    @Column(nullable = false, length = 60)
    var type: String,

    @Column(length = 500)
    var message: String? = null,

    @Column
    var metadata: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    @PrePersist
    fun onPrePersist() {
        createdAt = Instant.now()
    }
}
