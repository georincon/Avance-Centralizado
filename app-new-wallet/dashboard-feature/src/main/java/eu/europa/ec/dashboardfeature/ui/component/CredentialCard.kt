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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ImageOrPlaceholder
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemSupportingContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.wrap.WrapAsyncImage
import eu.europa.ec.uilogic.component.wrap.WrapIcon

/**
 * Width:height ratio measured off a reference bank-card-style credential wallet screenshot
 * (~366x226 px for a single card), i.e. close to a physical payment card.
 */
internal const val CREDENTIAL_CARD_ASPECT_RATIO = 1.6f
private val CREDENTIAL_CARD_SHAPE = RoundedCornerShape(20.dp)
private val CREDENTIAL_CARD_PADDING = 20.dp

/**
 * Smaller than [CREDENTIAL_CARD_PADDING] on purpose: when this card is only "peeking" behind
 * another one in [CredentialStack], the visible strip is capped at a fixed height, so trimming
 * the header's top inset (and see the title's [MaterialTheme.typography.titleSmall] style below)
 * leaves enough room for the category/program-name line to stay inside that strip instead of
 * being cut off by the card stacked on top of it.
 */
private val CREDENTIAL_CARD_HEADER_TOP_PADDING = 12.dp
private val LEADING_LOGO_SIZE = 28.dp
private val BADGE_SIZE = 24.dp
private val PORTRAIT_SIZE = 66.dp
private const val PORTRAIT_CORNER_RADIUS = 10

/**
 * The issuer's emblem is drawn as a watermark on the right: how much of the card's height it
 * takes, and how faint it is, so it reads as a watermark and not as a second logo.
 */
private const val WATERMARK_HEIGHT_FRACTION = 0.92f
private const val WATERMARK_ALPHA = 0.22f

/**
 * A large, bank-card-style credential tile: a diagonal metallic gradient background (lighter at
 * the top-left, darker at the bottom-right) resolved via [toCredentialMetallicGradient] from the
 * document's type and its [positionIndex] among all the user's documents, with white
 * text/iconography on top, matching the wallet's existing typography. Reads its content from the
 * same [ListItemDataUi] already built for the documents list so no new fields are needed on
 * [eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentUi].
 *
 * The credential keeps its own colours (the gradient above); on top of them, the ISSUER's emblem
 * (the credential's logo, published by the issuer with a transparent background) is drawn large
 * and faint on the right, as the watermark the physical document carries. The holder's portrait,
 * when the credential carries one, is shown above the issuer's name, the way the physical
 * document shows its photo.
 */
