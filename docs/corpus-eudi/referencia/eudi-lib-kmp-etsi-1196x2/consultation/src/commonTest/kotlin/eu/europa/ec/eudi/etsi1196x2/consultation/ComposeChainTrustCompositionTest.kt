/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.etsi1196x2.consultation

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ComposeChainTrustCompositionTest {

    private fun mockSource(value: String): GetTrustAnchors<String, String> = GetTrustAnchors { _ ->
        NonEmptyList(listOf(value))
    }

    private fun <C : Any, T : Any> mockChainValidator() = ValidateCertificateChain<C, T> { _, _ -> error("not used") }

    @Test
    fun testBasicInvoke() = runTest {
        val queries = setOf("q1", "q2")
        val source = mockSource("v1")
        val validator =
            source.validator(queries, mockChainValidator())

        val result1 = validator.getTrustAnchors("q1")
        assertNotNull(result1)
        assertEquals(listOf("v1"), result1.list)

        val result3 = validator.getTrustAnchors("q3")
        assertNull(result3)
    }

    @Test
    fun testPlus() = runTest {
        val s1 =
            mockSource("v1").validator(setOf("q1"), mockChainValidator())
        val s2 =
            mockSource("v2").validator(setOf("q2"), mockChainValidator())
        val combined =
            ComposeChainTrust.empty<Any, String, String>() + s1 + s2

        val r1 = combined.getTrustAnchors("q1")
        assertNotNull(r1)
        assertEquals(listOf("v1"), r1.list)

        val r2 = combined.getTrustAnchors("q2")
        assertNotNull(r2)
        assertEquals(listOf("v2"), r2.list)
    }

    @Test
    fun testPlusOverlapInvariant() {
        val s1 =
            mockSource("v1").validator(setOf("q1"), mockChainValidator())
        val s2 =
            mockSource("v2").validator(setOf("q1"), mockChainValidator())

        assertFailsWith<IllegalArgumentException> {
            s1 plus s2
        }
    }

    @Test
    fun testNotFound() = runTest {
        // A source that claims to support a query but returns null
        val source = GetTrustAnchors<String, String> { _ -> null }
        val validator =
            source.validator(setOf("q1"), mockChainValidator())

        val result = validator.getTrustAnchors("q1")
        assertNull(result)
    }
}
