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

package eu.europa.ec.issuancefeature.util

import eu.europa.ec.corelogic.model.ClaimItemId
import eu.europa.ec.corelogic.model.ClaimPathDomain
import eu.europa.ec.corelogic.model.ClaimPathDomain.Companion.toClaimPathDomain
import eu.europa.ec.corelogic.model.ClaimPathSegment
import eu.europa.ec.corelogic.model.ClaimType
import eu.europa.ec.corelogic.model.ScopedDocumentDomain
import eu.europa.ec.eudi.openid4vci.TxCode
import eu.europa.ec.eudi.openid4vci.TxCodeInputMode
import eu.europa.ec.eudi.wallet.document.NameSpace
import eu.europa.ec.issuancefeature.ui.add.model.AddDocumentUi
import eu.europa.ec.testfeature.util.mockedAgeVerificationDocName
import eu.europa.ec.testfeature.util.mockedMdlDocName
import eu.europa.ec.testfeature.util.mockedMdocAgeVerificationFormat
import eu.europa.ec.testfeature.util.mockedMdocMdlFormat
import eu.europa.ec.testfeature.util.mockedMdocPhotoIdFormat
import eu.europa.ec.testfeature.util.mockedMdocPidFormat
import eu.europa.ec.testfeature.util.mockedMdocPidNameSpace
import eu.europa.ec.testfeature.util.mockedPhotoIdDocName
import eu.europa.ec.testfeature.util.mockedPidDocName
import eu.europa.ec.testfeature.util.mockedPidId
import eu.europa.ec.testfeature.util.mockedSdJwtPidId
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.wrap.ExpandableListItemUi
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens

internal const val mockedOfferedDocumentName = "Offered Document"
internal const val mockedOfferedDocumentDocType = "mocked_offered_document_doc_type"
internal const val mockedTxCodeFourDigits = 4
internal const val mockedSuccessContentDescription = "Content description"
internal const val mockedIssuanceErrorMessage = "Issuance error message"
internal const val mockedInvalidCodeFormatMessage = "Invalid code format message"
internal const val mockedWalletActivationErrorMessage = "Wallet activation error message"
internal const val mockedPrimaryButtonText = "Primary button text"
internal const val mockedRouteArguments = "mockedRouteArguments"
internal const val mockedTxCode = "mockedTxCode"
internal const val mockedSuccessText = "Success text"
internal const val mockedCombinedPid = "PID Combined"
internal const val mockedSuccessDescription = "Success description"
internal const val mockedErrorDescription = "Error description"
internal const val mockedIssuerId = "issuerId"
internal const val mockedIssuerOrder = 0

private const val mockedConfigIssuerId = "configurationId"

internal val mockedPidOptionItemUi = AddDocumentUi(
    credentialIssuerId = mockedIssuerId,
    configurationIds = listOf(mockedConfigIssuerId),
    itemData = ListItemDataUi(
        itemId = "${mockedIssuerId}_$mockedConfigIssuerId",
        mainContentData = ListItemMainContentDataUi.Text(text = mockedCombinedPid),
        trailingContentData = ListItemTrailingContentDataUi.Icon(iconData = AppIcons.Add)
    ),
)

internal val mockedMdlOptionItemUi = AddDocumentUi(
    credentialIssuerId = mockedIssuerId,
    configurationIds = listOf(mockedConfigIssuerId),
    itemData = ListItemDataUi(
        itemId = mockedConfigIssuerId,
        mainContentData = ListItemMainContentDataUi.Text(text = mockedMdlDocName),
        trailingContentData = ListItemTrailingContentDataUi.Icon(iconData = AppIcons.Add)
    ),
)

internal val mockedAgeOptionItemUi = AddDocumentUi(
    credentialIssuerId = mockedIssuerId,
    configurationIds = listOf(mockedConfigIssuerId),
    itemData = ListItemDataUi(
        itemId = mockedConfigIssuerId,
        mainContentData = ListItemMainContentDataUi.Text(text = mockedAgeVerificationDocName),
        trailingContentData = ListItemTrailingContentDataUi.Icon(iconData = AppIcons.Add)
    ),
)

internal val mockedPhotoIdOptionItemUi = AddDocumentUi(
    credentialIssuerId = mockedIssuerId,
    configurationIds = listOf(mockedConfigIssuerId),
    itemData = ListItemDataUi(
        itemId = mockedConfigIssuerId,
        mainContentData = ListItemMainContentDataUi.Text(text = mockedPhotoIdDocName),
        trailingContentData = ListItemTrailingContentDataUi.Icon(iconData = AppIcons.Add)
    ),
)

