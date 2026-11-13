package com.onboarding.web.dto

import com.onboarding.domain.ContactChannel
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateSessionResponse(
    val id: UUID,
    val status: String,
    val expiresAt: Instant,
)

data class StartContactRequest(
    val channel: ContactChannel,
    val phoneNumber: String? = null,
    val email: String? = null,
)

data class StartContactResponse(
    val sessionId: UUID,
    val status: String,
    val channel: String,
    val destinationMasked: String,
    val provider: String,
    val devCode: String? = null,
)

data class VerifyContactRequest(val code: String)

data class ConsentRequest(
    val accepted: Boolean,
    val policyVersion: String? = null,
)

data class CapturedDocumentDto(
    val side: String,
    val validationStatus: String,
    val ocrConfidence: Double?,
    val contentType: String?,
)

data class BiometricSummaryDto(
    val status: String,
    val livenessStatus: String?,
    val livenessScore: Double?,
    val faceMatchStatus: String?,
    val faceMatchScore: Double?,
    val provider: String?,
)

data class ExtractedDataDto(
    val documentNumber: String?,
    val firstName: String?,
    val middleName: String?,
    val lastName: String?,
    val secondLastName: String?,
    val birthDate: LocalDate?,
    val gender: String?,
    val bloodType: String?,
    val birthCity: String?,
    val birthDepartment: String?,
    val heightCm: Int?,
    val expeditionPlace: String?,
    val issueDate: LocalDate?,
)

data class DeclaredDataRequest(
    val documentNumber: String,
    val firstNames: String,
    val lastNames: String,
    val birthDate: LocalDate,
)

data class DataMatchDto(
    val documentNumberMatch: Boolean,
    val birthDateMatch: Boolean,
    val firstNamesSimilarity: Double,
    val lastNamesSimilarity: Double,
    val namesMatch: Boolean,
)

data class SessionStatusResponse(
    val id: UUID,
    val status: String,
    val decision: String?,
    val contactVerified: Boolean,
    val consentAccepted: Boolean,
    val failureReason: String?,
    val data: ExtractedDataDto,
    val dataMatch: DataMatchDto?,
    val documents: List<CapturedDocumentDto>,
    val biometric: BiometricSummaryDto?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant,
)

data class UploadDocumentResponse(
    val sessionId: UUID,
    val side: String,
    val validationStatus: String,
    val ocrConfidence: Double?,
    val sessionStatus: String,
)

data class SelfieResponse(
    val sessionId: UUID,
    val sessionStatus: String,
    val decision: String?,
    val livenessStatus: String?,
    val faceMatchStatus: String?,
)

data class AuditEventResponse(
    val type: String,
    val message: String?,
    val createdAt: Instant,
)

// ---- «Perdi mi celular» ----
data class LostDeviceStartRequest(val documentNumber: String)

data class LostDeviceStartResponse(val message: String, val devCode: String? = null)

data class LostDeviceConfirmRequest(val documentNumber: String, val code: String)

data class LostDeviceConfirmResponse(val revoked: Int, val message: String)