@Composable
fun CredentialCard(
    item: ListItemDataUi,
    documentIdentifier: DocumentIdentifier,
    category: String,
    positionIndex: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    textColorHex: String? = null,
    watermarkUri: String? = null,
    portraitBase64: String? = null,
    licenseCard: eu.europa.ec.commonfeature.util.LicenseCardData? = null,
) {
    if (licenseCard != null) {
        LicenseCard(
            licenseCard,
            portraitBase64,
            (item.leadingContentData as? ListItemLeadingContentDataUi.AsyncImage)?.imageUrl,
            (item.mainContentData as? ListItemMainContentDataUi.Text)?.text.orEmpty(),
            category,
            onClick,
            modifier,
        )
        return
    }
    val (lightColor, darkColor) = documentIdentifier.toCredentialMetallicGradient(positionIndex)
    val issuerTextColor = textColorHex.toColorOrNull() ?: Color.White

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CREDENTIAL_CARD_ASPECT_RATIO)
            .clip(CREDENTIAL_CARD_SHAPE)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(lightColor, darkColor),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        // The issuer's emblem as a watermark behind the data.
        val watermarkUrl =
            watermarkUri?.takeIf { it.isNotBlank() }
                ?: (item.leadingContentData as? ListItemLeadingContentDataUi.AsyncImage)?.imageUrl
        watermarkUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { emblemUrl ->
                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    WrapAsyncImage(
                        source = emblemUrl,
                        modifier = Modifier
                            .fillMaxHeight(WATERMARK_HEIGHT_FRACTION)
                            .alpha(WATERMARK_ALPHA),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                    )
                }
            }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = CREDENTIAL_CARD_PADDING,
                    end = CREDENTIAL_CARD_PADDING,
                    top = CREDENTIAL_CARD_HEADER_TOP_PADDING,
                    bottom = CREDENTIAL_CARD_PADDING,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (val leading = item.leadingContentData) {
                    is ListItemLeadingContentDataUi.AsyncImage -> WrapAsyncImage(
                        source = leading.imageUrl,
                        modifier = Modifier
                            .size(LEADING_LOGO_SIZE)
                            .clip(RoundedCornerShape(6.dp)),
                        contentDescription = leading.contentDescription,
                        error = leading.errorImage,
                        placeholder = leading.placeholderImage,
                    )

                    is ListItemLeadingContentDataUi.Icon -> WrapIcon(
                        iconData = leading.iconData,
                        customTint = issuerTextColor,
                        modifier = Modifier.size(LEADING_LOGO_SIZE),
                    )

                    else -> {}
                }

                // e.g. "Academic Credential (SD-JWT VC Compact)" -> the "(...)" part always
                // starts its own line, instead of wrapping wherever it happens to run out of
                // width.
                val title = (item.mainContentData as? ListItemMainContentDataUi.Text)?.text
                    .orEmpty()
                    .replaceFirst(" (", "\n(")
                Column {
                    Text(
                        text = title,
                        color = issuerTextColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = category,
                        color = issuerTextColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                (item.trailingContentData as? ListItemTrailingContentDataUi.TextWithIcon)?.let { trailing ->
                    Text(
                        text = trailing.text,
                        color = issuerTextColor.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(BADGE_SIZE)
                        .clip(CircleShape)
                        .background(issuerTextColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    WrapIcon(
                        iconData = AppIcons.Verified,
                        customTint = issuerTextColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // The holder's photo, in the middle band of the card — vertically centered between the
        // credential's category ("Gobierno de Colombia") and the issuer's name at the bottom —
        // the way the physical document shows its photo.
        if (!portraitBase64.isNullOrBlank()) {
            ImageOrPlaceholder(
                modifier = Modifier
                    .size(PORTRAIT_SIZE)
                    .clip(RoundedCornerShape(PORTRAIT_CORNER_RADIUS.dp))
                    .border(
                        width = 2.dp,
                        color = issuerTextColor,
                        shape = RoundedCornerShape(PORTRAIT_CORNER_RADIUS.dp)
                    ),
                base64Image = portraitBase64,
                contentScale = ContentScale.Crop,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            item.overlineText?.let { overline ->
                Text(
                    text = overline,
                    color = issuerTextColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            (item.supportingContentData as? ListItemSupportingContentDataUi.Text)?.let { supporting ->
                Text(
                    text = supporting.text,
                    color = issuerTextColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
}

/**
 * Parses a color published by an issuer as a hex string (e.g. "#002D55") into a [Color], or null
 * when there is nothing to parse or the string is not a color at all.
 */
private fun String?.toColorOrNull(): Color? {
    val hex = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
}

/**
 * A driving licence drawn like the physical Colombian card: cream guilloche background, the
 * Republic's header, the data on the left and the holder's photo on the right.
 */
@Composable
private fun LicenseCard(
    data: eu.europa.ec.commonfeature.util.LicenseCardData,
    portraitBase64: String?,
    logoUrl: String?,
    docTitle: String,
    docCategory: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val ink = Color(0xFF2D2319)
    val label = Color(0xFF6B5A48)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CREDENTIAL_CARD_ASPECT_RATIO)
            .clip(CREDENTIAL_CARD_SHAPE)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFFBE9D6), Color(0xFFF1D2B5)),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        // El ícono del organismo, grande y tenue detrás de los datos (marca de agua).
        if (!logoUrl.isNullOrBlank()) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                WrapAsyncImage(
                    source = logoUrl,
                    // Más ancho que la tarjeta: el círculo desborda arriba y abajo y se recorta.
                    modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight(unbounded = true).alpha(0.16f),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
        // Tipo de documento, como en las credenciales de la Registraduría: nombre y categoría, arriba a la derecha.
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp).fillMaxWidth(0.34f),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = docTitle,
                color = ink,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            Text(
                text = "(mDL-ISO 18013-5)",
                color = ink,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            Text(
                text = docCategory,
                color = label,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!logoUrl.isNullOrBlank()) {
                    WrapAsyncImage(
                        source = logoUrl,
                        modifier = Modifier.size(46.dp),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                    )
                }
                Column(Modifier.fillMaxWidth(0.62f)) {
                Text("REPÚBLICA DE COLOMBIA", color = ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text("MINISTERIO DE TRANSPORTE", color = ink, style = MaterialTheme.typography.labelSmall)
                Text("LICENCIA DE CONDUCCIÓN", color = ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Text("No. ${data.number}", color = ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
            Column(Modifier.fillMaxWidth(0.62f)) {
                LicenseField("NOMBRE", data.name, label, ink)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    LicenseField("FECHA DE NACIMIENTO", data.birthDate, label, ink)
                    LicenseField("FECHA DE EXPEDICIÓN", data.issueDate, label, ink)
                }
                LicenseField("CATEGORÍAS", data.categories, label, ink)
                LicenseField("ORGANISMO DE TRÁNSITO EXPEDIDOR", data.authority, label, ink)
            }
        }
        if (!portraitBase64.isNullOrBlank()) {
            ImageOrPlaceholder(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 14.dp)
                    .size(width = 96.dp, height = 120.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, ink.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                base64Image = portraitBase64,
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun LicenseField(title: String, value: String, label: Color, ink: Color) {
    if (value.isBlank()) return
    Text(title, color = label, fontSize = 7.sp, maxLines = 1)
    Text(value, color = ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
