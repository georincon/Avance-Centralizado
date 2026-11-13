package eu.europa.ec.onboardingfeature.ui.personalinfo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.europa.ec.onboardingfeature.data.PersonalInfo
import eu.europa.ec.onboardingfeature.data.PersonalInfoStore
import eu.europa.ec.onboardingfeature.data.ValidationReport
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ContentTitle
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import org.koin.compose.koinInject

/** "Info Personal": datos de la cédula validados en el onboarding (solo lectura). */
@Composable
fun PersonalInfoScreen(onBack: () -> Unit, onRequestPid: () -> Unit, onRequestAcademic: () -> Unit = {}) {
    val store = koinInject<PersonalInfoStore>()
    val documents = koinInject<WalletCoreDocumentsController>()
    val loaded by produceState(initialValue = Loaded(false, null)) {
        value = Loaded(true, store.load())
    }
    var hasBothPidFormats by remember { mutableStateOf(false) }
    val refreshPidStatus = {
        documents.getAllDocumentsByType(listOf(DocumentIdentifier.MdocPid)).isNotEmpty() &&
            documents.getAllDocumentsByType(listOf(DocumentIdentifier.SdJwtPid)).isNotEmpty()
    }
    LaunchedEffect(documents) { hasBothPidFormats = refreshPidStatus() }
    LifecycleEffect(LocalLifecycleOwner.current, Lifecycle.Event.ON_RESUME) {
        hasBothPidFormats = refreshPidStatus()
    }
    ContentScreen(
        navigatableAction = ScreenNavigateAction.BACKABLE,
        isLoading = !loaded.done,
        onBack = onBack,
    ) { padding -> Body(loaded.info, hasBothPidFormats, padding, onRequestPid, onRequestAcademic) }
}

private data class Loaded(val done: Boolean, val info: PersonalInfo?)

@Composable
private fun Body(info: PersonalInfo?, hasBothPidFormats: Boolean, padding: PaddingValues, onRequestPid: () -> Unit, onRequestAcademic: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        ContentTitle(
            modifier = Modifier.fillMaxWidth(),
            title = "Info Personal",
            subtitle = "Datos de tu cédula y reporte de la validación (fotos de la cédula y selfie)."
        )
        if (info == null) {
            Text(
                "Aún no hay datos validados.",
                modifier = Modifier.padding(top = 24.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }
        val age = info.ageOn()
        Column(modifier = Modifier.padding(top = 16.dp)) {
            InfoRow("Número de cédula", info.documentNumber)
            InfoRow("Nombres", info.firstNames)
            InfoRow("Apellidos", info.lastNames)
            InfoRow("Fecha de nacimiento", info.birthDate.dmy())
            InfoRow("Edad", age?.let { "$it años" })
            InfoRow("Mayor de edad", age?.let { if (it >= 18) "Sí" else "No" })
            InfoRow("Lugar de nacimiento", info.birthPlace)
            InfoRow("Sexo", when (info.gender) { "MALE" -> "Masculino"; "FEMALE" -> "Femenino"; else -> null })
            InfoRow("Grupo sanguíneo y RH", info.bloodType)
            InfoRow("Estatura", info.heightCm?.let { "%d.%02d m".format(it / 100, it % 100) })
            InfoRow("Fecha de expedición", info.issueDate?.dmy())
            InfoRow("Lugar de expedición", info.expeditionPlace)
        }
        info.report?.let { ReportSection(it, info.validatedAtEpochMs) }
        androidx.compose.material3.Button(
            onClick = onRequestPid,
            enabled = info.sessionId != null && !hasBothPidFormats,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp)
        ) { Text(if (hasBothPidFormats) "Ya tienes los dos tipos de PID" else "Obtener mi PID de la Registraduría") }
        androidx.compose.material3.OutlinedButton(
            onClick = onRequestAcademic,
            enabled = hasBothPidFormats,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) { Text(if (hasBothPidFormats) "Pedir mis credenciales de la UIS" else "Primero obtén tu PID para poder identificarte") }
    }
}

@Composable
private fun ReportSection(r: ValidationReport, validatedAt: Long) {
    fun mark(ok: Boolean?) = when (ok) { true -> "✔"; false -> "✘"; null -> "—" }
    fun pct(v: Double?, min: Double) = v?.let { "${if (it >= min) "✔" else "✘"} ${(it * 100).toInt()}%" }
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text("Reporte de validación", style = MaterialTheme.typography.titleMedium)
        InfoRow(
            "Resultado",
            when (r.decision) { "APPROVED" -> "✔ Aprobada"; "MANUAL_REVIEW" -> "⚠ Revisión manual"; null -> null; else -> "✘ Rechazada" }
        )
        InfoRow("Fecha de validación", java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.US).format(java.util.Date(validatedAt)))
        Text("Lo que escribiste vs. tu cédula", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        InfoRow("Número de cédula", mark(r.documentNumberMatch))
        InfoRow("Fecha de nacimiento", mark(r.birthDateMatch))
        InfoRow("Nombres", pct(r.firstNamesSimilarity, 0.75))
        InfoRow("Apellidos", pct(r.lastNamesSimilarity, 0.75))
        Text("Selfie vs. foto de la cédula", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        InfoRow("Calidad de la selfie", mark(r.selfieQualityOk))
        InfoRow("Comparación facial", mark(r.faceMatchOk))
        InfoRow("Similitud facial", r.faceMatchScore?.let { "${(it.coerceAtLeast(0.0) * 100).toInt()}%" })
        InfoRow("Puntaje", r.faceMatchScore?.let { "%.2f".format(it) })
        InfoRow("Mínimo requerido", "36% (0.36)")
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
    HorizontalDivider()
}

/** AAAA-MM-DD -> DD-MM-AAAA */
private fun String.dmy(): String = split("-").takeIf { it.size == 3 }?.let { "${it[2]}-${it[1]}-${it[0]}" } ?: this
