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

import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.extension.getLocalizedValue
import eu.europa.ec.businesslogic.extension.isBeyondNextDays
import eu.europa.ec.businesslogic.extension.isExpired
import eu.europa.ec.businesslogic.extension.isValid
import eu.europa.ec.businesslogic.extension.isWithinNextDays
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.util.formatInstant
import eu.europa.ec.businesslogic.validator.FilterValidator
import eu.europa.ec.businesslogic.validator.FilterValidatorPartialState
import eu.europa.ec.businesslogic.validator.model.FilterAction
import eu.europa.ec.businesslogic.validator.model.FilterElement.FilterItem
import eu.europa.ec.businesslogic.validator.model.FilterGroup
import eu.europa.ec.businesslogic.validator.model.FilterMultipleAction
import eu.europa.ec.businesslogic.validator.model.FilterSort
import eu.europa.ec.businesslogic.validator.model.FilterableItem
import eu.europa.ec.businesslogic.validator.model.FilterableList
import eu.europa.ec.businesslogic.validator.model.Filters
import eu.europa.ec.businesslogic.validator.model.SortOrder
import eu.europa.ec.commonfeature.util.extractLicenseCardData
import eu.europa.ec.commonfeature.util.extractPortraitBase64
import eu.europa.ec.commonfeature.util.extractValueFromDocumentOrEmpty
import eu.europa.ec.corelogic.controller.DeleteDocumentPartialState
import eu.europa.ec.corelogic.controller.IssueDeferredDocumentPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.extension.getExpiryDate
import eu.europa.ec.corelogic.extension.isExpired
import eu.europa.ec.corelogic.extension.localizedDocumentDisplay
import eu.europa.ec.corelogic.extension.localizedIssuerMetadata
import eu.europa.ec.corelogic.model.DeferredDocumentDataDomain
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.model.FormatType
import eu.europa.ec.corelogic.model.toDocumentCategory
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.dashboardfeature.ui.documents.detail.model.DocumentIssuanceStateUi
import eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentFilterIds
import eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentUi
import eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentsFilterableAttributes
import eu.europa.ec.dashboardfeature.ui.documents.model.DocumentCredentialsInfoUi
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.eudi.openid4vci.CredentialIssuerMetadata
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.UnsignedDocument
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemSupportingContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.ThemeColorKey
import eu.europa.ec.uilogic.component.wrap.CheckboxDataUi
import eu.europa.ec.uilogic.component.wrap.ExpandableListItemUi
import eu.europa.ec.uilogic.component.wrap.RadioButtonDataUi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

sealed class DocumentInteractorFilterPartialState {
    data class FilterApplyResult(
        val documents: List<DocumentUi>,
        val filters: List<ExpandableListItemUi.NestedListItem>,
        val allDefaultFiltersAreSelected: Boolean,
    ) : DocumentInteractorFilterPartialState()

    data class FilterUpdateResult(
        val filters: List<ExpandableListItemUi.NestedListItem>,
    ) : DocumentInteractorFilterPartialState()
}

sealed class DocumentInteractorGetDocumentsPartialState {
    data class Success(
        val allDocuments: FilterableList,
        val shouldAllowUserInteraction: Boolean,
    ) : DocumentInteractorGetDocumentsPartialState()

    data class Failure(val error: String) : DocumentInteractorGetDocumentsPartialState()
}

sealed class DocumentInteractorDeleteDocumentPartialState {
    data object SingleDocumentDeleted : DocumentInteractorDeleteDocumentPartialState()
    data object AllDocumentsDeleted : DocumentInteractorDeleteDocumentPartialState()
    data class Failure(val errorMessage: String) :
        DocumentInteractorDeleteDocumentPartialState()
}

sealed class DocumentInteractorRetryIssuingDeferredDocumentPartialState {
    data class Success(
        val deferredDocumentData: DeferredDocumentDataDomain,
    ) : DocumentInteractorRetryIssuingDeferredDocumentPartialState()

    data class NotReady(
        val deferredDocumentData: DeferredDocumentDataDomain,
    ) : DocumentInteractorRetryIssuingDeferredDocumentPartialState()

    data class Failure(
        val documentId: DocumentId,
        val errorMessage: String,
    ) : DocumentInteractorRetryIssuingDeferredDocumentPartialState()

    data class Expired(
        val documentId: DocumentId,
    ) : DocumentInteractorRetryIssuingDeferredDocumentPartialState()

    data class IssuerNotTrusted(
        val documentId: DocumentId,
    ) : DocumentInteractorRetryIssuingDeferredDocumentPartialState()
}

