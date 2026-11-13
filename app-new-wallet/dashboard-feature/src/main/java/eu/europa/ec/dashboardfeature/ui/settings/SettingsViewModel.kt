/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.dashboardfeature.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAuthenticate
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.businesslogic.extension.toUri
import eu.europa.ec.businesslogic.migration.MigrationEntry
import eu.europa.ec.businesslogic.migration.MigrationObject
import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.commonfeature.config.IssuanceUiConfig
import eu.europa.ec.dashboardfeature.interactor.MigrationExportResult
import eu.europa.ec.dashboardfeature.interactor.MigrationImportResult
import eu.europa.ec.dashboardfeature.interactor.MigrationInteractor
import eu.europa.ec.dashboardfeature.interactor.PassphraseError
import eu.europa.ec.dashboardfeature.interactor.SettingsInteractor
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsItemUi
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsMenuItemType
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class State(
    val isLoading: Boolean = false,
    val screenTitle: String,

    val settingsItems: List<SettingsItemUi> = emptyList(),

    val appVersion: String = "",
    val changelogUrl: String?,

    val migrationDialog: MigrationDialog? = null,
    val showAppearanceDialog: Boolean = false,
) : ViewState

/** Diálogos de la copia de migración (ARF Topic 34). */
sealed interface MigrationDialog {
    data class ExportPassphrase(val error: String? = null) : MigrationDialog
    data class ImportPassphrase(val error: String? = null) : MigrationDialog

    /** Entradas ya ordenadas con el PID primero (restorePlan). */
    data class RestoreList(
        val entries: List<MigrationEntry>,
        val selectedIds: Set<String>,
    ) : MigrationDialog
}

sealed class Event : ViewEvent {
    data object Init : Event()
    data object Pop : Event()
    data object LaunchBiometricSystemScreen : Event()
    data class ItemClicked(
        val itemType: SettingsMenuItemType,
        val context: Context
    ) : Event()

    // Copia de migración. Las frases viajan como String solo desde el campo de texto y se
    // convierten a CharArray (y se limpian) en cuanto se usan.
    data class MigrationExportConfirmed(val passphrase: String, val confirmation: String) : Event()
    data class MigrationExportDestinationChosen(val uri: Uri?) : Event()
    data class MigrationImportFileChosen(val uri: Uri?) : Event()
    data class MigrationImportConfirmed(val passphrase: String) : Event()
    data class MigrationToggleEntry(val documentId: String) : Event()
    data object MigrationRestoreConfirmed : Event()
    data object MigrationDialogDismissed : Event()

    data class AppearanceSelected(val mode: eu.europa.ec.resourceslogic.theme.AppearanceMode, val context: Context) : Event()
    data object AppearanceDialogDismissed : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
        data object LaunchBiometricsSystemScreen : Navigation()

        data class OpenUrlExternally(val url: Uri) : Navigation()

        /** Lleva a la pantalla EXISTENTE de agregar documento. */
        data class GoToAddDocument(val screenRoute: String) : Navigation()
    }

    data class LaunchCreateMigrationFile(val fileName: String) : Effect()
    data object LaunchOpenMigrationFile : Effect()

    data class ShareLogFile(val intent: Intent, val chooserTitle: String) : Effect()
    data class ShowSnackbar(val message: String) : Effect()
}

