package eu.europa.ec.onboardingfeature.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import eu.europa.ec.onboardingfeature.ui.OnboardingState
import eu.europa.ec.onboardingfeature.ui.screens.CedulaScreen
import eu.europa.ec.onboardingfeature.ui.screens.CameraCaptureScreen
import eu.europa.ec.onboardingfeature.ui.screens.CaptureMode
import eu.europa.ec.onboardingfeature.ui.screens.ConsentScreen
import eu.europa.ec.onboardingfeature.ui.screens.EmailScreen
import eu.europa.ec.onboardingfeature.ui.screens.EmailOtpScreen
import eu.europa.ec.onboardingfeature.ui.screens.NamesScreen
import eu.europa.ec.onboardingfeature.ui.screens.OtpScreen
import eu.europa.ec.onboardingfeature.ui.screens.PhoneScreen
import eu.europa.ec.onboardingfeature.ui.screens.ProcessingScreen
import eu.europa.ec.onboardingfeature.ui.screens.ResultScreen
import eu.europa.ec.onboardingfeature.ui.screens.SimpleStep
import eu.europa.ec.onboardingfeature.ui.screens.SummaryScreen

enum class Step(
    val route: String,
    val title: String,
    val subtitle: String?,
    val button: String?
) {
    Phone(
        route = "phone",
        title = "Ingresa tu número de celular",
        subtitle = "Te enviaremos un código de confirmación. Asegúrate de tener acceso a este número celular.",
        button = "Enviar código"
    ),
    Otp(
        route = "otp",
        title = "Ingresa tu código",
        subtitle = "Ya te enviamos tu código de confirmación a tu celular.",
        button = "Continuar"
    ),
    ProfileIntro(
        route = "profile_intro",
        title = "Tu perfil es único",
        subtitle = "A continuación te pediremos tu información básica. Esto nos ayudará a conocerte mejor.",
        button = "Continuar"
    ),
    Cedula(
        route = "cedula",
        title = "Ingresa tu cédula",
        subtitle = "Escribe y confirma tu número de cédula de ciudadanía colombiana.",
        button = "Continuar"
    ),
    Names(
        route = "names",
        title = "Cuéntanos sobre ti",
        subtitle = "Ingresa tus datos (no tiene que ser exactamente igual a lo que aparece en la cédula).",
        button = "Continuar"
    ),
    Email(
        route = "email",
        title = "Ingresa tu correo electrónico",
        subtitle = "Te enviaremos un código de confirmación. Asegúrate de tener acceso a este correo.",
        button = "Continuar"
    ),
    EmailOtp(
        route = "email_otp",
        title = "Ingresa tu código",
        subtitle = "Ya te enviamos tu código de confirmación a tu correo electrónico.",
        button = "Continuar"
    ),
    Summary(
        route = "summary",
        title = "Antes de crear tu usuario",
        subtitle = "Revisa que tus datos estén correctos.",
        button = "Continuar"
    ),
    Consent(
        route = "consent",
        title = "Autoriza el tratamiento de tus datos",
        subtitle = "Para validar tu identidad necesitamos tu autorización para el tratamiento de tus datos personales, conforme a la Ley 1581 de 2012.",
        button = "Autorizar y continuar"
    ),
    DocumentFront(
        route = "doc_front",
        title = "Saca una foto a la parte del documento donde sale tu foto",
        subtitle = "Documentos aceptados: documento de identidad. Ten el documento a la mano y toca el botón cuando estés listo.",
        button = "Tomar foto"
    ),
    DocumentFrontCamera(
        route = "doc_front_camera",
        title = "",
        subtitle = null,
        button = null
    ),
    DocumentBack(
        route = "doc_back",
        title = "Dale la vuelta a tu documento de identidad",
        subtitle = "Asegúrate de que tu documento sea totalmente visible y legible. Toca el botón cuando estés listo.",
        button = "Tomar foto"
    ),
    DocumentBackCamera(
        route = "doc_back_camera",
        title = "",
        subtitle = null,
        button = null
    ),
    Selfie(
        route = "selfie",
        title = "Hazte un selfie",
        subtitle = "Coloca la cara en el centro del óvalo y no te muevas. Toca el botón cuando estés listo.",
        button = "Tomar selfie"
    ),
    SelfieCamera(
        route = "selfie_camera",
        title = "",
        subtitle = null,
        button = null
    ),
    Processing(
        route = "processing",
        title = "Ya casi está",
        subtitle = "Danos un segundo para hacer nuestra magia y poder continuar.",
        button = "Continuar"
    ),
    Result(
        route = "result",
        title = "Resultado de la validación",
        subtitle = "Aquí verás el resultado de tu proceso.",
        button = null
    )
}