sealed class DocumentInteractorRetryIssuingDeferredDocumentsPartialState {
    data class Result(
        val successfullyIssuedDeferredDocuments: List<DeferredDocumentDataDomain>,
        val failedIssuedDeferredDocuments: List<DocumentId>,
    ) : DocumentInteractorRetryIssuingDeferredDocumentsPartialState()

    data class Failure(
        val errorMessage: String,
    ) : DocumentInteractorRetryIssuingDeferredDocumentsPartialState()
}

interface DocumentsInteractor {
    fun getDocuments(): Flow<DocumentInteractorGetDocumentsPartialState>

    fun tryIssuingDeferredDocumentsFlow(
        deferredDocuments: Map<DocumentId, FormatType>,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Flow<DocumentInteractorRetryIssuingDeferredDocumentsPartialState>

    fun deleteDocument(
        documentId: String,
    ): Flow<DocumentInteractorDeleteDocumentPartialState>

    fun onFilterStateChange(): Flow<DocumentInteractorFilterPartialState>

    /**
     * Persists the user's manually-chosen credential display order (front-to-back / top-to-bottom),
     * so it survives future app opens. Pass the full, currently-displayed list of document ids in
     * their new order.
     */
    suspend fun saveCredentialOrder(orderedDocumentIds: List<String>)
    fun initializeFilters(
        filterableList: FilterableList,
    )

    fun updateLists(filterableList: FilterableList)
    fun applyFilters()
    fun applySearch(query: String)
    fun resetFilters()
    fun revertFilters()
    fun updateFilter(filterGroupId: String, filterId: String)
    fun updateSort(filterId: String)
    fun addDynamicFilters(
        documents: FilterableList,
        filters: Filters = Filters.emptyFilters(),
    ): Filters

    fun getFilters(): Filters
}

class DocumentsInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val filterValidator: FilterValidator,
    private val configLogic: ConfigLogic,
    private val prefKeys: PrefKeys,
) : DocumentsInteractor {

    /**
     * Claim keys tried, in order, to find a document's academic program/degree name so it can
     * be shown on its credential card instead of the generic [DocumentCategory.Other] label
     * ("OTROS"). Best-effort: covers common English/Spanish naming across academic credential
     * schemas since there is no single standardized claim name for this.
     */
    private val academicProgramClaimKeys = listOf(
        "program", "program_name", "programName",
        "programa", "nombre_programa", "nombrePrograma",
        "degree", "degree_name", "degreeName",
        "titulo", "título",
        "course", "course_name", "courseName",
        "carrera", "field_of_study", "fieldOfStudy",
        // Cuenta bancaria: el producto (Cuenta de ahorros, Cuenta corriente, etc.).
        "tipo_cuenta",
        // Fe de vida y declaración de convivencia (notaría): a quién van dirigidas (EPS, fondo de pensiones, empleador…).
        "entidad_destino",
        // Carné de afiliado e historia clínica: la EPS que los emite («EPS Salud Avance»).
        "eps",
        // Pasaporte: la autoridad que lo expide («Ministerio de Relaciones Exteriores de Cívica»).
        "autoridad",
    )

    /**
     * Tiquete aéreo: en vez de «OTROS», la ruta y el vuelo («BGA - BOG · Vuelo PMO 4862»). Sale de los datos del propio tiquete
     * (`origen` y `destino`, con el código del aeropuerto entre paréntesis, y `vuelo`), así que sirve también para los ya emitidos.
     */
    private fun flightCardLabel(document: IssuedDocument): String? {
        val flight = extractValueFromDocumentOrEmpty(document, "vuelo").takeIf { it.isNotBlank() } ?: return null
        fun airport(key: String): String? {
            val text = extractValueFromDocumentOrEmpty(document, key).takeIf { it.isNotBlank() } ?: return null
            return Regex("\\(([A-Za-z]{3})\\)").find(text)?.groupValues?.get(1)?.uppercase() ?: text
        }
        val origin = airport("origen") ?: return null
        val destination = airport("destino") ?: return null
        return resourceProvider.getString(R.string.document_card_flight, origin, destination, flight)
    }

    private companion object {
        private const val CREDENTIAL_ORDER_DELIMITER = ","
    }

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun onFilterStateChange(): Flow<DocumentInteractorFilterPartialState> =
        filterValidator.onFilterStateChange().map { result ->
            val unorderedDocuments = when (result) {
                is FilterValidatorPartialState.FilterListResult.FilterApplyResult -> {
                    result.filteredList.items.mapNotNull { filterableItem ->
                        filterableItem.payload as? DocumentUi
                    }
                }

                is FilterValidatorPartialState.FilterListResult.FilterListEmptyResult -> {
                    emptyList()
                }

                else -> {
                    emptyList()
                }
            }

            // Every document keeps the color slot it was given (see allocateCredentialColorSlots),
            // so its color doesn't change when the user drags it elsewhere in the display order,
            // filters the list or deletes another credential.
            val colorSlots = prefKeys.allocateCredentialColorSlots(walletCoreDocumentsController)
            val defaultOrderedDocuments = unorderedDocuments
                .sortedBy { it.documentCategory.order }
                .map { document ->
                    document.copy(colorIndex = colorSlots[document.uiData.itemId] ?: 0)
                }

            val documentsUi = applyCredentialDisplayOrder(defaultOrderedDocuments)

            val filtersUi = result.updatedFilters.filterGroups.map { filterGroup ->
                ExpandableListItemUi.NestedListItem(
                    isExpanded = false,
                    header = ListItemDataUi(
                        itemId = filterGroup.id,
                        mainContentData = ListItemMainContentDataUi.Text(filterGroup.name),
                        trailingContentData = ListItemTrailingContentDataUi.Icon(
                            iconData = AppIcons.KeyboardArrowDown
                        )
                    ),
                    nestedItems = filterGroup.filters.map { filterItem ->
                        ExpandableListItemUi.SingleListItem(
                            header = ListItemDataUi(
                                itemId = filterItem.id,
                                mainContentData = ListItemMainContentDataUi.Text(filterItem.name),
                                trailingContentData = when (filterGroup) {
                                    is FilterGroup.MultipleSelectionFilterGroup<*>,
                                    is FilterGroup.ReversibleMultipleSelectionFilterGroup<*> -> {
                                        ListItemTrailingContentDataUi.Checkbox(
                                            checkboxData = CheckboxDataUi(
                                                isChecked = filterItem.selected,
                                                enabled = true
                                            )
                                        )
                                    }

                                    is FilterGroup.SingleSelectionFilterGroup,
                                    is FilterGroup.ReversibleSingleSelectionFilterGroup -> {
                                        ListItemTrailingContentDataUi.RadioButton(
                                            radioButtonData = RadioButtonDataUi(
                                                isSelected = filterItem.selected,
                                                enabled = true
                                            )
                                        )
                                    }
                                },
                            )
                        )
                    }
                )
            }

            when (result) {
                is FilterValidatorPartialState.FilterListResult -> {
                    DocumentInteractorFilterPartialState.FilterApplyResult(
                        documents = documentsUi,
                        filters = filtersUi,
                        allDefaultFiltersAreSelected = result.allDefaultFiltersAreSelected
                    )
                }

                is FilterValidatorPartialState.FilterUpdateResult -> {
                    DocumentInteractorFilterPartialState.FilterUpdateResult(
                        filters = filtersUi,
                    )
                }
            }
        }

    /**
     * Reorders [defaultOrderedDocuments] to match the user's saved credential order (see
     * [PrefKeys.getCredentialOrder]), if any: documents present in the saved order come first, in
     * that order; anything else (never reordered, or added since) keeps its default relative
     * order and is appended at the end.
     */
    private suspend fun applyCredentialDisplayOrder(
        defaultOrderedDocuments: List<DocumentUi>,
    ): List<DocumentUi> {
        val savedOrder = prefKeys.getCredentialOrder()
            .split(CREDENTIAL_ORDER_DELIMITER)
            .filter { it.isNotBlank() }

        if (savedOrder.isEmpty()) return defaultOrderedDocuments

        val documentsById = defaultOrderedDocuments.associateBy { it.uiData.itemId }
        val reordered = savedOrder.mapNotNull { documentsById[it] }
        val newDocuments = defaultOrderedDocuments.filterNot { it.uiData.itemId in savedOrder }
        return reordered + newDocuments
    }

    override suspend fun saveCredentialOrder(orderedDocumentIds: List<String>) {
        prefKeys.setCredentialOrder(orderedDocumentIds.joinToString(CREDENTIAL_ORDER_DELIMITER))
    }

    override fun initializeFilters(
        filterableList: FilterableList,
    ) = filterValidator.initializeValidator(
        addDynamicFilters(filterableList, getFilters()),
        filterableList
    )

    override fun updateLists(filterableList: FilterableList) =
        filterValidator.updateLists(filterableList)

    override fun applySearch(query: String) = filterValidator.applySearch(query)

    override fun revertFilters() = filterValidator.revertFilters()

    override fun updateFilter(filterGroupId: String, filterId: String) =
        filterValidator.updateFilter(filterGroupId, filterId)

    override fun updateSort(filterId: String) =
        filterValidator.updateSort(filterId)

    override fun applyFilters() = filterValidator.applyFilters()

    override fun resetFilters() = filterValidator.resetFilters()

    override fun getDocuments(): Flow<DocumentInteractorGetDocumentsPartialState> =
        flow<DocumentInteractorGetDocumentsPartialState> {
            val shouldAllowUserInteraction =
                walletCoreDocumentsController.getMainPidDocument() != null

            val documentCategories = walletCoreDocumentsController.getAllDocumentCategories()

            val userLocale = resourceProvider.getLocale()

            val showBatchIssuanceCounter = prefKeys.getShowBatchIssuanceCounter()

            val documents = walletCoreDocumentsController.getAllDocuments()

            // The metadata a document carries is the one that was current when the credential
            // was issued, and it is never updated afterwards. Asking the issuers for the one
            // they publish NOW makes their current branding (name, logo, colours) show up
            // without having to reissue the credential.
            val freshMetadataByIssuer: Map<String, CredentialIssuerMetadata> =
                documents.mapNotNull { it.issuerMetadata?.credentialIssuerIdentifier }
                    .distinct()
                    .mapNotNull { issuerId ->
                        walletCoreDocumentsController.fetchIssuerMetadata(issuerId)
                            ?.let { issuerId to it }
                    }
                    .toMap()

            val allDocuments = FilterableList(
                items = documents.map { document ->

                    val documentIsRevoked =
                        walletCoreDocumentsController.isDocumentRevoked(document.id)

                    when (document) {
                        is IssuedDocument -> {
                            val localizedIssuerMetadata =
                                document.localizedIssuerMetadata(userLocale)
                            val localizedDocumentDisplay =
                                document.localizedDocumentDisplay(userLocale)

                            // What the issuer publishes right now, for this very credential
                            // configuration (the name, the logo and the colours) and for the
                            // issuer itself (its emblem, the watermark of the card).
                            val freshIssuerMetadata = document.issuerMetadata
                                ?.credentialIssuerIdentifier
                                ?.let { freshMetadataByIssuer[it] }
                            val freshDocumentDisplay = freshIssuerMetadata
                                ?.credentialConfigurationsSupported
                                ?.get(
                                    document.issuerMetadata?.documentConfigurationIdentifier
                                        ?.let { CredentialConfigurationIdentifier(it) }
                                )
                                ?.credentialMetadata
                                ?.display
                                .getLocalizedValue(
                                    userLocale = userLocale,
                                    localeExtractor = { it.locale },
                                    valueExtractor = { it },
                                    fallback = null,
                                )
                            val freshIssuerDisplay = freshIssuerMetadata
                                ?.display
                                .getLocalizedValue(
                                    userLocale = userLocale,
                                    localeExtractor = { it.locale },
                                    valueExtractor = { it },
                                    fallback = null,
                                )

                            val issuerName = freshIssuerDisplay?.name
                                ?: localizedIssuerMetadata?.name
                                ?: resourceProvider.getString(R.string.documents_screen_filters_unknown_issuer)

                            val documentIdentifier = document.toDocumentIdentifier()

                            val documentCategory = documentIdentifier.toDocumentCategory(
                                allCategories = documentCategories
                            )

                            val categoryOverride = if (documentCategory == DocumentCategory.Other) {
                                flightCardLabel(document) ?: academicProgramClaimKeys.firstNotNullOfOrNull { key ->
                                    extractValueFromDocumentOrEmpty(document, key)
                                        .takeIf { it.isNotBlank() }
                                        ?.let { value ->
                                            // El destino se muestra como tal («Dirigida a: EPS»), no como un dato suelto.
                                            if (key == "entidad_destino") {
                                                resourceProvider.getString(R.string.document_card_destination, value)
                                            } else {
                                                value
                                            }
                                        }
                                }
                            } else {
                                null
                            }

                            val documentName = freshDocumentDisplay?.name ?: document.name

                            val documentSearchTags = buildList {
                                add(documentName)
                                if (issuerName.isNotBlank()) {
                                    add(issuerName)
                                }
                            }

                            val documentExpirationDate = document.getExpiryDate()
                            val documentHasExpired = document.isExpired()

                            val documentIssuanceState = when {
                                documentIsRevoked -> DocumentIssuanceStateUi.Revoked
                                documentHasExpired -> DocumentIssuanceStateUi.Expired
                                else -> DocumentIssuanceStateUi.Issued
                            }

                            val supportingContentData = when {
                                documentIsRevoked -> ListItemSupportingContentDataUi.Text(
                                    text = resourceProvider.getString(R.string.dashboard_document_revoked),
                                    textColorKey = ThemeColorKey.Error,
                                )

                                documentHasExpired -> ListItemSupportingContentDataUi.Text(
                                    text = resourceProvider.getString(R.string.dashboard_document_has_expired),
                                    textColorKey = ThemeColorKey.Error,
                                )

                                documentExpirationDate == null -> null

                                else -> ListItemSupportingContentDataUi.Text(
                                    text = resourceProvider.getString(
                                        R.string.dashboard_document_has_not_expired,
                                        documentExpirationDate.formatInstant()
                                    ),
                                )
                            }

                            val trailingContentData = if (documentIsRevoked) {
                                ListItemTrailingContentDataUi.Icon(
                                    iconData = AppIcons.ErrorFilled,
                                    tint = ThemeColorKey.Error
                                )
                            } else {
                                val documentAvailableCredentials = document.credentialsCount()
                                val documentTotalCredentials = document.initialCredentialsCount()

                                val documentCredentialsInfoUi = DocumentCredentialsInfoUi(
                                    availableCredentials = documentAvailableCredentials,
                                    totalCredentials = documentTotalCredentials,
                                    title = resourceProvider.getString(
                                        R.string.dashboard_document_credentials_info_text,
                                        documentAvailableCredentials,
                                        documentTotalCredentials
                                    )
                                )

                                val documentLowOnCredentials = walletCoreDocumentsController
                                    .isDocumentLowOnCredentials(document)

                                createDocumentTrailingContentData(
                                    documentCredentialsInfoUi = documentCredentialsInfoUi,
                                    documentLowOnCredentials = documentLowOnCredentials,
                                    showBatchIssuanceCounter = showBatchIssuanceCounter
                                )
                            }

                            FilterableItem(
                                payload = DocumentUi(
                                    documentIssuanceState = documentIssuanceState,
                                    uiData = ListItemDataUi(
                                        itemId = document.id,
                                        mainContentData = ListItemMainContentDataUi.Text(text = documentName),
                                        overlineText = issuerName,
                                        supportingContentData = supportingContentData,
                                        leadingContentData = ListItemLeadingContentDataUi.AsyncImage(
                                            imageUrl = (freshDocumentDisplay?.logo?.uri
                                                ?: localizedDocumentDisplay?.logo?.uri
                                                ?: localizedIssuerMetadata?.logo?.uri).toString(),
                                            contentDescription = resourceProvider.getString(R.string.content_description_issuer_logo_icon),
                                            errorImage = AppIcons.Id,
                                        ),
                                        trailingContentData = trailingContentData
                                    ),
                                    documentIdentifier = documentIdentifier,
                                    documentCategory = documentCategory,
                                    categoryOverride = categoryOverride,
                                    textColorHex = freshDocumentDisplay?.textColor
                                        ?: localizedDocumentDisplay?.textColor,
                                    watermarkUri = (freshIssuerDisplay?.logo?.uri
                                        ?: localizedIssuerMetadata?.logo?.uri)?.toString(),
                                    portraitBase64 = document.extractPortraitBase64(),
                                    licenseCard = document.extractLicenseCardData(),
                                ),
                                attributes = DocumentsFilterableAttributes(
                                    searchTags = documentSearchTags,
                                    issuedDate = document.issuedAt,
                                    expiryDate = documentExpirationDate,
                                    issuer = issuerName,
                                    name = documentName,
                                    category = documentCategory,
                                    isRevoked = documentIsRevoked
                                )
                            )
                        }

                        is UnsignedDocument -> {
                            val localizedIssuerMetadata =
                                document.localizedIssuerMetadata(userLocale)
                            val localizedDocumentDisplay =
                                document.localizedDocumentDisplay(userLocale)

                            val issuerName = localizedIssuerMetadata?.name
                                ?: resourceProvider.getString(R.string.documents_screen_filters_unknown_issuer)

                            val documentIdentifier = document.toDocumentIdentifier()

                            val documentCategory = documentIdentifier.toDocumentCategory(
                                allCategories = documentCategories
                            )

                            val documentName = document.name

                            val documentSearchTags = buildList {
                                add(documentName)
                                if (issuerName.isNotBlank()) {
                                    add(issuerName)
                                }
                            }

                            FilterableItem(
                                payload = DocumentUi(
                                    documentIssuanceState = DocumentIssuanceStateUi.Pending,
                                    uiData = ListItemDataUi(
                                        itemId = document.id,
                                        mainContentData = ListItemMainContentDataUi.Text(text = documentName),
                                        overlineText = issuerName,
                                        supportingContentData = ListItemSupportingContentDataUi.Text(
                                            text = resourceProvider.getString(R.string.dashboard_document_deferred_pending),
                                            textColorKey = ThemeColorKey.Warning,
                                        ),
                                        leadingContentData = ListItemLeadingContentDataUi.AsyncImage(
                                            imageUrl = (localizedDocumentDisplay?.logo?.uri
                                                ?: localizedIssuerMetadata?.logo?.uri).toString(),
                                            contentDescription = resourceProvider.getString(R.string.content_description_issuer_logo_icon),
                                            errorImage = AppIcons.Id,
                                        ),
                                        trailingContentData = ListItemTrailingContentDataUi.Icon(
                                            iconData = AppIcons.ClockTimer,
                                            tint = ThemeColorKey.Warning,
                                        )
                                    ),
                                    documentIdentifier = documentIdentifier,
                                    documentCategory = documentCategory,
                                ),
                                attributes = DocumentsFilterableAttributes(
                                    searchTags = documentSearchTags,
                                    issuedDate = null,
                                    expiryDate = null,
                                    issuer = issuerName,
                                    name = documentName,
                                    category = documentCategory,
                                    isRevoked = documentIsRevoked
                                )
                            )
                        }
                    }
                }
            )

            emit(
                DocumentInteractorGetDocumentsPartialState.Success(
                    allDocuments = allDocuments,
                    shouldAllowUserInteraction = shouldAllowUserInteraction,
                )
            )
        }.safeAsync {
            DocumentInteractorGetDocumentsPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMsg
            )
        }

    private fun createDocumentTrailingContentData(
        documentCredentialsInfoUi: DocumentCredentialsInfoUi,
        documentLowOnCredentials: Boolean,
        showBatchIssuanceCounter: Boolean,
    ): ListItemTrailingContentDataUi {
        val lowOnCredentialsIcon = AppIcons.ErrorFilled
        val lowOnCredentialsIconTint = ThemeColorKey.Warning

        return when {
            showBatchIssuanceCounter && documentLowOnCredentials -> {
                ListItemTrailingContentDataUi.TextWithIcon(
                    text = documentCredentialsInfoUi.title,
                    iconData = lowOnCredentialsIcon,
                    tint = lowOnCredentialsIconTint
                )
            }

            showBatchIssuanceCounter -> {
                ListItemTrailingContentDataUi.TextWithIcon(
                    text = documentCredentialsInfoUi.title,
                    iconData = AppIcons.KeyboardArrowRight
                )
            }

            documentLowOnCredentials -> {
                ListItemTrailingContentDataUi.Icon(
                    iconData = lowOnCredentialsIcon,
                    tint = lowOnCredentialsIconTint
                )
            }

            else -> {
                ListItemTrailingContentDataUi.Icon(
                    iconData = AppIcons.KeyboardArrowRight
                )
            }
        }
    }

    override fun tryIssuingDeferredDocumentsFlow(
        deferredDocuments: Map<DocumentId, FormatType>,
        dispatcher: CoroutineDispatcher,
    ): Flow<DocumentInteractorRetryIssuingDeferredDocumentsPartialState> = flow {

        val successResults: MutableList<DeferredDocumentDataDomain> = mutableListOf()
        val failedResults: MutableList<DocumentId> = mutableListOf()

        withContext(dispatcher) {
            val allJobs = deferredDocuments.keys.map { deferredDocumentId ->
                async {
                    tryIssuingDeferredDocumentSuspend(deferredDocumentId)
                }
            }

            allJobs.forEach { job ->
                when (val result = job.await()) {
                    is DocumentInteractorRetryIssuingDeferredDocumentPartialState.Failure -> {
                        failedResults.add(result.documentId)
                    }

                    is DocumentInteractorRetryIssuingDeferredDocumentPartialState.Success -> {
                        successResults.add(result.deferredDocumentData)
                    }

                    is DocumentInteractorRetryIssuingDeferredDocumentPartialState.NotReady -> {}

                    is DocumentInteractorRetryIssuingDeferredDocumentPartialState.Expired -> {
                        deleteDocument(result.documentId).lastOrNull()
                    }

                    is DocumentInteractorRetryIssuingDeferredDocumentPartialState.IssuerNotTrusted -> {
                        deleteDocument(result.documentId).lastOrNull()
                    }
                }
            }
        }

        emit(
            DocumentInteractorRetryIssuingDeferredDocumentsPartialState.Result(
                successfullyIssuedDeferredDocuments = successResults,
                failedIssuedDeferredDocuments = failedResults
            )
        )

    }.safeAsync {
        DocumentInteractorRetryIssuingDeferredDocumentsPartialState.Failure(
            errorMessage = it.localizedMessage ?: genericErrorMsg
        )
    }

    private suspend fun tryIssuingDeferredDocumentSuspend(
        deferredDocumentId: DocumentId,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): DocumentInteractorRetryIssuingDeferredDocumentPartialState {
        return withContext(dispatcher) {
            walletCoreDocumentsController.issueDeferredDocument(deferredDocumentId)
                .map { result ->
                    when (result) {
                        is IssueDeferredDocumentPartialState.Failed -> {
                            DocumentInteractorRetryIssuingDeferredDocumentPartialState.Failure(
                                documentId = result.documentId,
                                errorMessage = result.errorMessage
                            )
                        }

                        is IssueDeferredDocumentPartialState.Issued -> {
                            DocumentInteractorRetryIssuingDeferredDocumentPartialState.Success(
                                deferredDocumentData = result.deferredDocumentData
                            )
                        }

                        is IssueDeferredDocumentPartialState.NotReady -> {
                            DocumentInteractorRetryIssuingDeferredDocumentPartialState.NotReady(
                                deferredDocumentData = result.deferredDocumentData
                            )
                        }

                        is IssueDeferredDocumentPartialState.Expired -> {
                            DocumentInteractorRetryIssuingDeferredDocumentPartialState.Expired(
                                documentId = result.documentId
                            )
                        }

                        is IssueDeferredDocumentPartialState.IssuerNotTrusted -> {
                            DocumentInteractorRetryIssuingDeferredDocumentPartialState.IssuerNotTrusted(
                                documentId = result.documentId
                            )
                        }
                    }
                }.firstOrNull()
                ?: DocumentInteractorRetryIssuingDeferredDocumentPartialState.Failure(
                    documentId = deferredDocumentId,
                    errorMessage = genericErrorMsg
                )
        }
    }

    override fun deleteDocument(
        documentId: String,
    ): Flow<DocumentInteractorDeleteDocumentPartialState> =
        flow {
            walletCoreDocumentsController.deleteDocument(documentId).collect { response ->
                when (response) {
                    is DeleteDocumentPartialState.Failure -> {
                        emit(
                            DocumentInteractorDeleteDocumentPartialState.Failure(
                                errorMessage = response.errorMessage
                            )
                        )
                    }

                    is DeleteDocumentPartialState.Success -> {
                        if (configLogic.forcePidActivation
                            && walletCoreDocumentsController.getAllDocuments().isEmpty()
                        ) {
                            emit(DocumentInteractorDeleteDocumentPartialState.AllDocumentsDeleted)
                        } else
                            emit(DocumentInteractorDeleteDocumentPartialState.SingleDocumentDeleted)
                    }
                }
            }
        }.safeAsync {
            DocumentInteractorDeleteDocumentPartialState.Failure(
                errorMessage = it.localizedMessage ?: genericErrorMsg
            )
        }

    override fun addDynamicFilters(documents: FilterableList, filters: Filters): Filters {
        return filters.copy(
            filterGroups = filters.filterGroups.map { filterGroup ->
                when (filterGroup.id) {
                    DocumentFilterIds.FILTER_BY_ISSUER_GROUP_ID -> {
                        filterGroup as FilterGroup.MultipleSelectionFilterGroup<*>
                        filterGroup.copy(
                            filters = addIssuerFilter(documents)
                        )
                    }

                    DocumentFilterIds.FILTER_BY_DOCUMENT_CATEGORY_GROUP_ID -> {
                        filterGroup as FilterGroup.MultipleSelectionFilterGroup<*>
                        filterGroup.copy(
                            filters = addDocumentCategoryFilter(documents)
                        )
                    }

                    DocumentFilterIds.FILTER_BY_STATE_GROUP_ID -> {
                        filterGroup as FilterGroup.MultipleSelectionFilterGroup<*>
                        filterGroup.copy(
                            filters = buildList {
                                addAll(filterGroup.filters)
                            }
                        )
                    }

                    else -> {
                        filterGroup
                    }
                }
            },
            sortOrder = filters.sortOrder
        )
    }

    override fun getFilters(): Filters = Filters(
        filterGroups = listOf(
            // Filter by expiry period
            FilterGroup.SingleSelectionFilterGroup(
                id = DocumentFilterIds.FILTER_BY_PERIOD_GROUP_ID,
                name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_expiry_period),
                filters = listOf(
                    FilterItem(
                        id = DocumentFilterIds.FILTER_BY_PERIOD_DEFAULT,
                        name = resourceProvider.getString(R.string.documents_screen_filters_sort_default),
                        selected = true,
                        isDefault = true,
                        filterableAction = FilterAction.Filter<DocumentsFilterableAttributes> { _, _ ->
                            true // Get everything
                        }
                    ),
                    FilterItem(
                        id = DocumentFilterIds.FILTER_BY_PERIOD_NEXT_7,
                        name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_expiry_period_1),
                        selected = false,
                        filterableAction = FilterAction.Filter<DocumentsFilterableAttributes> { attributes, _ ->
                            attributes.expiryDate?.isWithinNextDays(7) == true
                        }
                    ),
                    FilterItem(
                        id = DocumentFilterIds.FILTER_BY_PERIOD_NEXT_30,
                        name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_expiry_period_2),
                        selected = false,
                        filterableAction = FilterAction.Filter<DocumentsFilterableAttributes> { attributes, _ ->
                            attributes.expiryDate?.isWithinNextDays(30) == true
                        }
                    ),
                    FilterItem(
                        id = DocumentFilterIds.FILTER_BY_PERIOD_BEYOND_30,
                        name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_expiry_period_3),
                        selected = false,
                        filterableAction = FilterAction.Filter<DocumentsFilterableAttributes> { attributes, _ ->
                            attributes.expiryDate?.isBeyondNextDays(30) == true
                        }
                    ),
                    FilterItem(
                        id = DocumentFilterIds.FILTER_BY_PERIOD_EXPIRED,
                        name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_expiry_period_4),
                        selected = false,
                        filterableAction = FilterAction.Filter<DocumentsFilterableAttributes> { attributes, _ ->
                            attributes.expiryDate?.isExpired() == true
                        }
                    )
                )
            ),
            // Filter by Issuer
            FilterGroup.MultipleSelectionFilterGroup(
                id = DocumentFilterIds.FILTER_BY_ISSUER_GROUP_ID,
                name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_issuer),
                filters = emptyList(),
                filterableAction = FilterMultipleAction<DocumentsFilterableAttributes> { attributes, filter ->
                    attributes.issuer == filter.name
                }
            ),
            // Filter by category
            FilterGroup.MultipleSelectionFilterGroup(
                id = DocumentFilterIds.FILTER_BY_DOCUMENT_CATEGORY_GROUP_ID,
                name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_category),
                filters = emptyList(),
                filterableAction = FilterMultipleAction<DocumentsFilterableAttributes> { attributes, filter ->
                    attributes.category.id.toString() == filter.id
                }
            ),
            // Filter by State
            FilterGroup.MultipleSelectionFilterGroup(
                id = DocumentFilterIds.FILTER_BY_STATE_GROUP_ID,
                name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_state),
                filters = listOf(
                    FilterItem(
                        id = DocumentFilterIds.FILTER_BY_STATE_VALID,
                        name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_state_valid),
                        selected = true,
                        isDefault = true,
                    ),
                    FilterItem(
                        id = DocumentFilterIds.FILTER_BY_STATE_EXPIRED,
                        name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_state_expired),
                        selected = false,
                        isDefault = false,
                    ),
                    FilterItem(
                        id = DocumentFilterIds.FILTER_BY_STATE_REVOKED,
                        name = resourceProvider.getString(R.string.documents_screen_filters_filter_by_state_revoked),
                        selected = false,
                        isDefault = false,
                    ),
                ),
                filterableAction = FilterMultipleAction<DocumentsFilterableAttributes> { attributes, filter ->
                    when (filter.id) {
                        DocumentFilterIds.FILTER_BY_STATE_VALID -> {
                            (attributes.expiryDate?.isValid() == true || attributes.expiryDate == null)
                                    && attributes.isRevoked == false
                        }

                        DocumentFilterIds.FILTER_BY_STATE_EXPIRED -> attributes.expiryDate?.isExpired() == true && attributes.isRevoked == false
                        DocumentFilterIds.FILTER_BY_STATE_REVOKED -> attributes.isRevoked
                        else -> true
                    }
                }
            )
        ),
        sortOrder = SortOrder.Ascending(isDefault = true),
        sort = FilterSort(
            id = DocumentFilterIds.FILTER_SORT_GROUP_ID,
            name = resourceProvider.getString(R.string.documents_screen_filters_sort_by),
            filters = listOf(
                FilterItem(
                    id = DocumentFilterIds.FILTER_SORT_DEFAULT,
                    name = resourceProvider.getString(R.string.documents_screen_filters_sort_default),
                    selected = true,
                    isDefault = true,
                    filterableAction = FilterAction.Sort<DocumentsFilterableAttributes, String> { attributes ->
                        attributes.name.lowercase()
                    }
                ),
            )
        )
    )

    private fun addDocumentCategoryFilter(documents: FilterableList): List<FilterItem> {
        return documents.items
            .distinctBy { (it.attributes as DocumentsFilterableAttributes).category }
            .map { filterableItem ->
                with(filterableItem.attributes as DocumentsFilterableAttributes) {
                    FilterItem(
                        id = category.id.toString(),
                        name = resourceProvider.getString(category.stringResId),
                        selected = true,
                        isDefault = true
                    )
                }
            }
    }

    private fun addIssuerFilter(documents: FilterableList): List<FilterItem> {
        return documents.items
            .distinctBy { (it.attributes as DocumentsFilterableAttributes).issuer }
            .map { filterableItem ->
                with(filterableItem.attributes as DocumentsFilterableAttributes) {
                    FilterItem(
                        id = issuer,
                        name = issuer,
                        selected = true,
                        isDefault = true,
                    )
                }
            }
    }

}