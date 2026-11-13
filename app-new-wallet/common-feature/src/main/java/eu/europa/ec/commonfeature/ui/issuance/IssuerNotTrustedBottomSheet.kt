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

package eu.europa.ec.commonfeature.ui.issuance

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.warning
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.wrap.BottomSheetTextDataUi
import eu.europa.ec.uilogic.component.wrap.DialogBottomSheet

@Composable
fun IssuerNotTrustedSheetContent(
    onClose: () -> Unit,
) {
    DialogBottomSheet(
        textData = BottomSheetTextDataUi(
            title = stringResource(id = R.string.issuance_blocked_bottom_sheet_title),
            message = stringResource(id = R.string.issuance_blocked_bottom_sheet_message),
            positiveButtonText = stringResource(id = R.string.issuance_blocked_bottom_sheet_primary_button_text),
        ),
        leadingIcon = AppIcons.Warning,
        leadingIconTint = MaterialTheme.colorScheme.warning,
        onPositiveClick = onClose,
    )
}

@ThemeModePreviews
@Composable
private fun IssuerNotTrustedSheetContentPreview() {
    PreviewTheme {
        IssuerNotTrustedSheetContent(
            onClose = {},
        )
    }
}