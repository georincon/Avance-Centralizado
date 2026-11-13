package com.onboarding.domain

/** Estado global del flujo de onboarding. Progresion lineal con finales de decision. */
enum class OnboardingStatus {
    CREATED,
    CONTACT_PENDING,
    CONTACT_VERIFIED,
    CONSENT_ACCEPTED,
    DOCUMENTS_PENDING,
    DOCUMENTS_CAPTURED,
    BIOMETRIC_PENDING,
    BIOMETRIC_CAPTURED,
    APPROVED,
    REJECTED,
    MANUAL_REVIEW,
    EXPIRED;

    val isFinal: Boolean
        get() = this == APPROVED || this == REJECTED || this == MANUAL_REVIEW || this == EXPIRED
}

/** Resultado de la verificacion integral (documento + biometria). */
enum class VerificationDecision { APPROVED, REJECTED, MANUAL_REVIEW }

/** Canal por el que se envia el OTP. */
enum class ContactChannel { SMS, EMAIL }

/** Cara del documento de identidad. */
enum class DocumentSide { FRONT, BACK }

/** Estado de validacion de una captura documental. */
enum class CaptureValidationStatus { PENDING, VALID, INVALID, ERROR }

/** Tipo de captura biometrica. */
enum class BiometricKind { SELFIE, VIDEO }

/** Resultado de un sub-paso biometrico (liveness / face match). */
enum class CheckStatus { SUCCESS, FAILURE, PENDING }
