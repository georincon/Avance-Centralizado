package com.onboarding.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Agregado raiz del onboarding. Contiene el estado del flujo y los datos
 * personales extraidos del documento de identidad.
 */
@Entity
@Table(name = "onboarding_session")
class OnboardingSession(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    var status: OnboardingStatus = OnboardingStatus.CREATED,

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    var decision: VerificationDecision? = null,

    @Column(name = "phone_number", length = 20)
    var phoneNumber: String? = null,

    @Column(length = 255)
    var email: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_channel", length = 20)
    var contactChannel: ContactChannel? = null,

    @Column(name = "contact_verified", nullable = false)
    var contactVerified: Boolean = false,

    @Column(name = "consent_accepted", nullable = false)
    var consentAccepted: Boolean = false,

    @Column(name = "consent_accepted_at")
    var consentAcceptedAt: Instant? = null,

    @Column(name = "document_type", nullable = false, length = 40)
    var documentType: String = "national-id",

    @Column(name = "document_number", length = 30)
    var documentNumber: String? = null,

    @Column(name = "first_name", length = 80)
    var firstName: String? = null,

    @Column(name = "middle_name", length = 80)
    var middleName: String? = null,

    @Column(name = "last_name", length = 80)
    var lastName: String? = null,

    @Column(name = "second_last_name", length = 80)
    var secondLastName: String? = null,

    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,

    @Column(length = 10)
    var gender: String? = null,

    @Column(name = "blood_type", length = 5)
    var bloodType: String? = null,

    @Column(name = "expedition_place", length = 120)
    var expeditionPlace: String? = null,

    @Column(name = "issue_date")
    var issueDate: LocalDate? = null,

    @Column(name = "birth_city", length = 80)
    var birthCity: String? = null,

    @Column(name = "birth_department", length = 80)
    var birthDepartment: String? = null,

    @Column(name = "height_cm")
    var heightCm: Int? = null,

    @Column(name = "declared_document_number", length = 30)
    var declaredDocumentNumber: String? = null,

    @Column(name = "declared_first_names", length = 160)
    var declaredFirstNames: String? = null,

    @Column(name = "declared_last_names", length = 160)
    var declaredLastNames: String? = null,

    @Column(name = "declared_birth_date")
    var declaredBirthDate: LocalDate? = null,

    @Column(name = "failure_reason", length = 255)
    var failureReason: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.now(),

    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    @PrePersist
    fun onPrePersist() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onPreUpdate() {
        updatedAt = Instant.now()
    }

    fun isExpired(now: Instant = Instant.now()): Boolean = now.isAfter(expiresAt)
}
