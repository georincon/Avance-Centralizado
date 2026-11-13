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

package eu.europa.ec.dashboardfeature.interactor

import android.net.Uri
import eu.europa.ec.businesslogic.migration.MigrationBackup
import eu.europa.ec.businesslogic.migration.MigrationDecryptionException
import eu.europa.ec.businesslogic.migration.MigrationFormatException
import eu.europa.ec.businesslogic.migration.MigrationObject
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.migration.toMigratableDocuments
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsItemUi
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsMenuItemType
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface MigrationExportResult {
    data class Success(val bytes: ByteArray) : MigrationExportResult
    /** No hay documentos que se puedan volver a pedir. */
    data object NothingToExport : MigrationExportResult
    data object Failure : MigrationExportResult
}

sealed interface MigrationImportResult {
    data class Success(val migrationObject: MigrationObject) : MigrationImportResult
    /** Frase incorrecta o archivo alterado (no se distingue a propósito). */
    data object WrongPassphraseOrTampered : MigrationImportResult
    data object InvalidFile : MigrationImportResult
}

/**
 * Lógica de la copia de migración (ARF Topic 34) para Ajustes.
 *
 * El Objeto de Migración NO se persiste: se construye en el momento de exportar a partir de los
 * documentos emitidos (instantánea) y solo se lee en la importación.
 */
interface MigrationInteractor {
    suspend fun getMigrationItemsUi(): List<SettingsItemUi>
    fun suggestedFileName(): String
    fun validatePassphrase(passphrase: CharArray, confirmation: CharArray?): PassphraseError?
    suspend fun buildExport(passphrase: CharArray): MigrationExportResult
    suspend fun writeToUri(uri: Uri, bytes: ByteArray): Boolean
    suspend fun readFromUri(uri: Uri): ByteArray?
    suspend fun importBackup(data: ByteArray, passphrase: CharArray): MigrationImportResult
}

enum class PassphraseError { TOO_SHORT, MISMATCH }

class MigrationInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val documentsController: WalletCoreDocumentsController,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val today: () -> LocalDate = { LocalDate.now() },
) : MigrationInteractor {

    companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
    }

    override suspend fun getMigrationItemsUi(): List<SettingsItemUi> = listOf(
        item(SettingsMenuItemType.EXPORT_MIGRATION, R.string.migration_option_export, AppIcons.ShareOut),
        item(SettingsMenuItemType.IMPORT_MIGRATION, R.string.migration_option_import, AppIcons.ShareIn),
    )

    private fun item(type: SettingsMenuItemType, label: Int, icon: eu.europa.ec.uilogic.component.IconDataUi) =
        SettingsItemUi(
            type = type,
            data = ListItemDataUi(
                itemId = type.itemId,
                mainContentData = ListItemMainContentDataUi.Text(resourceProvider.getString(label)),
                leadingContentData = ListItemLeadingContentDataUi.Icon(iconData = icon),
                trailingContentData = ListItemTrailingContentDataUi.Icon(
                    iconData = AppIcons.KeyboardArrowRight
                )
            )
        )

    override fun suggestedFileName(): String {
        val d = today()
        return "wallet-migracion-%d%02d%02d.nwmo".format(d.year, d.monthValue, d.dayOfMonth)
    }

    override fun validatePassphrase(passphrase: CharArray, confirmation: CharArray?): PassphraseError? =
        when {
            passphrase.size < MIN_PASSPHRASE_LENGTH -> PassphraseError.TOO_SHORT
            confirmation != null && !passphrase.contentEquals(confirmation) -> PassphraseError.MISMATCH
            else -> null
        }

    override suspend fun buildExport(passphrase: CharArray): MigrationExportResult =
        withContext(Dispatchers.Default) {
            try {
                // Solo metadatos de emisión; nunca claims ni claves.
                val documents = documentsController.getAllIssuedDocuments()
                    .toMigratableDocuments(resourceProvider.getLocale())
                if (documents.isEmpty()) return@withContext MigrationExportResult.NothingToExport
                val now = clock()
                val obj = MigrationObject.empty(now).syncWith(documents, now)
                MigrationExportResult.Success(MigrationBackup.export(obj, passphrase))
            } catch (e: Exception) {
                MigrationExportResult.Failure
            }
        }

    override suspend fun writeToUri(uri: Uri, bytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                resourceProvider.provideContentResolver().openOutputStream(uri, "wt")?.use {
                    it.write(bytes)
                    true
                } ?: false
            } catch (e: Exception) {
                false
            }
        }

    override suspend fun readFromUri(uri: Uri): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                resourceProvider.provideContentResolver().openInputStream(uri)?.use {
                    it.readBytes()
                }
            } catch (e: Exception) {
                null
            }
        }

    override suspend fun importBackup(data: ByteArray, passphrase: CharArray): MigrationImportResult =
        withContext(Dispatchers.Default) {
            try {
                MigrationImportResult.Success(MigrationBackup.import(data, passphrase))
            } catch (e: MigrationDecryptionException) {
                MigrationImportResult.WrongPassphraseOrTampered
            } catch (e: MigrationFormatException) {
                MigrationImportResult.InvalidFile
            } catch (e: Exception) {
                MigrationImportResult.InvalidFile
            }
        }
}
