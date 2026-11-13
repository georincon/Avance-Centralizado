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

package eu.europa.ec.corelogic.migration

import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Locale

class TestIssuedDocumentMigratable {

    private fun doc(
        docId: String,
        format: eu.europa.ec.eudi.wallet.document.format.DocumentFormat,
        issuer: String?,
        configId: String? = "cfg",
        docName: String = "Mi documento",
    ): IssuedDocument {
        val meta = issuer?.let {
            mock<IssuerMetadata>().also { m ->
                whenever(m.credentialIssuerIdentifier).thenReturn(it)
                whenever(m.documentConfigurationIdentifier).thenReturn(configId.orEmpty())
            }
        }
        return mock<IssuedDocument>().also {
            whenever(it.id).thenReturn(docId)
            whenever(it.name).thenReturn(docName)
            whenever(it.format).thenReturn(format)
            whenever(it.issuerMetadata).thenReturn(meta)
        }
    }

    @Test
    fun `mdoc PID se marca como PID y conserva tipo, emisor y configuracion`() {
        val m = doc("1", MsoMdocFormat("eu.europa.ec.eudi.pid.1"), "https://iss.example")
            .toMigratableDocument(Locale.ENGLISH)!!
        assertEquals("1", m.id)
        assertEquals("eu.europa.ec.eudi.pid.1", m.attestationType)
        assertEquals("mso_mdoc", m.format)
        assertTrue(m.isPid)
        assertEquals("https://iss.example", m.issuerIdentifier)
        assertEquals("cfg", m.credentialConfigurationId)
        assertEquals("Mi documento", m.documentDisplayName)
    }

    @Test
    fun `sd-jwt PID se marca como PID`() {
        val m = doc("2", SdJwtVcFormat("urn:eudi:pid:1"), "https://iss.example")
            .toMigratableDocument(Locale.ENGLISH)!!
        assertEquals("dc+sd-jwt", m.format)
        assertTrue(m.isPid)
    }

    @Test
    fun `atestacion que no es PID no se marca como PID`() {
        val m = doc("3", SdJwtVcFormat("urn:uis:carnet:1"), "https://uis.example")
            .toMigratableDocument(Locale.ENGLISH)!!
        assertFalse(m.isPid)
        assertEquals("urn:uis:carnet:1", m.attestationType)
    }

    @Test
    fun `documento sin metadatos de emisor se omite`() {
        assertNull(doc("4", MsoMdocFormat("x"), null).toMigratableDocument(Locale.ENGLISH))
        val list = listOf(
            doc("4", MsoMdocFormat("x"), null),
            doc("5", MsoMdocFormat("y"), "https://iss.example"),
        ).toMigratableDocuments(Locale.ENGLISH)
        assertEquals(listOf("5"), list.map { it.id })
    }
}