@Composable
fun OnboardingNavHost(
    state: OnboardingState,
    onFinished: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = state.resumeStep().route
    ) {
        Step.entries.forEachIndexed { index, step ->
            composable(step.route) {
                val canGoBack = navController.previousBackStackEntry != null
                val onBack: () -> Unit = { navController.popBackStack() }
                val onNext: () -> Unit = {
                    Step.entries.getOrNull(index + 1)?.let { next ->
                        navController.navigate(next.route)
                    }
                }
                when (step) {
                    Step.Phone -> PhoneScreen(state, canGoBack, onBack, onNext)
                    Step.Otp -> OtpScreen(state, canGoBack, onBack, onNext)
                    Step.Cedula -> CedulaScreen(state, canGoBack, onBack, onNext)
                    Step.Names -> NamesScreen(state, canGoBack, onBack, onNext)
                    Step.Email -> EmailScreen(state, canGoBack, onBack, onNext)
                    Step.EmailOtp -> EmailOtpScreen(state, canGoBack, onBack, onNext)
                    Step.Summary -> SummaryScreen(state, canGoBack, onBack, onNext)
                    Step.Consent -> ConsentScreen(state, canGoBack, onBack, onNext)
                    Step.DocumentFront -> SimpleStep(step, canGoBack, onBack, onNext)
                    Step.DocumentFrontCamera -> CameraCaptureScreen(state, CaptureMode.DOC_FRONT, canGoBack, onBack, onNext)
                    Step.DocumentBack -> SimpleStep(step, canGoBack, onBack, onNext)
                    Step.DocumentBackCamera -> CameraCaptureScreen(state, CaptureMode.DOC_BACK, canGoBack, onBack, onNext)
                    Step.Selfie -> SimpleStep(step, canGoBack, onBack, onNext)
                    Step.SelfieCamera -> CameraCaptureScreen(state, CaptureMode.SELFIE, canGoBack, onBack, onNext)
                    Step.Processing -> ProcessingScreen(
                        state, canGoBack, onBack, onNext,
                        onRetakePhotos = {
                            navController.navigate(Step.DocumentFront.route) {
                                popUpTo(Step.DocumentFront.route) { inclusive = true }
                            }
                        }
                    )
                    Step.Result -> ResultScreen(
                        state = state,
                        canGoBack = false,
                        onBack = onBack,
                        // La emisión de la PID se inicia desde Configuración > Info Personal.
                        onWelcome = onFinished,
                        onRetryData = {
                            // Los datos escritos no coinciden con la cédula: se borran las fotos y se
                            // vuelve a pedir nombres, cédula y fecha de nacimiento.
                            state.prepareRetry()
                            navController.navigate(Step.Cedula.route) { popUpTo(0) }
                        },
                        onRetryPhotos = {
                            // Datos correctos pero la validación no salió bien (rostro/calidad): solo fotos.
                            state.prepareRetakePhotos()
                            navController.navigate(Step.DocumentFront.route) { popUpTo(0) }
                        }
                    )
                    else -> SimpleStep(step, canGoBack, onBack, onNext)
                }
            }
        }
    }
}
