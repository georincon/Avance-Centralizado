/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the 'Licence'); You may not use this work
 * except in compliance with the Licence.
 *
 * You may have received a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * Licence is distributed on an 'AS IS' basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the Licence for the specific language governing permissions
 * and limitations under the Licence.
 */

package eu.europa.ec.uilogic.component.wrap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.SIZE_EXTRA_SMALL

/**
 * A filter pill: the rounded, single-choice filter of a list ("Todo | Compartido | Recibido").
 *
 * The selected pill is filled with the theme's primary colour; the others keep a light surface
 * with a thin outline. Unlike [WrapChip] this is a full pill (not a 8dp rounded rectangle) and
 * the selected state is not left to the Material defaults.
 */
@Composable
fun WrapFilterPill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val pillShape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .clip(pillShape)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = pillShape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = SPACING_MEDIUM.dp,
                vertical = (SPACING_SMALL + SIZE_EXTRA_SMALL).dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@ThemeModePreviews
@Composable
private fun WrapFilterPillPreview() {
    PreviewTheme {
        Box(modifier = Modifier.padding(SPACING_MEDIUM.dp)) {
            WrapFilterPill(
                text = "Todo",
                selected = true,
                onClick = {},
            )
        }
    }
}
