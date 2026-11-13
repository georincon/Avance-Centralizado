package eu.europa.ec.onboardingfeature.ui.personalinfo

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.europa.ec.corelogic.BuildConfig
import eu.europa.ec.issuancefeature.di.getOrCreateCredentialOfferScope
import eu.europa.ec.issuancefeature.interactor.DocumentOfferInteractor
import eu.europa.ec.issuancefeature.interactor.IssueDocumentsInteractorPartialState
import eu.europa.ec.issuancefeature.interactor.ResolveDocumentOfferInteractorPartialState
import eu.europa.ec.onboardingfeature.network.UisIngresoClient
import eu.europa.ec.onboardingfeature.network.UisIngresoEstado
import eu.europa.ec.onboardingfeature.network.UisIngresoException
import eu.europa.ec.onboardingfeature.network.UisPrograma
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ContentTitle
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** El ingreso en curso sobrevive a que la pantalla de presentación (la de la wallet) tome el primer plano y vuelva. */
private object IngresoEnCurso {
    @Volatile var id: String? = null
    @Volatile var desde: Long = 0L
    fun vigente(): String? = id?.takeIf { System.currentTimeMillis() - desde < 5 * 60_000L }
}

/**
 * Credenciales de la UIS sin contraseña ni navegador: el estudiante comparte su cédula digital (PID) desde la wallet, la UIS la
 * verifica y le muestra sus programas; al elegir uno se emite la credencial. La identidad sale del PID, no de lo que se escriba.
 */
@Composable
fun AcademicIssuanceScreen(onBack: () -> Unit, onIssued: () -> Unit) {
    val context = LocalContext.current
    val uisUrl = remember { BuildConfig.NW_ISSUER_URL.substringBeforeLast("/pid-registraduria") + "/uis-issuer" }
    val client = remember { UisIngresoClient(uisUrl) }
    val offerInteractor = remember { getOrCreateCredentialOfferScope().get<DocumentOfferInteractor>() }
    val scope = rememberCoroutineScope()
    var ingresoId by remember { mutableStateOf(IngresoEnCurso.vigente()) }
    var estado by remember { mutableStateOf<UisIngresoEstado?>(null) }
    var busy by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    // Mientras hay un ingreso en curso y la pantalla está a la vista, se consulta si la UIS ya verificó el PID.
    val resumed = LocalLifecycleOwner.current
    var tick by remember { mutableStateOf(0) }
    LifecycleEffect(resumed, Lifecycle.Event.ON_RESUME) { ingresoId = IngresoEnCurso.vigente(); tick++ }
    LaunchedEffect(ingresoId, tick) {
        val id = ingresoId ?: return@LaunchedEffect
        while (estado !is UisIngresoEstado.Listo && estado !is UisIngresoEstado.Error) {
            val r = runCatching { withContext(Dispatchers.IO) { client.estado(id) } }
            r.onSuccess { estado = it }.onFailure { mensaje = (it as? UisIngresoException)?.message ?: "Error de conexión." ; return@LaunchedEffect }
            if (estado is UisIngresoEstado.Esperando) delay(2000)
        }
    }

    fun emitir(p: UisPrograma, credencial: String) {
        val id = ingresoId ?: return
        busy = true; mensaje = null
        scope.launch {
            runCatching {
                val offerUri = withContext(Dispatchers.IO) { client.oferta(id, p.historialId, credencial) }
                val resolved = offerInteractor.resolveDocumentOffer(offerUri).first()
                when (resolved) {
                    is ResolveDocumentOfferInteractorPartialState.Success -> {
                        val navigation = ConfigNavigation(
                            navigationType = NavigationType.PushScreen(screen = DashboardScreens.Dashboard, popUpToScreen = DashboardScreens.Dashboard),
                        )
                        offerInteractor.issueDocuments(offerUri, resolved.issuerName, navigation).collect { r ->
                            when (r) {
                                is IssueDocumentsInteractorPartialState.Success,
                                is IssueDocumentsInteractorPartialState.PartialSuccessWithUntrustedIssuer,
                                is IssueDocumentsInteractorPartialState.DeferredSuccess -> { IngresoEnCurso.id = null; onIssued() }
                                is IssueDocumentsInteractorPartialState.Failure -> { mensaje = "No pudimos emitir tu credencial: ${r.errorMessage}"; busy = false }
                                is IssueDocumentsInteractorPartialState.IssuerNotTrusted -> { mensaje = "La UIS no figura como emisor de confianza."; busy = false }
                                is IssueDocumentsInteractorPartialState.UserAuthRequired -> offerInteractor.handleUserAuthentication(
                                    context = context, crypto = r.crypto, notifyOnAuthenticationFailure = true, resultHandler = r.resultHandler,
                                )
                            }
                        }
                    }
                    is ResolveDocumentOfferInteractorPartialState.Failure -> { mensaje = resolved.errorMessage; busy = false }
                    is ResolveDocumentOfferInteractorPartialState.IssuerNotTrusted -> { mensaje = "La UIS no figura como emisor de confianza."; busy = false }
                    is ResolveDocumentOfferInteractorPartialState.NoDocument -> { mensaje = "La UIS no incluyó credenciales en la oferta."; busy = false }
                }
            }.onFailure {
                if (it !is CancellationException) { mensaje = (it as? UisIngresoException)?.message ?: (it.message ?: "Error al pedir la credencial."); busy = false }
            }
        }
    }

    ContentScreen(navigatableAction = ScreenNavigateAction.BACKABLE, onBack = onBack, isLoading = false) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ContentTitle(
                modifier = Modifier.fillMaxWidth(),
                title = "Credenciales de la UIS",
                subtitle = "Comparte tu cédula digital (PID) y te mostramos tus programas. Sin contraseñas ni navegador.",
            )
            when (val e = estado) {
                is UisIngresoEstado.Listo -> {
                    Text("Hola, ${e.nombre}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                    e.programas.forEach { p ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(p.nombre, style = MaterialTheme.typography.titleSmall)
                            Text("${p.nivel} · ${p.estado}", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { emitir(p, "academic") }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text(if (busy) "Emitiendo…" else "Credencial académica")
                            }
                            if (p.estado == "ACTIVO") {
                                OutlinedButton(onClick = { emitir(p, "carnet") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Carné estudiantil") }
                            }
                        }
                    }
                }
                is UisIngresoEstado.Error -> {
                    Text(e.motivo, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    OutlinedButton(onClick = { IngresoEnCurso.id = null; ingresoId = null; estado = null }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("Volver a intentar") }
                }
                else -> {
                    if (ingresoId != null) {
                        Text("Esperando que compartas tu cédula digital con la UIS…", modifier = Modifier.padding(16.dp))
                        Text("Si aún no viste la solicitud, vuelve a abrirla.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    Button(
                        onClick = {
                            busy = true; mensaje = null
                            scope.launch {
                                runCatching {
                                    val (id, requestUri) = withContext(Dispatchers.IO) { client.iniciar() }
                                    IngresoEnCurso.id = id; IngresoEnCurso.desde = System.currentTimeMillis(); ingresoId = id; estado = null
                                    // La solicitud se procesa dentro de la propia wallet: muestra qué se comparte y pide confirmar.
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(requestUri)).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.onFailure { mensaje = (it as? UisIngresoException)?.message ?: (it.message ?: "No se pudo iniciar.") }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text(if (ingresoId != null) "Compartir de nuevo mi cédula digital" else "Compartir mi cédula digital con la UIS") }
                }
            }
            mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            TextButton(modifier = Modifier.fillMaxWidth(), onClick = onBack) { Text("Volver") }
        }
    }
}
