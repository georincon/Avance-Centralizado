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

import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Locale

class TestMigrationInteractor {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val resources: ResourceProvider = mock()
    private val controller: WalletCoreDocumentsController = mock()
    private lateinit var interactor: MigrationInteractor

    private fun doc(id: String, format: eu.europa.ec.eudi.wallet.document.format.DocumentFormat, issuer: String): IssuedDocument {
        val meta = mock<IssuerMetadata>()
        whenever(meta.credentialIssuerIdentifier).thenReturn(issuer)
        whenever(meta.documentConfigurationIdentifier).thenReturn("cfg-$id")
        val d = mock<IssuedDocument>()
        whenever(d.id).thenReturn(id)
        whenever(d.name).thenReturn("Doc $id")
        whenever(d.format).thenReturn(format)
        whenever(d.issuerMetadata).thenReturn(meta)
        return d
    }

    @Before
    fun before() {
        whenever(resources.getLocale()).thenReturn(Locale.ENGLISH)
        whenever(resources.getString(org.mockito.kotlin.any())).thenReturn("txt")
        interactor = MigrationInteractorImpl(
            resourceProvider = resources,
            documentsController = controller,
            clock = { 1_700_000_000_000L },
            today = { LocalDate.of(2026, 10, 30) },
        )
    }

    @Test
    fun `nombre sugerido lleva la fecha y extension nwmo`() {
        assertEquals("wallet-migracion-20261030.nwmo", interactor.suggestedFileName())
    }

    @Test
    fun `frase corta se rechaza, frases distintas se rechazan, valida pasa`() {
        assertEquals(PassphraseError.TOO_SHORT, interactor.validatePassphrase("1234567".toCharArray(), "1234567".toCharArray()))
        assertEquals(PassphraseError.MISMATCH, interactor.validatePassphrase("12345678".toCharArray(), "12345679".toCharArray()))
        assertNull(interactor.validatePassphrase("12345678".toCharArray(), "12345678".toCharArray()))
        assertNull(interactor.validatePassphrase("12345678".toCharArray(), null))
    }

    @Test
    fun `exportar e importar devuelve las entradas con PID presente y sin datos extra`() = coroutineRule.runTest {
        val docs = listOf(
            doc("a", SdJwtVcFormat("urn:uis:carnet:1"), "https://uis.example"),
            doc("b", MsoMdocFormat("eu.europa.ec.eudi.pid.1"), "https://pid.example"),
        )
        whenever(controller.getAllIssuedDocuments()).thenReturn(docs)
        val export = interactor.buildExport("frase-segura".toCharArray())
        assertTrue(export is MigrationExportResult.Success)
        val bytes = (export as MigrationExportResult.Success).bytes

        val result = interactor.importBackup(bytes, "frase-segura".toCharArray())
        assertTrue(result is MigrationImportResult.Success)
        val obj = (result as MigrationImportResult.Success).migrationObject
        assertEquals(listOf("a", "b"), obj.entries.map { it.documentId })
        assertEquals(listOf(false, true), obj.entries.map { it.isPid })
        // restorePlan pone el PID primero
        assertEquals(listOf("b", "a"), obj.restorePlan(setOf("a", "b")).map { it.documentId })
    }

    @Test
    fun `frase incorrecta y archivo alterado dan el mismo resultado`() = coroutineRule.runTest {
        val docs = listOf(doc("a", SdJwtVcFormat("urn:x"), "https://x.example"))
        whenever(controller.getAllIssuedDocuments()).thenReturn(docs)
        val bytes = (interactor.buildExport("frase-segura".toCharArray()) as MigrationExportResult.Success).bytes

        assertEquals(
            MigrationImportResult.WrongPassphraseOrTampered,
            interactor.importBackup(bytes, "otra-frase-mal".toCharArray())
        )
        val tampered = bytes.copyOf().also { it[it.size - 3] = (it[it.size - 3].toInt() xor 1).toByte() }
        assertEquals(
            MigrationImportResult.WrongPassphraseOrTampered,
            interactor.importBackup(tampered, "frase-segura".toCharArray())
        )
    }

    @Test
    fun `archivo que no es una copia da InvalidFile`() = coroutineRule.runTest {
        assertEquals(
            MigrationImportResult.InvalidFile,
            interactor.importBackup(ByteArray(80) { 7 }, "frase-segura".toCharArray())
        )
    }

    @Test
    fun `sin documentos migrables no se exporta`() = coroutineRule.runTest {
        whenever(controller.getAllIssuedDocuments()).thenReturn(emptyList())
        assertEquals(MigrationExportResult.NothingToExport, interactor.buildExport("frase-segura".toCharArray()))
    }
}
