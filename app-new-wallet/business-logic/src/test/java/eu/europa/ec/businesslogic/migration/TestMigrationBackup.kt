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

package eu.europa.ec.businesslogic.migration

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TestMigrationBackup {

    private class Doc(
        override val id: String,
        override val attestationType: String = "eu.europa.ec.eudi.pid.1",
        override val format: String = "mso_mdoc",
        override val isPid: Boolean = false,
        override val deviceBound: Boolean = true,
        override val issuerIdentifier: String = "https://issuer.example/uis",
        override val credentialConfigurationId: String? = "cfg-$id",
        override val issuerDisplayName: String? = "UIS",
        override val documentDisplayName: String? = "Carnet",
    ) : MigratableDocument

    private val pw = "frase secreta".toCharArray()
    private val fast = 2_000
    private val docs = listOf(
        Doc("a", "org.iso.18013.5.1.mDL", isPid = false),
        Doc("b", "eu.europa.ec.eudi.pid.1", isPid = true),
        Doc("c", "urn:uis:carnet", format = "dc+sd-jwt"),
    )

    private fun built() = MigrationObject.empty(100).syncWith(docs, 200)

    @Test
    fun roundTripKeepsEverything() {
        val obj = built()
        val back = MigrationBackup.import(MigrationBackup.export(obj, pw, fast), pw)
        assertEquals(obj, back)
        assertEquals(3, back.entries.size)
        assertEquals("cfg-b", back.entries[1].credentialConfigurationId)
    }

    @Test
    fun roundTripWithDefaultIterations() {
        val obj = built()
        assertEquals(obj, MigrationBackup.import(MigrationBackup.export(obj, pw), pw))
    }

    @Test
    fun wrongPasswordFails() {
        val data = MigrationBackup.export(built(), pw, fast)
        try {
            MigrationBackup.import(data, "otra".toCharArray())
            fail()
        } catch (_: MigrationDecryptionException) {
        }
    }

    @Test
    fun anyTamperedByteFails() {
        val data = MigrationBackup.export(built(), pw, fast)
        for (i in data.indices) {
            val bad = data.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            try {
                MigrationBackup.import(bad, pw)
                fail("byte $i alterado fue aceptado")
            } catch (_: MigrationDecryptionException) {
            } catch (_: MigrationFormatException) {
            }
        }
    }

    @Test
    fun truncatedOrForeignFileRejected() {
        val data = MigrationBackup.export(built(), pw, fast)
        for (bad in listOf(data.copyOf(data.size - 1), data.copyOf(10), ByteArray(0), ByteArray(80))) {
            try {
                MigrationBackup.import(bad, pw)
                fail()
            } catch (_: MigrationDecryptionException) {
            } catch (_: MigrationFormatException) {
            }
        }
    }

    @Test
    fun saltAndNonceAreRandom() {
        val a = MigrationBackup.export(built(), pw, fast)
        val b = MigrationBackup.export(built(), pw, fast)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun ciphertextDoesNotContainPlainText() {
        val data = MigrationBackup.export(built(), pw, fast)
        assertFalse(String(data, Charsets.ISO_8859_1).contains("issuer.example"))
    }

    @Test
    fun emptyPasswordRejected() {
        try {
            MigrationBackup.export(built(), CharArray(0), fast)
            fail()
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun jsonHasNoAttributesNorKeys() {
        val json = MigrationBackup.toJson(built()).lowercase()
        listOf("claim", "attribute", "family_name", "given_name", "birth", "privatekey", "private_key", "keyalias", "credentials")
            .forEach { assertFalse("no debe contener $it", json.contains(it)) }
        val fields = com.google.gson.JsonParser.parseString(json).asJsonObject["entries"].asJsonArray
            .flatMap { it.asJsonObject.keySet() }.toSet()
        assertEquals(
            setOf(
                "documentid", "attestationtype", "format", "ispid", "devicebound", "issueridentifier",
                "credentialissuerurl", "credentialconfigurationid", "issuerdisplayname", "documentdisplayname",
            ),
            fields.map { it.lowercase() }.toSet(),
        )
    }

    @Test
    fun deletingDocumentRemovesEntry() {
        val obj = built()
        val after = obj.withoutDocument("b", 300)
        assertEquals(listOf("a", "c"), after.entries.map { it.documentId })
        assertEquals(300, after.updatedAt)
        assertSame(obj, obj.withoutDocument("zzz", 400))
    }

    @Test
    fun syncDropsDeletedAndAddsNewKeepsCreatedAt() {
        val obj = built()
        val synced = obj.syncWith(listOf(docs[0], Doc("d")), 500)
        assertEquals(listOf("a", "d"), synced.entries.map { it.documentId })
        assertEquals(100, synced.createdAt)
        assertEquals(500, synced.updatedAt)
    }

    @Test
    fun syncWithoutChangesKeepsUpdatedAt() {
        val obj = built()
        assertEquals(200, obj.syncWith(docs, 999).updatedAt)
    }

    @Test
    fun restorePlanPutsPidFirstAndOnlySelected() {
        val plan = built().restorePlan(setOf("a", "b", "c"))
        assertEquals(listOf("b", "a", "c"), plan.map { it.documentId })
        assertEquals(listOf("c"), built().restorePlan(setOf("c", "nope")).map { it.documentId })
        assertTrue(built().restorePlan(emptySet()).isEmpty())
    }

    @Test
    fun credentialIssuerUrlDefaultsToIdentifier() {
        val e = MigrationEntry.from(Doc("x"))
        assertEquals("https://issuer.example/uis", e.credentialIssuerUrl)
    }

    @Test
    fun invalidJsonRejected() {
        for (j in listOf("", "null", "{", "{\"version\":99,\"entries\":[]}", "{\"version\":1}", "{\"version\":1,\"entries\":[{}]}")) {
            try {
                MigrationBackup.fromJson(j)
                fail(j)
            } catch (_: MigrationFormatException) {
            }
        }
    }

    @Test
    fun differentPasswordsGiveDifferentCiphertextKeys() {
        val a = MigrationCrypto.encrypt("x".toByteArray(), pw, fast)
        assertNotEquals(0, a.size)
        assertArrayEquals("x".toByteArray(), MigrationCrypto.decrypt(a, pw))
    }
}