internal val mockedScopedDocuments: List<ScopedDocumentDomain>
    get() = listOf(
        ScopedDocumentDomain(
            name = mockedPidDocName,
            configurationId = mockedConfigIssuerId,
            credentialIssuerId = mockedIssuerId,
            credentialIssuerOrder = mockedIssuerOrder,
            isPid = true,
            formatType = mockedMdocPidFormat.docType
        ),
        ScopedDocumentDomain(
            name = mockedMdlDocName,
            configurationId = mockedConfigIssuerId,
            credentialIssuerId = mockedIssuerId,
            credentialIssuerOrder = mockedIssuerOrder,
            isPid = false,
            formatType = mockedMdocMdlFormat.docType
        ),
        ScopedDocumentDomain(
            name = mockedAgeVerificationDocName,
            configurationId = mockedConfigIssuerId,
            credentialIssuerId = mockedIssuerId,
            credentialIssuerOrder = mockedIssuerOrder,
            isPid = false,
            formatType = mockedMdocAgeVerificationFormat.docType
        ),
        ScopedDocumentDomain(
            name = mockedPhotoIdDocName,
            configurationId = mockedConfigIssuerId,
            credentialIssuerId = mockedIssuerId,
            isPid = false,
            credentialIssuerOrder = mockedIssuerOrder,
            formatType = mockedMdocPhotoIdFormat.docType
        )
    ).sortedBy { it.name.lowercase() }

internal val mockedOfferTxCodeFourDigits = TxCode(
    inputMode = TxCodeInputMode.NUMERIC,
    length = mockedTxCodeFourDigits
)

internal val mockedConfigNavigationTypePop = ConfigNavigation(navigationType = NavigationType.Pop)
internal val mockedConfigNavigationTypePush = ConfigNavigation(
    navigationType = NavigationType.PushRoute(
        route = DashboardScreens.Dashboard.screenRoute,
        popUpToRoute = IssuanceScreens.AddDocument.screenRoute
    )
)
internal val mockedConfigNavigationTypePopToScreen = ConfigNavigation(
    navigationType = NavigationType.PopTo(
        screen = DashboardScreens.Dashboard
    )
)

internal val mockedMdocPidClaims = listOf(
    createMdocClaimListItem(mockedPidId, mockedMdocPidNameSpace, "family_name", "ANDERSSON", "Apellido(s)"),
    createMdocClaimListItem(mockedPidId, mockedMdocPidNameSpace, "age_birth_year", "1985", "Año de nacimiento"),
    createMdocClaimListItem(mockedPidId, mockedMdocPidNameSpace, "birth_city", "KATRINEHOLM", "Ciudad de nacimiento"),
    createMdocClaimListItem(mockedPidId, mockedMdocPidNameSpace, "expiry_date", "30 Mar 2050", "Fecha de caducidad"),
    createMdocClaimListItem(mockedPidId, mockedMdocPidNameSpace, "age_over_18", "yes", "Mayor de 18 años"),
    createMdocClaimListItem(mockedPidId, mockedMdocPidNameSpace, "age_over_65", "no", "Mayor de 65 años"),
    createMdocClaimListItem(mockedPidId, mockedMdocPidNameSpace, "given_name", "JAN", "Nombre(s)"),
    createMdocClaimListItem(mockedPidId, mockedMdocPidNameSpace, "gender", "Male", "Sexo"),
)