@KoinViewModel
class SettingsViewModel(
    private val settingsInteractor: SettingsInteractor,
    private val resourceProvider: ResourceProvider,
    private val migrationInteractor: MigrationInteractor,
    private val uiSerializer: UiSerializer,
) : MviViewModel<Event, State, Effect>() {

    private var isBiometricAuthenticating: Boolean = false

    // Copia ya cifrada, esperando a que la persona elija dónde guardarla.
    private var pendingExportBytes: ByteArray? = null

    // Archivo importado, esperando la frase de paso.
    private var pendingImportBytes: ByteArray? = null
    private var importedObject: MigrationObject? = null

    override fun setInitialState(): State {
        return State(
            screenTitle = resourceProvider.getString(R.string.settings_screen_title),
            appVersion = settingsInteractor.getAppVersion(),
            changelogUrl = settingsInteractor.getChangelogUrl(),
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> {
                createSettingsItemsUi(viewState.value.changelogUrl)
            }

            is Event.Pop -> setEffect { Effect.Navigation.Pop }

            is Event.LaunchBiometricSystemScreen -> {
                settingsInteractor.launchBiometricSystemScreen()
            }

            is Event.MigrationExportConfirmed -> onExportConfirmed(event)
            is Event.MigrationExportDestinationChosen -> onExportDestinationChosen(event.uri)
            is Event.MigrationImportFileChosen -> onImportFileChosen(event.uri)
            is Event.MigrationImportConfirmed -> onImportConfirmed(event.passphrase)
            is Event.MigrationToggleEntry -> onToggleEntry(event.documentId)
            is Event.MigrationRestoreConfirmed -> onRestoreConfirmed()
            is Event.MigrationDialogDismissed -> {
                clearMigrationState()
                setState { copy(migrationDialog = null) }
            }

            is Event.AppearanceSelected -> {
                eu.europa.ec.resourceslogic.theme.ThemeAppearance.set(event.context, event.mode)
                setState { copy(showAppearanceDialog = false) }
            }

            is Event.AppearanceDialogDismissed -> setState { copy(showAppearanceDialog = false) }

            is Event.ItemClicked -> handleSettingsMenuItemClicked(
                itemType = event.itemType,
                context = event.context
            )
        }
    }

    private fun createSettingsItemsUi(changelogUrl: String?) {
        viewModelScope.launch {
            setState {
                copy(
                    isLoading = true,
                )
            }

            val settingsItems = loadItems(changelogUrl)
            setState {
                copy(
                    isLoading = false,
                    settingsItems = settingsItems,
                )
            }
        }
    }

    private suspend fun loadItems(changelogUrl: String?): List<SettingsItemUi> {
        // Las dos filas de migración van justo antes del historial de cambios (si existe).
        val base = settingsInteractor.getSettingsItemsUi(changelogUrl = changelogUrl)
        val migration = migrationInteractor.getMigrationItemsUi()
        val idx = base.indexOfFirst { it.type == SettingsMenuItemType.CHANGELOG }
        return if (idx >= 0) base.take(idx) + migration + base.drop(idx) else base + migration
    }

    //region Copia de migración
    private fun clearMigrationState() {
        pendingExportBytes?.fill(0)
        pendingExportBytes = null
        pendingImportBytes = null
        importedObject = null
    }

    private fun passphraseErrorMessage(error: PassphraseError): String = resourceProvider.getString(
        when (error) {
            PassphraseError.TOO_SHORT -> R.string.migration_error_passphrase_short
            PassphraseError.MISMATCH -> R.string.migration_error_passphrase_mismatch
        }
    )

    private fun onExportConfirmed(event: Event.MigrationExportConfirmed) {
        val pass = event.passphrase.toCharArray()
        val confirm = event.confirmation.toCharArray()
        val error = migrationInteractor.validatePassphrase(pass, confirm)
        if (error != null) {
            pass.fill('\u0000'); confirm.fill('\u0000')
            setState { copy(migrationDialog = MigrationDialog.ExportPassphrase(passphraseErrorMessage(error))) }
            return
        }
        confirm.fill('\u0000')
        setState { copy(migrationDialog = null, isLoading = true) }
        viewModelScope.launch {
            val result = try {
                migrationInteractor.buildExport(pass)
            } finally {
                pass.fill('\u0000')
            }
            setState { copy(isLoading = false) }
            when (result) {
                is MigrationExportResult.Success -> {
                    pendingExportBytes = result.bytes
                    setEffect { Effect.LaunchCreateMigrationFile(migrationInteractor.suggestedFileName()) }
                }

                MigrationExportResult.NothingToExport -> snackbar(R.string.migration_export_empty)
                MigrationExportResult.Failure -> snackbar(R.string.migration_export_error)
            }
        }
    }

    private fun onExportDestinationChosen(uri: Uri?) {
        val bytes = pendingExportBytes
        pendingExportBytes = null
        if (uri == null || bytes == null) {
            bytes?.fill(0)
            return // la persona canceló el selector
        }
        viewModelScope.launch {
            val ok = migrationInteractor.writeToUri(uri, bytes)
            bytes.fill(0)
            snackbar(if (ok) R.string.migration_export_success else R.string.migration_export_error)
        }
    }

    private fun onImportFileChosen(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val bytes = migrationInteractor.readFromUri(uri)
            if (bytes == null) {
                snackbar(R.string.migration_import_read_error)
            } else {
                pendingImportBytes = bytes
                setState { copy(migrationDialog = MigrationDialog.ImportPassphrase()) }
            }
        }
    }

    private fun onImportConfirmed(passphrase: String) {
        val data = pendingImportBytes ?: return
        val pass = passphrase.toCharArray()
        if (migrationInteractor.validatePassphrase(pass, null) != null) {
            pass.fill('\u0000')
            setState {
                copy(migrationDialog = MigrationDialog.ImportPassphrase(
                    resourceProvider.getString(R.string.migration_error_passphrase_short)
                ))
            }
            return
        }
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            val result = try {
                migrationInteractor.importBackup(data, pass)
            } finally {
                pass.fill('\u0000')
            }
            setState { copy(isLoading = false) }
            when (result) {
                is MigrationImportResult.Success -> {
                    pendingImportBytes = null
                    importedObject = result.migrationObject
                    val ordered = result.migrationObject.restorePlan(
                        result.migrationObject.entries.map { it.documentId }.toSet()
                    )
                    setState {
                        copy(
                            migrationDialog = MigrationDialog.RestoreList(
                                entries = ordered,
                                selectedIds = ordered.map { it.documentId }.toSet(),
                            )
                        )
                    }
                }

                // Mensaje claro, sin detalles técnicos. Se conserva el archivo para reintentar.
                MigrationImportResult.WrongPassphraseOrTampered -> setState {
                    copy(migrationDialog = MigrationDialog.ImportPassphrase(
                        resourceProvider.getString(R.string.migration_import_wrong_passphrase)
                    ))
                }

                MigrationImportResult.InvalidFile -> {
                    clearMigrationState()
                    setState { copy(migrationDialog = null) }
                    snackbar(R.string.migration_import_invalid_file)
                }
            }
        }
    }

    private fun onToggleEntry(documentId: String) {
        val dialog = viewState.value.migrationDialog as? MigrationDialog.RestoreList ?: return
        val selected = if (documentId in dialog.selectedIds) {
            dialog.selectedIds - documentId
        } else {
            dialog.selectedIds + documentId
        }
        setState { copy(migrationDialog = dialog.copy(selectedIds = selected)) }
    }

    /**
     * No se enlaza a un emisor/tipo concreto: el flujo existente de agregar documento no admite
     * pre-seleccionar emisor de forma comprobable. Se lleva a la pantalla de agregar documento
     * (sin filtro) y se muestra el resumen de qué pedir, PID primero.
     */
    private fun onRestoreConfirmed() {
        val dialog = viewState.value.migrationDialog as? MigrationDialog.RestoreList ?: return
        val plan = importedObject?.restorePlan(dialog.selectedIds).orEmpty()
        clearMigrationState()
        setState { copy(migrationDialog = null) }
        if (plan.isEmpty()) return
        setEffect { Effect.Navigation.GoToAddDocument(addDocumentRoute()) }
    }

    private fun addDocumentRoute(): String = generateComposableNavigationLink(
        screen = IssuanceScreens.AddDocument,
        arguments = generateComposableArguments(
            mapOf(
                IssuanceUiConfig.serializedKeyName to uiSerializer.toBase64(
                    model = IssuanceUiConfig(
                        flowType = IssuanceFlowType.ExtraDocument(formatType = null)
                    ),
                    parser = IssuanceUiConfig.Parser
                )
            )
        )
    )

    private fun snackbar(resId: Int) {
        setEffect { Effect.ShowSnackbar(resourceProvider.getString(resId)) }
    }
    //endregion

    private fun handleSettingsMenuItemClicked(
        itemType: SettingsMenuItemType,
        context: Context,
    ) {
        when (itemType) {
            SettingsMenuItemType.BIOMETRICS_AUTHENTICATION -> {
                if (isBiometricAuthenticating) return
                when (val availability = settingsInteractor.getBiometricsAvailability()) {
                    is BiometricsAvailability.CanAuthenticate -> authenticate(context)

                    is BiometricsAvailability.NonEnrolled -> {
                        setEffect {
                            Effect.Navigation.LaunchBiometricsSystemScreen
                        }
                    }

                    is BiometricsAvailability.Failure -> {
                        setEffect {
                            Effect.ShowSnackbar(availability.errorMessage)
                        }
                    }
                }
            }

            SettingsMenuItemType.APPEARANCE -> setState { copy(showAppearanceDialog = true) }

            SettingsMenuItemType.SHOW_BATCH_ISSUANCE_COUNTER -> {
                viewModelScope.launch {
                    settingsInteractor.toggleShowBatchIssuanceCounter()

                    val settingsItems = loadItems(viewState.value.changelogUrl)

                    setState {
                        copy(
                            settingsItems = settingsItems,
                        )
                    }
                }
            }

            SettingsMenuItemType.REGISTRATION_CHECK -> {
                viewModelScope.launch {
                    settingsInteractor.toggleRegistrationCheck()

                    val settingsItems = loadItems(viewState.value.changelogUrl)

                    setState {
                        copy(
                            settingsItems = settingsItems,
                        )
                    }

                    setEffect {
                        Effect.ShowSnackbar(
                            message = resourceProvider.getString(R.string.settings_screen_option_registration_check_restart)
                        )
                    }
                }
            }

            SettingsMenuItemType.RETRIEVE_LOGS -> {
                val logs = settingsInteractor.retrieveLogFileUris()
                if (logs.isNotEmpty()) {
                    setEffect {
                        Effect.ShareLogFile(
                            intent = Intent().apply {
                                action = Intent.ACTION_SEND_MULTIPLE
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, logs)
                                type = "text/*"
                            },
                            chooserTitle = resourceProvider.getString(R.string.settings_intent_chooser_logs_share_title)
                        )
                    }
                }
            }

            SettingsMenuItemType.EXPORT_MIGRATION -> {
                setState { copy(migrationDialog = MigrationDialog.ExportPassphrase()) }
            }

            SettingsMenuItemType.IMPORT_MIGRATION -> {
                setEffect { Effect.LaunchOpenMigrationFile }
            }

            SettingsMenuItemType.CHANGELOG -> {
                val changelogUrl = viewState.value.changelogUrl
                if (changelogUrl != null) {
                    setEffect {
                        Effect.Navigation.OpenUrlExternally(
                            url = changelogUrl.toUri()
                        )
                    }
                }
            }
        }
    }

    private fun authenticate(context: Context) {

        if (isBiometricAuthenticating) return
        isBiometricAuthenticating = true

        settingsInteractor.authenticateWithBiometrics(
            context = context,
            notifyOnAuthenticationFailure = false,
        ) { result ->
            when (result) {
                is BiometricsAuthenticate.Success -> {
                    viewModelScope.launch {
                        try {
                            settingsInteractor.toggleBiometricsAuthentication()
                            val settingsItems = loadItems(viewState.value.changelogUrl)
                            setState { copy(settingsItems = settingsItems) }
                        } finally {
                            isBiometricAuthenticating = false
                        }
                    }
                }

                is BiometricsAuthenticate.Failed -> {
                    isBiometricAuthenticating = false
                    setEffect {
                        Effect.ShowSnackbar(message = result.errorMessage)
                    }
                }

                BiometricsAuthenticate.Cancelled -> {
                    isBiometricAuthenticating = false
                }
            }
        }
    }
}