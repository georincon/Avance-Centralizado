package eu.europa.ec.onboardingfeature.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import eu.europa.ec.onboardingfeature.navigation.Step
import eu.europa.ec.onboardingfeature.ui.OnboardingState
import eu.europa.ec.onboardingfeature.ui.StepScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun OtpField(
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Código de 6 dígitos") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun SimpleStep(
    step: Step,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    StepScreen(
        title = step.title,
        subtitle = step.subtitle,
        buttonText = step.button,
        canGoBack = canGoBack,
        onBack = onBack,
        onNext = onNext
    )
}

@Composable
fun PhoneScreen(
    state: OnboardingState,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var phone by rememberSaveable { mutableStateOf(state.data.phone) }
    StepScreen(
        title = Step.Phone.title,
        subtitle = Step.Phone.subtitle,
        buttonText = Step.Phone.button,
        canGoBack = canGoBack,
        onBack = onBack,
        enabled = phone.length == 10,
        onNext = {
            state.updatePhone(phone)
            state.sendPhoneOtp()
            onNext()
        }
    ) {
        OutlinedTextField(
            value = phone,
            onValueChange = { v -> phone = v.filter { it.isDigit() }.take(10) },
            label = { Text("Número de celular") },
            prefix = { Text("+57  ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun OtpScreen(
    state: OnboardingState,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    StepScreen(
        title = Step.Otp.title,
        subtitle = Step.Otp.subtitle,
        buttonText = Step.Otp.button,
        canGoBack = canGoBack,
        onBack = onBack,
        enabled = code.length == 6,
        onNext = {
            if (state.verifyPhoneOtp(code)) onNext() else error = true
        }
    ) {
        OtpField(value = code, onValueChange = { v ->
            code = v.filter { it.isDigit() }.take(6)
            error = false
        })
        Hint("Simulación (sin SMS): tu código es ${state.data.phoneOtpGenerated}")
        if (error) ErrorText("Código incorrecto, intenta de nuevo.")
    }
}

@Composable
fun CedulaScreen(
    state: OnboardingState,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var cedula by rememberSaveable { mutableStateOf(state.data.cedula) }
    StepScreen(
        title = Step.Cedula.title,
        subtitle = Step.Cedula.subtitle,
        buttonText = Step.Cedula.button,
        canGoBack = canGoBack,
        onBack = onBack,
        enabled = cedula.length in 6..10,
        onNext = {
            state.updateCedula(cedula)
            onNext()
        }
    ) {
        OutlinedTextField(
            value = cedula,
            onValueChange = { v -> cedula = v.filter { it.isDigit() }.take(10) },
            label = { Text("Número de cédula") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun NamesScreen(
    state: OnboardingState,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var nombres by rememberSaveable { mutableStateOf(state.data.nombres) }
    var apellidos by rememberSaveable { mutableStateOf(state.data.apellidos) }
    var birthDate by rememberSaveable { mutableStateOf(state.data.birthDate) }
    StepScreen(
        title = Step.Names.title,
        subtitle = "Escríbelos como aparecen en tu cédula. Mayúsculas y tildes no importan, pero la fecha de nacimiento debe ser exacta.",
        buttonText = Step.Names.button,
        canGoBack = canGoBack,
        onBack = onBack,
        enabled = nombres.isNotBlank() && apellidos.isNotBlank() && birthDate.isNotBlank(),
        onNext = {
            state.updateNombres(nombres)
            state.updateApellidos(apellidos)
            state.updateBirthDate(birthDate)
            onNext()
        }
    ) {
        OutlinedTextField(
            value = nombres,
            onValueChange = { nombres = it },
            label = { Text("Nombres") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = apellidos,
            onValueChange = { apellidos = it },
            label = { Text("Apellidos") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        BirthDateField(
            value = birthDate,
            onValueChange = { birthDate = it }
        )
    }
}

@Composable
fun EmailScreen(
    state: OnboardingState,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf(state.data.email) }
    val valid = email.contains("@") && email.substringAfter("@").contains(".")
    StepScreen(
        title = Step.Email.title,
        subtitle = Step.Email.subtitle,
        buttonText = Step.Email.button,
        canGoBack = canGoBack,
        onBack = onBack,
        enabled = valid,
        onNext = {
            state.updateEmail(email)
            state.sendEmailOtp()
            onNext()
        }
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun EmailOtpScreen(
    state: OnboardingState,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    StepScreen(
        title = Step.EmailOtp.title,
        subtitle = Step.EmailOtp.subtitle,
        buttonText = Step.EmailOtp.button,
        canGoBack = canGoBack,
        onBack = onBack,
        enabled = code.length == 6,
        onNext = {
            if (state.verifyEmailOtp(code)) onNext() else error = true
        }
    ) {
        OtpField(value = code, onValueChange = { v ->
            code = v.filter { it.isDigit() }.take(6)
            error = false
        })
        Hint("Simulación (sin correo): tu código es ${state.data.emailOtpGenerated}")
        if (error) ErrorText("Código incorrecto, intenta de nuevo.")
    }
}

@Composable
fun ConsentScreen(
    state: OnboardingState,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var accepted by rememberSaveable { mutableStateOf(state.data.consentAccepted) }
    StepScreen(
        title = Step.Consent.title,
        subtitle = Step.Consent.subtitle,
        buttonText = Step.Consent.button,
        canGoBack = canGoBack,
        onBack = onBack,
        enabled = accepted,
        onNext = {
            state.updateConsent(accepted)
            onNext()
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
            Text(
                text = "Acepto la política de tratamiento de datos personales.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tus datos serán usados exclusivamente para validar tu identidad. " +
                "Puedes ejercer tus derechos de conocer, actualizar, rectificar y suprimir " +
                "tu información escribiendo al correo de contacto.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SummaryField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    prefix: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        prefix = if (prefix != null) ({ Text(prefix) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    )
}

@Composable
fun SummaryScreen(
    state: OnboardingState,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var phone by rememberSaveable { mutableStateOf(state.data.phone) }
    var cedula by rememberSaveable { mutableStateOf(state.data.cedula) }
    var nombres by rememberSaveable { mutableStateOf(state.data.nombres) }
    var apellidos by rememberSaveable { mutableStateOf(state.data.apellidos) }
    var email by rememberSaveable { mutableStateOf(state.data.email) }
    var birthDate by rememberSaveable { mutableStateOf(state.data.birthDate) }
    val emailValid = email.contains("@") && email.substringAfter("@").contains(".")
    val valid = phone.length == 10 && cedula.length in 6..10 &&
        nombres.isNotBlank() && apellidos.isNotBlank() && emailValid && birthDate.isNotBlank()
    StepScreen(
        title = Step.Summary.title,
        subtitle = "Revisa y corrige tus datos si es necesario. Estos datos se validarán con tu documento.",
        buttonText = Step.Summary.button,
        canGoBack = canGoBack,
        onBack = onBack,
        enabled = valid,
        onNext = {
            state.updatePhone(phone)
            state.updateCedula(cedula)
            state.updateNombres(nombres)
            state.updateApellidos(apellidos)
            state.updateBirthDate(birthDate)
            state.updateEmail(email)
            onNext()
        }
    ) {
        Column(Modifier.fillMaxWidth()) {
            SummaryField("Celular", phone, { phone = it.filter { ch -> ch.isDigit() }.take(10) }, KeyboardType.Phone, "+57 ")
            SummaryField("Cédula", cedula, { cedula = it.filter { ch -> ch.isDigit() }.take(10) }, KeyboardType.Number)
            SummaryField("Nombres", nombres, { nombres = it })
            SummaryField("Apellidos", apellidos, { apellidos = it })
            BirthDateField(
                value = birthDate,
                onValueChange = { birthDate = it },
                modifier = Modifier.padding(vertical = 6.dp)
            )
            SummaryField("Correo", email, { email = it }, KeyboardType.Email)
        }
    }
}

@Composable
fun ProcessingScreen(
    state: OnboardingState,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onRetakePhotos: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val missingCaptures = !state.hasAllCaptures || state.retakeNeeded
    LaunchedEffect(Unit) { state.runBackend() }
    LaunchedEffect(state.backendResult) {
        if (state.backendResult != null) {
            delay(800)
            onNext()
        }
    }
    StepScreen(
        title = Step.Processing.title,
        subtitle = Step.Processing.subtitle,
        buttonText = when {
            state.backendError == null -> null
            missingCaptures -> "Reintentar (tomar las fotos de nuevo)"
            else -> "Reintentar"
        },
        canGoBack = false,
        onBack = onBack,
        onNext = {
            if (missingCaptures) {
                state.prepareRetakePhotos()
                onRetakePhotos()
            } else {
                scope.launch { state.resetBackend(); state.runBackend() }
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.backendError == null) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Validando tus documentos con el servidor...",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                ErrorText("No se pudo validar: ${state.backendError}")
                Spacer(Modifier.height(8.dp))
                if (!missingCaptures) Text(
                    text = "Revisa que el servidor esté encendido y accesible desde el teléfono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.ifBlank { null } ?: "—", style = MaterialTheme.typography.bodyMedium)
    }
}

private fun String?.titleCase(): String? = this?.lowercase()?.split(" ")
    ?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }?.ifBlank { null }

/** AAAA-MM-DD -> DD-MM-AAAA */
private fun String?.dmy(): String? = this?.split("-")?.takeIf { it.size == 3 }?.let { "${it[2]}-${it[1]}-${it[0]}" }

@Composable
fun ResultScreen(
    state: OnboardingState,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onWelcome: () -> Unit,
    onRetryData: () -> Unit,
    onRetryPhotos: () -> Unit
) {
    val r = state.backendResult
    val x = r?.extracted.orEmpty()
    val m = r?.dataMatch.orEmpty()
    val (sign, signColor) = when (r?.decision) {
        "APPROVED" -> "✔" to Color(0xFF2E7D32)
        "MANUAL_REVIEW" -> "⚠" to Color(0xFFEF6C00)
        else -> "✘" to Color(0xFFC62828)
    }
    fun mark(ok: Boolean) = if (ok) "✔" else "✘"
    val birthPlace = listOfNotNull(
        x["birthCity"].titleCase(),
        x["birthDepartment"].titleCase()?.let { "($it)" }
    ).joinToString(" ").ifBlank { null }
    StepScreen(
        title = when (r?.decision) {
            "APPROVED" -> "Validación exitosa"
            "MANUAL_REVIEW" -> "Revisión manual"
            else -> "No pudimos validarte"
        },
        subtitle = if (r?.decision == "APPROVED") null else r?.failureReason,
        buttonText = when {
            r?.decision == "APPROVED" -> "Bienvenido a New Wallet"
            else -> "Volver a intentarlo"
        },
        enabled = true,
        canGoBack = false,
        onBack = onBack,
        onNext = when {
            r?.decision == "APPROVED" -> onWelcome
            state.hasDataMismatch() -> onRetryData
            else -> onRetryPhotos
        }
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                sign,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 72.sp,
                color = signColor
            )
            Spacer(Modifier.height(8.dp))

            Text("Datos de tu cédula", style = MaterialTheme.typography.labelLarge)
            ResultRow("Número", x["documentNumber"])
            ResultRow("Nombres", listOfNotNull(x["firstName"], x["middleName"]).joinToString(" ").titleCase())
            ResultRow("Apellidos", listOfNotNull(x["lastName"], x["secondLastName"]).joinToString(" ").titleCase())
            ResultRow("Fecha de nacimiento", x["birthDate"].dmy())
            ResultRow("Lugar de nacimiento", birthPlace)
            ResultRow("Estatura", x["heightCm"]?.toIntOrNull()?.let { "%d.%02d m".format(it / 100, it % 100) })
            ResultRow("Grupo sanguíneo y RH", x["bloodType"])
            ResultRow("Sexo", when (x["gender"]) { "MALE" -> "Masculino"; "FEMALE" -> "Femenino"; else -> null })
            ResultRow("Fecha de expedición", x["issueDate"].dmy())
            ResultRow("Lugar de expedición", x["expeditionPlace"].titleCase())

            if (m.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Lo que escribiste vs. tu cédula", style = MaterialTheme.typography.labelLarge)
                m["documentNumberMatch"]?.let { ResultRow("Número de cédula", mark(it == "true")) }
                m["birthDateMatch"]?.let { ResultRow("Fecha de nacimiento", mark(it == "true")) }
                m["firstNamesSimilarity"]?.toDoubleOrNull()?.let {
                    ResultRow("Nombres", "${mark(it >= 0.75)} ${(it * 100).toInt()}%")
                }
                m["lastNamesSimilarity"]?.toDoubleOrNull()?.let {
                    ResultRow("Apellidos", "${mark(it >= 0.75)} ${(it * 100).toInt()}%")
                }
            }

            // Al final: comparación selfie vs foto de la cédula (similitud coseno, 100% = idéntico).
            if (r?.faceMatchStatus != null) {
                val score = r.faceMatchScore
                Spacer(Modifier.height(12.dp))
                Text("Selfie vs. foto de la cédula", style = MaterialTheme.typography.labelLarge)
                ResultRow("Calidad de la selfie", mark(r.livenessStatus == "SUCCESS"))
                ResultRow("Comparación facial", mark(r.faceMatchStatus == "SUCCESS"))
                ResultRow("Similitud facial", score?.let { "${(it.coerceAtLeast(0.0) * 100).toInt()}%" })
                ResultRow("Puntaje", score?.let { "%.2f".format(it) })
                ResultRow("Mínimo requerido", "36% (0.36)")
            }
            state.backendError?.let {
                Spacer(Modifier.height(12.dp))
                ErrorText(it)
            }
        }
    }
}
