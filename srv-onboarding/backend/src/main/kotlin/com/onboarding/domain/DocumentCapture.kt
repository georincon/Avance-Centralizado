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

/** Foto de una cara de la cedula (frente o reverso) con el resultado del OCR. */
@Entity
@Table(name = "document_capture")
class DocumentCapture(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "session_id", nullable = false)
    var sessionId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var side: DocumentSide,

    @Column(name = "storage_key", nullable = false, length = 500)
    var storageKey: String,

    @Column(name = "original_filename", length = 255)
    var originalFilename: String? = null,

    @Column(name = "content_type", length = 100)
    var contentType: String? = null,

    @Column(name = "size_bytes")
    var sizeBytes: Long? = null,

    @Column(length = 64)
    var sha256: String? = null,

    @Column(name = "ocr_confidence")
    var ocrConfidence: Double? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", length = 40)
    var validationStatus: CaptureValidationStatus = CaptureValidationStatus.PENDING,

    @Column(name = "ocr_data")
    var ocrData: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    @PrePersist
    fun onPrePersist() {
        createdAt = Instant.now()
    }
}
