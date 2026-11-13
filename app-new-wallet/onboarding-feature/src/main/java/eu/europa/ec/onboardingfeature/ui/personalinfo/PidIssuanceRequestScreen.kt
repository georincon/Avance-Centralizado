package eu.europa.ec.onboardingfeature.ui.personalinfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.europa.ec.corelogic.BuildConfig
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.UntrustedIssuerReasonDomain
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.issuancefeature.di.getOrCreateCredentialOfferScope
import eu.europa.ec.issuancefeature.interactor.DocumentOfferInteractor
import eu.europa.ec.issuancefeature.interactor.IssueDocumentsInteractorPartialState
import eu.europa.ec.issuancefeature.interactor.ResolveDocumentOfferInteractorPartialState
import eu.europa.ec.onboardingfeature.data.PersonalInfoStore
import eu.europa.ec.onboardingfeature.network.PidOfferClient
import eu.europa.ec.onboardingfeature.R
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.extension.getPendingUri
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ContentTitle
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.content.BroadcastAction
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun PidIssuanceRequestScreen(onBack: () -> Unit, onIssued: () -> Unit) {
    val store = koinInject<PersonalInfoStore>()
    val documents = koinInject<WalletCoreDocumentsController>()
    val context = LocalContext.current
    val offerInteractor = remember { getOrCreateCredentialOfferScope().get<DocumentOfferInteractor>() }
    val info by produceState(initialValue = null as eu.europa.ec.onboardingfeature.data.PersonalInfo?) {
        value = store.load()
    }
    val credentialTypes = listOf(DocumentIdentifier.MdocPid.formatType, DocumentIdentifier.SdJwtPid.formatType)
    var existing by remember { mutableStateOf(emptySet<String>()) }
    val refresh = {
        buildSet {
            if (documents.getAllDocumentsByType(listOf(DocumentIdentifier.MdocPid)).isNotEmpty()) add(DocumentIdentifier.MdocPid.formatType)
            if (documents.getAllDocumentsByType(listOf(DocumentIdentifier.SdJwtPid)).isNotEmpty()) add(DocumentIdentifier.SdJwtPid.formatType)
        }
    }
    LaunchedEffect(documents) { existing = refresh() }
    LifecycleEffect(LocalLifecycleOwner.current, Lifecycle.Event.ON_RESUME) { existing = refresh() }
    LifecycleEffect(LocalLifecycleOwner.current, Lifecycle.Event.ON_RESUME) {
        context.getPendingUri()?.toString()?.let(offerInteractor::resumeOpenId4VciWithAuthorization)
    }
    var selected by remember(existing) { mutableStateOf(credentialTypes.filterNot(existing::contains).toSet()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorPrefix by remember { mutableStateOf("No pudimos crear tu solicitud: ") }
    val scope = rememberCoroutineScope()
    var issuanceJob by remember { mutableStateOf<Job?>(null) }

    ContentScreen(
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = onBack,
        isLoading = info == null,
        broadcastAction = BroadcastAction(
            intentFilters = listOf(CoreActions.VCI_RESUME_ACTION, CoreActions.VCI_DYNAMIC_PRESENTATION),
            callback = { intent ->
                when (intent?.action) {
                    CoreActions.VCI_RESUME_ACTION,
                    CoreActions.VCI_DYNAMIC_PRESENTATION -> intent.extras?.getString("uri")?.let(offerInteractor::resumeOpenId4VciWithAuthorization)
                }
            },
        ),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ContentTitle(
                modifier = Modifier.fillMaxWidth(),
                title = "Solicitud de emisión",
                subtitle = "Selecciona el formato de tu cédula de ciudadanía que quieres agregar a la wallet.",
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.Start,
            ) {
                Image(
                    painter = painterResource(R.drawable.registraduria_escudo),
                    contentDescription = "Escudo de la Registraduría Nacional del Estado Civil",
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    "Registraduría Nacional del Estado Civil",
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            credentialTypes.forEach { type ->
                val present = type in existing
                val format = if (type == DocumentIdentifier.MdocPid.formatType) "mDoc" else "SD-JWT VC"
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Cédula de ciudadanía", style = MaterialTheme.typography.titleSmall)
                        Text("Formato: $format", style = MaterialTheme.typography.bodyMedium)
                        if (present) Text("Ya está guardada en tu wallet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(
                        checked = present || type in selected,
                        enabled = !present && !busy,
                        onCheckedChange = { checked -> selected = if (checked) selected + type else selected - type },
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                enabled = !busy && selected.isNotEmpty() && info?.sessionId != null,
                onClick = {
                    busy = true
                    error = null
                    // La peticion se crea primero y la emision va despues: el prefijo del
                    // error tiene que decir en que fase fallo, o el usuario lee "no pudimos
                    // crear tu solicitud" cuando la solicitud SI se creo.
                    errorPrefix = "No pudimos crear tu solicitud: "
                    issuanceJob = scope.launch {
                        runCatching {
                            val formats = selected.map { type -> if (type == DocumentIdentifier.MdocPid.formatType) "mso_mdoc" else "dc+sd-jwt" }
                            withContext(Dispatchers.IO) { PidOfferClient(BuildConfig.NW_ISSUER_URL).requestOffer(info!!.sessionId!!, formats) }
                        }.onSuccess { offerUri ->
                            val resolved = offerInteractor.resolveDocumentOffer(offerUri).first()
                            when (resolved) {
                                is ResolveDocumentOfferInteractorPartialState.Success -> {
                                    errorPrefix = "No pudimos emitir tu documento: "
                                    val navigation = ConfigNavigation(
                                        navigationType = NavigationType.PushScreen(
                                            screen = DashboardScreens.Dashboard,
                                            popUpToScreen = DashboardScreens.Dashboard,
                                        ),
                                    )
                                    offerInteractor.issueDocuments(offerUri, resolved.issuerName, navigation).collect { result ->
                                        when (result) {
                                            is IssueDocumentsInteractorPartialState.Success,
                                            is IssueDocumentsInteractorPartialState.PartialSuccessWithUntrustedIssuer,
                                            is IssueDocumentsInteractorPartialState.DeferredSuccess -> onIssued()
                                            is IssueDocumentsInteractorPartialState.Failure -> { error = result.errorMessage; busy = false }
                                            is IssueDocumentsInteractorPartialState.IssuerNotTrusted -> { error = result.reason.toUserMessage(); busy = false }
                                            is IssueDocumentsInteractorPartialState.UserAuthRequired -> offerInteractor.handleUserAuthentication(
                                                context = context,
                                                crypto = result.crypto,
                                                notifyOnAuthenticationFailure = true,
                                                resultHandler = result.resultHandler,
                                            )
                                        }
                                    }
                                }
                                is ResolveDocumentOfferInteractorPartialState.Failure -> { error = resolved.errorMessage; busy = false }
                                is ResolveDocumentOfferInteractorPartialState.IssuerNotTrusted -> { error = resolved.reason.toUserMessage(); busy = false }
                                is ResolveDocumentOfferInteractorPartialState.NoDocument -> { error = "La Registraduría no incluyó credenciales en la oferta."; busy = false }
                            }
                        }.onFailure {
                            if (it !is CancellationException) {
                                error = it.message ?: "Error de conexión con la Registraduría."
                                busy = false
                            }
                        }
                    }
                },
            ) { Text(if (busy) "Contactando a la Registraduría…" else "Continuar con la emisión") }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { issuanceJob?.cancel(); onBack() },
            ) { Text("Cancelar") }
            error?.let { Text("$errorPrefix$it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        }
    }
}

/**
 * Traduce el motivo por el que se rechazo al emisor a un mensaje que diga QUE fallo, no solo
 * que "no se pudo verificar la confianza": el certificado de acceso y el de registro son
 * problemas distintos y se corrigen distinto.
 */
private fun UntrustedIssuerReasonDomain.toUserMessage(): String = when (this) {
    UntrustedIssuerReasonDomain.ACCESS_CERTIFICATE ->
        "La Registraduría no presenta un certificado de acceso válido en la lista de confianza."

    UntrustedIssuerReasonDomain.REGISTRATION_CERTIFICATE ->
        "La Registraduría no presenta un certificado de registro válido en la lista de confianza."
}