internal val mockedSdJwtPidClaims = listOf(
    ExpandableListItemUi.NestedListItem(
        header = ListItemDataUi(
            itemId = sdJwtItemId(mockedSdJwtPidId, ClaimPathSegment.Key("age_equal_or_over")),
            mainContentData = ListItemMainContentDataUi.Text("age_equal_or_over"),
            trailingContentData = ListItemTrailingContentDataUi.Icon(
                iconData = AppIcons.KeyboardArrowDown
            )
        ),
        nestedItems = listOf(
            ExpandableListItemUi.SingleListItem(
                header = ListItemDataUi(
                    itemId = sdJwtItemId(
                        mockedSdJwtPidId,
                        ClaimPathSegment.Key("age_equal_or_over"),
                        ClaimPathSegment.Key("18"),
                    ),
                    overlineText = "18",
                    mainContentData = ListItemMainContentDataUi.Text("true")
                )
            ),
            ExpandableListItemUi.SingleListItem(
                header = ListItemDataUi(
                    itemId = sdJwtItemId(
                        mockedSdJwtPidId,
                        ClaimPathSegment.Key("age_equal_or_over"),
                        ClaimPathSegment.Key("65"),
                    ),
                    overlineText = "65",
                    mainContentData = ListItemMainContentDataUi.Text("unset")
                )
            )
        ),
        isExpanded = false
    ),
    ExpandableListItemUi.SingleListItem(
        header = ListItemDataUi(
            itemId = sdJwtItemId(mockedSdJwtPidId, ClaimPathSegment.Key("family_name")),
            overlineText = "Apellido(s)",
            mainContentData = ListItemMainContentDataUi.Text("ANDERSSON")
        )
    ),
    ExpandableListItemUi.SingleListItem(
        header = ListItemDataUi(
            itemId = sdJwtItemId(mockedSdJwtPidId, ClaimPathSegment.Key("issuing_authority")),
            overlineText = "Autoridad emisora",
            mainContentData = ListItemMainContentDataUi.Text(text = "Test PID issuer")
        )
    ),
    ExpandableListItemUi.SingleListItem(
        header = ListItemDataUi(
            itemId = sdJwtItemId(mockedSdJwtPidId, ClaimPathSegment.Key("age_birth_year")),
            overlineText = "Año de nacimiento",
            mainContentData = ListItemMainContentDataUi.Text("1985")
        )
    ),
    ExpandableListItemUi.SingleListItem(
        header = ListItemDataUi(
            itemId = sdJwtItemId(mockedSdJwtPidId, ClaimPathSegment.Key("birth_date")),
            overlineText = "Fecha de nacimiento",
            mainContentData = ListItemMainContentDataUi.Text("30 Mar 1985")
        )
    ),
    ExpandableListItemUi.NestedListItem(
        header = ListItemDataUi(
            itemId = sdJwtItemId(mockedSdJwtPidId, ClaimPathSegment.Key("place_of_birth")),
            overlineText = null,
            mainContentData = ListItemMainContentDataUi.Text("Lugar de nacimiento"),
            trailingContentData = ListItemTrailingContentDataUi.Icon(
                iconData = AppIcons.KeyboardArrowDown
            )
        ),
        nestedItems = listOf(
            ExpandableListItemUi.SingleListItem(
                header = ListItemDataUi(
                    itemId = sdJwtItemId(
                        mockedSdJwtPidId,
                        ClaimPathSegment.Key("place_of_birth"),
                        ClaimPathSegment.Key("locality"),
                    ),
                    overlineText = "Localidad",
                    mainContentData = ListItemMainContentDataUi.Text("KATRINEHOLM")
                )
            )
        ),
        isExpanded = false
    ),
    ExpandableListItemUi.NestedListItem(
        header = ListItemDataUi(
            itemId = sdJwtItemId(mockedSdJwtPidId, ClaimPathSegment.Key("nationalities")),
            overlineText = null,
            mainContentData = ListItemMainContentDataUi.Text("Nacionalidad(es)"),
            trailingContentData = ListItemTrailingContentDataUi.Icon(
                iconData = AppIcons.KeyboardArrowDown
            )
        ),
        nestedItems = listOf(
            ExpandableListItemUi.SingleListItem(
                header = ListItemDataUi(
                    // The element is addressed by its typed array index; its overline is the
                    // index ("0"), not the array name.
                    itemId = sdJwtItemId(
                        mockedSdJwtPidId,
                        ClaimPathSegment.Key("nationalities"),
                        ClaimPathSegment.Index(0),
                    ),
                    overlineText = "0",
                    mainContentData = ListItemMainContentDataUi.Text("SE")
                )
            )
        ),
        isExpanded = false
    ),
    ExpandableListItemUi.SingleListItem(
        header = ListItemDataUi(
            itemId = sdJwtItemId(mockedSdJwtPidId, ClaimPathSegment.Key("given_name")),
            overlineText = "Nombre(s)",
            mainContentData = ListItemMainContentDataUi.Text("JAN")
        )
    ),
    ExpandableListItemUi.SingleListItem(
        header = ListItemDataUi(
            itemId = sdJwtItemId(mockedSdJwtPidId, ClaimPathSegment.Key("issuing_country")),
            overlineText = "País emisor",
            mainContentData = ListItemMainContentDataUi.Text("FC")
        )
    ),
)

private fun createMdocClaimListItem(
    docId: String,
    nameSpace: NameSpace,
    claimIdentifier: String,
    value: String,
    displayTitle: String = claimIdentifier,
): ExpandableListItemUi.SingleListItem {
    return ExpandableListItemUi.SingleListItem(
        header = ListItemDataUi(
            itemId = ClaimItemId.Claim(
                docId = docId,
                queryId = null,
                path = ClaimPathDomain.ofPlainKeys(
                    names = listOf(claimIdentifier),
                    type = ClaimType.MsoMdoc(namespace = nameSpace)
                ),
            ).encode(),
            overlineText = displayTitle,
            mainContentData = ListItemMainContentDataUi.Text(value)
        )
    )
}

/**
 * Builds the request-screen item id for an SD-JWT VC claim leaf/group the way production does:
 * encoding the typed claim path with [ClaimItemId.encode] (queryId is null on the
 * issuance-success / detail screens). Computing it here, rather than hard-coding the encoded
 * string, keeps it correct if the id encoding evolves.
 */
private fun sdJwtItemId(docId: String, vararg segments: ClaimPathSegment): String =
    ClaimItemId.Claim(
        docId = docId,
        queryId = null,
        path = segments.toList().toClaimPathDomain(ClaimType.SdJwtVc),
    ).encode()