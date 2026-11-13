/*
 * Copyright (c) 2023-2026 European Commission
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
package eu.europa.ec.eudi.pidissuer.adapter.out.status

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkStatusAsRevokedWithExternalServiceTest {
    private fun path(uri: String) = MarkStatusAsRevokedWithExternalService.statusListServiceUri(uri)

    @Test
    fun `drops the public proxy prefix so the service can find the list on disk`() {
        assertEquals(
            "/token_status_list/FC/urn:eudi:academic:credential:1/9a16ce13-7fff-432d-a399-54c5f2c371b5",
            path(
                "https://civica-desarrollo.avance.org.co/status-list/uis-issuer/token_status_list/FC/" +
                    "urn:eudi:academic:credential:1/9a16ce13-7fff-432d-a399-54c5f2c371b5",
            ),
        )
        assertEquals(
            "/token_status_list/FC/urn:eudi:pid:1/abc",
            path("https://civica-desarrollo.avance.org.co/status-list/pid-transito/token_status_list/FC/urn:eudi:pid:1/abc"),
        )
    }

    @Test
    fun `a uri without a proxy prefix is reduced to the same path`() {
        assertEquals("/token_status_list/FC/urn:eudi:pid:1/abc", path("https://status.example.com/token_status_list/FC/urn:eudi:pid:1/abc"))
    }

    @Test
    fun `a uri without the token_status_list segment is left unchanged`() {
        assertEquals("https://example.com/other/path", path("https://example.com/other/path"))
    }
}
