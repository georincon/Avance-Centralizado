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

import org.junit.Assert.assertEquals
import org.junit.Test

class TestCredentialColorSlots {

    @Test
    fun `Given no saved slots, When assigning, Then documents get consecutive slots after the reserved ones`() {
        val result = assignColorSlots(emptyMap(), listOf("a", "b", "c"), paletteSize = 10, reservedSlots = 2)
        assertEquals(mapOf("a" to 2, "b" to 3, "c" to 4), result)
    }

    @Test
    fun `Given a deleted document, When assigning, Then the others keep their slot and the new one takes the freed slot`() {
        val saved = mapOf("a" to 2, "b" to 3, "c" to 4)

        val afterDelete = assignColorSlots(saved, listOf("a", "c"), paletteSize = 10, reservedSlots = 2)
        assertEquals(mapOf("a" to 2, "c" to 4), afterDelete)

        val afterNew = assignColorSlots(afterDelete, listOf("a", "c", "d"), paletteSize = 10, reservedSlots = 2)
        assertEquals(mapOf("a" to 2, "c" to 4, "d" to 3), afterNew)
    }

    @Test
    fun `Given every slot taken, When assigning a new document, Then the least used slot is reused`() {
        val saved = mapOf("a" to 2, "b" to 3, "c" to 3)
        val result = assignColorSlots(saved, listOf("a", "b", "c", "d"), paletteSize = 4, reservedSlots = 2)
        assertEquals(2, result["d"])
        assertEquals(mapOf("a" to 2, "b" to 3, "c" to 3, "d" to 2), result)
    }

    @Test
    fun `Given slots, When encoding and decoding, Then they round trip`() {
        val slots = mapOf("id-1" to 2, "id-2" to 7)
        assertEquals(slots, slots.encodeColorSlots().decodeColorSlots())
        assertEquals(emptyMap<String, Int>(), "".decodeColorSlots())
    }
}
