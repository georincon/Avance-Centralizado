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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsItemUi
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsMenuItemType
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ContentTitle
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.OncePerViewModelEffect
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapListItem
import eu.europa.ec.uilogic.extension.openIntentChooser
import eu.europa.ec.uilogic.extension.openUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Selectores de documentos del sistema (Storage Access Framework) para la copia de migración.
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> viewModel.setEvent(Event.MigrationExportDestinationChosen(uri)) }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.setEvent(Event.MigrationImportFileChosen(uri)) }

    ContentScreen(
        navigatableAction = ScreenNavigateAction.BACKABLE,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { snackbarData ->
                    Snackbar(snackbarData = snackbarData)
                }
            )
        },
        isLoading = state.isLoading,
        onBack = { viewModel.setEvent(Event.Pop) }
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onEventSend = { viewModel.setEvent(it) },
            onNavigationRequested = { navigationEffect ->
                handleNavigationEffect(
                    navigationEffect = navigationEffect,
                    navController = navController,
                    context = context,
                    onLaunchBiometricSystemScreen = {
                        viewModel.setEvent(Event.LaunchBiometricSystemScreen)
                    }
                )
            },
            snackbarHostState = snackbarHostState,
            context = context,
            paddingValues = paddingValues,
            onLaunchCreateMigrationFile = { createLauncher.launch(it) },
            onLaunchOpenMigrationFile = { openLauncher.launch(arrayOf("*/*")) },
        )
        MigrationDialogs(state = state, onEventSend = { viewModel.setEvent(it) })
        AppearanceDialog(state = state, onEventSend = { viewModel.setEvent(it) })
    }

    OncePerViewModelEffect(viewModel) {
        viewModel.setEvent(Event.Init)
    }
}

private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
    context: Context,
    onLaunchBiometricSystemScreen: () -> Unit,
) {
    when (navigationEffect) {
        is Effect.Navigation.Pop -> navController.popBackStack()

        is Effect.Navigation.LaunchBiometricsSystemScreen -> onLaunchBiometricSystemScreen()

        is Effect.Navigation.OpenUrlExternally -> context.openUrl(uri = navigationEffect.url)

        is Effect.Navigation.GoToAddDocument -> navController.navigate(navigationEffect.screenRoute)
    }
}

@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSend: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    snackbarHostState: SnackbarHostState,
    context: Context,
    paddingValues: PaddingValues,
    onLaunchCreateMigrationFile: (String) -> Unit = {},
    onLaunchOpenMigrationFile: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            ContentTitle(
                modifier = Modifier.fillMaxWidth(),
                title = state.screenTitle,
            )

            SettingsItems(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                items = state.settingsItems,
                onEventSent = onEventSend,
                context = context,
            )
        }

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SPACING_MEDIUM.dp),
            text = state.appVersion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
                is Effect.ShareLogFile -> {
                    context.openIntentChooser(
                        effect.intent,
                        effect.chooserTitle
                    )
                }

                is Effect.LaunchCreateMigrationFile -> onLaunchCreateMigrationFile(effect.fileName)
                is Effect.LaunchOpenMigrationFile -> onLaunchOpenMigrationFile()

                is Effect.ShowSnackbar -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    launch {
                        snackbarHostState.showSnackbar(
                            message = effect.message,
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            }
        }.collect()
    }
}

@Composable
private fun AppearanceDialog(
    state: State,
    onEventSend: (Event) -> Unit,
) {
    if (!state.showAppearanceDialog) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val current = eu.europa.ec.resourceslogic.theme.ThemeAppearance.mode
    val options = listOf(
        eu.europa.ec.resourceslogic.theme.AppearanceMode.BLUE to R.string.appearance_option_blue,
        eu.europa.ec.resourceslogic.theme.AppearanceMode.WHITE to R.string.appearance_option_white,
        eu.europa.ec.resourceslogic.theme.AppearanceMode.BLACK to R.string.appearance_option_black,
    )
    AlertDialog(
        onDismissRequest = { onEventSend(Event.AppearanceDialogDismissed) },
        title = { Text(stringResource(R.string.appearance_dialog_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.appearance_dialog_message))
                options.forEach { (mode, label) ->
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .clickable { onEventSend(Event.AppearanceSelected(mode, context)) }
                            .padding(vertical = 4.dp),
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = mode == current,
                            onClick = { onEventSend(Event.AppearanceSelected(mode, context)) },
                        )
                        Text(stringResource(label))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEventSend(Event.AppearanceDialogDismissed) }) {
                Text(stringResource(R.string.appearance_dialog_close))
            }
        },
    )
}

