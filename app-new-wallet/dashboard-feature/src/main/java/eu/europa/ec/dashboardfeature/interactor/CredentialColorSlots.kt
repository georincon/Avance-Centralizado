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

import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.rankedByCategory
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.dashboardfeature.ui.component.CREDENTIAL_PALETTE_SIZE
import eu.europa.ec.dashboardfeature.ui.component.CREDENTIAL_RESERVED_SLOTS

private const val PAIR_DELIMITER = ","
private const val SLOT_DELIMITER = ":"

/**
 * Gives every credential a color slot that it keeps until it is deleted.
 *
 * The slots are saved in [PrefKeys]: a credential without one takes the lowest slot no other
 * credential uses (the palette is reused, least-used first, only once all are taken), and the slot
 * of a deleted credential is dropped so the next credential can take its color. Both the list and
 * the detail screen read slots from here, so a credential has the same color on both.
 *
 * The PID variants have fixed colors and are not part of the slots.
 *
 * @return document id -> color slot, for every non-PID document.
 */
suspend fun PrefKeys.allocateCredentialColorSlots(
    walletCoreDocumentsController: WalletCoreDocumentsController,
): Map<String, Int> {
    val documents = walletCoreDocumentsController.getAllDocuments()
    // An empty list can be a transient state: don't forget everyone's color because of it.
    if (documents.isEmpty()) return emptyMap()

    val orderedIds = documents
        .rankedByCategory(walletCoreDocumentsController.getAllDocumentCategories())
        .filterNot {
            val identifier = it.toDocumentIdentifier()
            identifier is DocumentIdentifier.MdocPid || identifier is DocumentIdentifier.SdJwtPid
        }
        .map { it.id }

    val saved = getCredentialColorSlots().decodeColorSlots()
    val allocated = assignColorSlots(saved = saved, orderedIds = orderedIds)
    if (allocated != saved) {
        setCredentialColorSlots(allocated.encodeColorSlots())
    }
    return allocated
}

internal fun assignColorSlots(
    saved: Map<String, Int>,
    orderedIds: List<String>,
    paletteSize: Int = CREDENTIAL_PALETTE_SIZE,
    reservedSlots: Int = CREDENTIAL_RESERVED_SLOTS,
): Map<String, Int> {
    val slotRange = reservedSlots until paletteSize
    val current = orderedIds.toSet()
    val result = saved
        .filter { (id, slot) -> id in current && slot in slotRange }
        .toMutableMap()

    orderedIds.filterNot { it in result }.forEach { id ->
        val usage = result.values.groupingBy { it }.eachCount()
        // minByOrNull keeps the first minimum: the lowest free slot, or the least used one when
        // every slot is taken.
        result[id] = slotRange.minByOrNull { usage[it] ?: 0 } ?: reservedSlots
    }
    return result
}

internal fun String.decodeColorSlots(): Map<String, Int> =
    split(PAIR_DELIMITER)
        .mapNotNull { pair ->
            val id = pair.substringBeforeLast(SLOT_DELIMITER, "")
            val slot = pair.substringAfterLast(SLOT_DELIMITER, "").toIntOrNull()
            if (id.isBlank() || slot == null) null else id to slot
        }
        .toMap()

internal fun Map<String, Int>.encodeColorSlots(): String =
    entries.joinToString(PAIR_DELIMITER) { (id, slot) -> "$id$SLOT_DELIMITER$slot" }
