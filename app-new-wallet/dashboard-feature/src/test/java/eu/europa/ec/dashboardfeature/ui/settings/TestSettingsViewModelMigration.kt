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

import android.net.Uri
import eu.europa.ec.businesslogic.migration.MigrationEntry
import eu.europa.ec.businesslogic.migration.MigrationObject
import eu.europa.ec.dashboardfeature.interactor.MigrationExportResult
import eu.europa.ec.dashboardfeature.interactor.MigrationImportResult
import eu.europa.ec.dashboardfeature.interactor.MigrationInteractor
import eu.europa.ec.dashboardfeature.interactor.PassphraseError
import eu.europa.ec.dashboardfeature.interactor.SettingsInteractor
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsMenuItemType
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TestSettingsViewModelMigration {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val settings: SettingsInteractor = mock()
    private val resources: ResourceProvider = mock()
    private val migration: MigrationInteractor = mock()
    private val serializer: UiSerializer = mock()

    private lateinit var vm: SettingsViewModel

    private fun entry(id: String, pid: Boolean) = MigrationEntry(
        documentId = id, attestationType = "t$id", format = "mso_mdoc", isPid = pid,
        deviceBound = true, issuerIdentifier = "https://i", credentialIssuerUrl = "https://i",
        credentialConfigurationId = "c", issuerDisplayName = "Emisor", documentDisplayName = "Doc $id",
    )

    @Before
    fun before() {
        whenever(resources.getString(any())).thenReturn("txt")
        whenever(settings.getAppVersion()).thenReturn("v")
        vm = SettingsViewModel(settings, resources, migration, serializer)
    }

    @Test
    fun `tocar exportar abre el dialogo de frase`() = coroutineRule.runTest {
        vm.handleEvents(Event.ItemClicked(SettingsMenuItemType.EXPORT_MIGRATION, mock()))
        assertEquals(MigrationDialog.ExportPassphrase(), vm.viewState.value.migrationDialog)
    }

    @Test
    fun `frase invalida no exporta y muestra error`() = coroutineRule.runTest {
        whenever(migration.validatePassphrase(any(), any())).thenReturn(PassphraseError.TOO_SHORT)
        vm.handleEvents(Event.MigrationExportConfirmed("abc", "abc"))
        coroutineRule.testScope.runCurrent()
        assertTrue(vm.viewState.value.migrationDialog is MigrationDialog.ExportPassphrase)
        verify(migration, never()).buildExport(any())
    }

    @Test
    fun `frase valida construye la copia y pide destino`() = coroutineRule.runTest {
        whenever(migration.validatePassphrase(any(), any())).thenReturn(null)
        whenever(migration.buildExport(any())).thenReturn(MigrationExportResult.Success(byteArrayOf(1, 2, 3)))
        whenever(migration.suggestedFileName()).thenReturn("wallet-migracion-20261030.nwmo")
        val effects = mutableListOf<Effect>()
        val job = coroutineRule.testScope.launch { vm.effect.collect { e -> effects += e } }
        vm.handleEvents(Event.MigrationExportConfirmed("frase-segura", "frase-segura"))
        coroutineRule.testScope.runCurrent()
        assertNull(vm.viewState.value.migrationDialog)
        job.cancel()
        assertTrue(effects.contains(Effect.LaunchCreateMigrationFile("wallet-migracion-20261030.nwmo")))
    }

    @Test
    fun `importar con frase mala conserva el dialogo con error`() = coroutineRule.runTest {
        val uri: Uri = mock()
        whenever(migration.readFromUri(uri)).thenReturn(byteArrayOf(1))
        whenever(migration.validatePassphrase(any(), any())).thenReturn(null)
        whenever(migration.importBackup(any(), any())).thenReturn(MigrationImportResult.WrongPassphraseOrTampered)
        whenever(resources.getString(eq(eu.europa.ec.resourceslogic.R.string.migration_import_wrong_passphrase)))
            .thenReturn("MALA")

        vm.handleEvents(Event.MigrationImportFileChosen(uri))
        coroutineRule.testScope.runCurrent()
        assertTrue(vm.viewState.value.migrationDialog is MigrationDialog.ImportPassphrase)

        vm.handleEvents(Event.MigrationImportConfirmed("incorrecta-1"))
        coroutineRule.testScope.runCurrent()
        assertEquals(MigrationDialog.ImportPassphrase("MALA"), vm.viewState.value.migrationDialog)
    }

    @Test
    fun `importar bien muestra lista con PID primero y todo marcado, y permite desmarcar`() = coroutineRule.runTest {
        val uri: Uri = mock()
        val obj = MigrationObject(createdAt = 1, updatedAt = 1, entries = listOf(entry("a", false), entry("b", true)))
        whenever(migration.readFromUri(uri)).thenReturn(byteArrayOf(1))
        whenever(migration.validatePassphrase(any(), any())).thenReturn(null)
        whenever(migration.importBackup(any(), any())).thenReturn(MigrationImportResult.Success(obj))

        vm.handleEvents(Event.MigrationImportFileChosen(uri))
        coroutineRule.testScope.runCurrent()
        vm.handleEvents(Event.MigrationImportConfirmed("correcta-123"))
        coroutineRule.testScope.runCurrent()

        val list = vm.viewState.value.migrationDialog as MigrationDialog.RestoreList
        assertEquals(listOf("b", "a"), list.entries.map { it.documentId })
        assertEquals(setOf("a", "b"), list.selectedIds)

        vm.handleEvents(Event.MigrationToggleEntry("a"))
        val after = vm.viewState.value.migrationDialog as MigrationDialog.RestoreList
        assertEquals(setOf("b"), after.selectedIds)
    }
}