@Composable
private fun MigrationDialogs(
    state: State,
    onEventSend: (Event) -> Unit,
) {
    when (val dialog = state.migrationDialog) {
        null -> Unit

        is MigrationDialog.ExportPassphrase -> PassphraseDialog(
            title = stringResource(R.string.migration_export_title),
            message = stringResource(R.string.migration_export_message),
            error = dialog.error,
            askConfirmation = true,
            onConfirm = { pass, confirm ->
                onEventSend(Event.MigrationExportConfirmed(pass, confirm))
            },
            onDismiss = { onEventSend(Event.MigrationDialogDismissed) },
        )

        is MigrationDialog.ImportPassphrase -> PassphraseDialog(
            title = stringResource(R.string.migration_import_title),
            message = stringResource(R.string.migration_import_message),
            error = dialog.error,
            askConfirmation = false,
            onConfirm = { pass, _ -> onEventSend(Event.MigrationImportConfirmed(pass)) },
            onDismiss = { onEventSend(Event.MigrationDialogDismissed) },
        )

        is MigrationDialog.RestoreList -> AlertDialog(
            onDismissRequest = { onEventSend(Event.MigrationDialogDismissed) },
            title = { Text(stringResource(R.string.migration_restore_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.migration_restore_message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (dialog.entries.isEmpty()) {
                        Text(
                            modifier = Modifier.padding(top = SPACING_MEDIUM.dp),
                            text = stringResource(R.string.migration_restore_empty),
                        )
                    }
                    dialog.entries.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEventSend(Event.MigrationToggleEntry(entry.documentId)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = entry.documentId in dialog.selectedIds,
                                onCheckedChange = {
                                    onEventSend(Event.MigrationToggleEntry(entry.documentId))
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                val name = entry.documentDisplayName ?: entry.attestationType
                                Text(
                                    text = if (entry.isPid) {
                                        name + " (" + stringResource(R.string.migration_restore_pid_tag) + ")"
                                    } else name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.migration_restore_entry_detail,
                                        entry.issuerDisplayName ?: entry.credentialIssuerUrl,
                                        entry.attestationType,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = dialog.selectedIds.isNotEmpty(),
                    onClick = { onEventSend(Event.MigrationRestoreConfirmed) },
                ) { Text(stringResource(R.string.migration_restore_action)) }
            },
            dismissButton = {
                TextButton(onClick = { onEventSend(Event.MigrationDialogDismissed) }) {
                    Text(stringResource(R.string.migration_action_close))
                }
            },
        )
    }
}

@Composable
private fun PassphraseDialog(
    title: String,
    message: String,
    error: String?,
    askConfirmation: Boolean,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SPACING_MEDIUM.dp),
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.migration_passphrase_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (askConfirmation) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = SPACING_SMALL.dp),
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text(stringResource(R.string.migration_passphrase_confirm_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Text(
                        modifier = Modifier.padding(top = SPACING_SMALL.dp),
                        text = stringResource(R.string.migration_passphrase_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (error != null) {
                    Text(
                        modifier = Modifier.padding(top = SPACING_SMALL.dp),
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pass, confirm) }) {
                Text(stringResource(R.string.migration_action_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.migration_action_cancel))
            }
        },
    )
}

@Composable
private fun SettingsItems(
    modifier: Modifier = Modifier,
    items: List<SettingsItemUi>,
    onEventSent: (Event) -> Unit,
    context: Context,
) {
    Column(
        modifier = modifier
    ) {
        items.forEachIndexed { index, settingsItemUi ->
            WrapListItem(
                modifier = Modifier.fillMaxWidth(),
                item = settingsItemUi.data,
                onItemClick = {
                    onEventSent(
                        Event.ItemClicked(
                            itemType = settingsItemUi.type,
                            context = context
                        )
                    )
                },
                throttleClicks = false,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                mainContentVerticalPadding = SPACING_MEDIUM.dp,
            )


            if (index != items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SPACING_SMALL.dp)
                )
            }
        }
    }
}

@ThemeModePreviews
@Composable
private fun SettingsScreenPreview() {
    PreviewTheme {
        val context = LocalContext.current

        val settingsItems = listOf(
            SettingsItemUi(
                type = SettingsMenuItemType.RETRIEVE_LOGS,
                data = ListItemDataUi(
                    itemId = SettingsMenuItemType.RETRIEVE_LOGS.itemId,
                    mainContentData = ListItemMainContentDataUi.Text(
                        text = stringResource(R.string.settings_screen_option_retrieve_logs)
                    ),
                    leadingContentData = ListItemLeadingContentDataUi.Icon(
                        iconData = AppIcons.OpenNew
                    ),
                    trailingContentData = ListItemTrailingContentDataUi.Icon(
                        iconData = AppIcons.KeyboardArrowRight
                    )
                )
            )
        )

        Content(
            state = State(
                screenTitle = stringResource(R.string.settings_screen_title),
                settingsItems = settingsItems,
                appVersion = "1.0.0",
                changelogUrl = "",
            ),
            effectFlow = emptyFlow(),
            onEventSend = {},
            onNavigationRequested = {},
            snackbarHostState = remember { SnackbarHostState() },
            context = context,
            paddingValues = PaddingValues(SPACING_MEDIUM.dp)
        )
    }
}