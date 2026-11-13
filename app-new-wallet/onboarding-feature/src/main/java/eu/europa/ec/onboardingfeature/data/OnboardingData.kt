package eu.europa.ec.onboardingfeature.data

data class OnboardingData(
    val phone: String = "",
    val phoneOtpGenerated: String = "",
    val phoneVerified: Boolean = false,
    val cedula: String = "",
    val nombres: String = "",
    val apellidos: String = "",
    val birthDate: String = "", // DD-MM-AAAA
    val email: String = "",
    val emailOtpGenerated: String = "",
    val emailVerified: Boolean = false,
    val consentAccepted: Boolean = false,
    val frontImagePath: String? = null,
    val backImagePath: String? = null,
    val selfieImagePath: String? = null
)
