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

package eu.europa.ec.dashboardfeature.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentUi
import kotlin.math.roundToInt

/**
 * How far each card behind the front one peeks out above it, i.e. how much of a covered card's
 * header block (logo + title + category, see [CredentialCard]) stays visible. Measured against
 * [CredentialCard]'s 20dp padding plus its title/category text block, with a small margin.
 *
 * Also the "slot height" used while dragging a card: crossing half of this distance past a
 * neighboring card swaps the two, exactly like reordering a list one slot at a time.
 */
private val STACK_PEEK_HEIGHT = 72.dp

/**
 * The "collapsed" (recogida) credential layout: full-size [CredentialCard]s stacked on top of one
 * another like a single deck, each offset down by [STACK_PEEK_HEIGHT] so only the header strip of
 * every card behind the front one stays visible. The frontmost (last) card keeps its full original
 * size and shows all of its content, matching the reference wallet screenshot.
 *
 * Takes the FULL, flattened credential list (every category combined) so every credential —
 * regardless of category — is part of the same deck with the exact same peek spacing, and any
 * newly added credential simply becomes the new frontmost card instead of starting a separate,
 * visually detached stack.
 *
 * Every card — whether fully visible or only peeking — keeps the exact same [onItemClick]
 * navigation as the extended view: Compose only routes a tap to the topmost composable actually
 * drawn at that point, so a tap on a peeking card's visible strip naturally reaches that card's
 * own `clickable`, with no custom hit-testing needed.
 *
 * Any card can be long-pressed and then dragged up or down to ANY position in the deck, not just
 * the front (a plain drag instead scrolls the list, so the deck never traps the scroll when its
 * cards fill the screen): as it's
 * dragged past the midpoint of a neighboring card, the two swap places right away (calling
 * [onReorder] with the list in its new order) and the card keeps moving under the finger from
 * there, exactly like reordering a list one slot at a time. Releasing leaves it wherever it
 * currently is; every other (non-dragged) card animates smoothly into its new resting slot as it
 * gets displaced.
 *
 * Each card is wrapped in [key] by its document id: without it, Compose would match each loop
 * iteration's remembered state (the drag-settle animation in particular) to its POSITION rather
 * than to the actual credential there, so after a swap the wrong card would inherit the wrong
 * animation state — visually "stuck" on top instead of settling behind its new neighbor. Keying
 * by id keeps every card's own state (and stacking order) correctly attached to it as it moves.
 */
@Composable
fun CredentialStack(
    documents: List<DocumentUi>,
    onItemClick: (DocumentUi) -> Unit,
    onReorder: (List<DocumentUi>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (documents.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardHeight = maxWidth / CREDENTIAL_CARD_ASPECT_RATIO
        val stackHeight = cardHeight + STACK_PEEK_HEIGHT * (documents.size - 1)
        val density = LocalDensity.current
        val peekHeightPx = with(density) { STACK_PEEK_HEIGHT.toPx() }

        // Only one card can ever be dragged at a time; tracked here (rather than per-card) since
        // reordering one card also shifts every other card's resting slot.
        var draggedDocumentId by remember { mutableStateOf<String?>(null) }
        var dragOffsetPx by remember { mutableFloatStateOf(0f) }
        val currentDocuments by rememberUpdatedState(documents)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stackHeight)
        ) {
            documents.forEachIndexed { index, document ->
                key(document.uiData.itemId) {
                    val isBeingDragged = document.uiData.itemId == draggedDocumentId

                    // The dragged card's own base offset must snap instantly to its new slot (the
                    // drag delta below is already compensated to stay visually continuous across a
                    // swap); every OTHER, displaced card instead animates smoothly into its new slot.
                    val baseOffsetY = STACK_PEEK_HEIGHT * index
                    val animatedBaseOffsetY: Dp by animateDpAsState(
                        targetValue = baseOffsetY,
                        label = "credentialCardBaseOffset",
                    )
                    val effectiveBaseOffsetY = if (isBeingDragged) baseOffsetY else animatedBaseOffsetY

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isBeingDragged) documents.size.toFloat() else index.toFloat())
                            .offset(y = effectiveBaseOffsetY)
                            .offset {
                                IntOffset(x = 0, y = if (isBeingDragged) dragOffsetPx.roundToInt() else 0)
                            }
                            .graphicsLayer {
                                val scale = if (isBeingDragged) 1.03f else 1f
                                scaleX = scale
                                scaleY = scale
                            }
                            .pointerInput(document.uiData.itemId) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedDocumentId = document.uiData.itemId
                                        dragOffsetPx = 0f
                                    },
                                    onDragEnd = {
                                        draggedDocumentId = null
                                        dragOffsetPx = 0f
                                    },
                                    onDragCancel = {
                                        draggedDocumentId = null
                                        dragOffsetPx = 0f
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    dragOffsetPx += dragAmount.y

                                    val docs = currentDocuments
                                    val currentIndex =
                                        docs.indexOfFirst { it.uiData.itemId == document.uiData.itemId }
                                    if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                                    val slotsMoved = (dragOffsetPx / peekHeightPx).roundToInt()
                                    val targetIndex = (currentIndex + slotsMoved)
                                        .coerceIn(0, docs.lastIndex)

                                    if (targetIndex != currentIndex) {
                                        val reordered = docs.toMutableList().apply {
                                            add(targetIndex, removeAt(currentIndex))
                                        }
                                        dragOffsetPx -= (targetIndex - currentIndex) * peekHeightPx
                                        onReorder(reordered)
                                    }
                                }
                            }
                    ) {
                        CredentialCard(
                            modifier = Modifier.fillMaxWidth(),
                            item = document.uiData,
                            documentIdentifier = document.documentIdentifier,
                            category = document.categoryOverride
                                ?: stringResource(document.documentCategory.stringResId),
                            positionIndex = document.colorIndex,
                            onClick = { onItemClick(document) },
                            textColorHex = document.textColorHex,
                            watermarkUri = document.watermarkUri,
                            portraitBase64 = document.portraitBase64,
                            licenseCard = document.licenseCard,
                        )
                    }
                }
            }
        }
    }
}
