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

package eu.europa.ec.dashboardfeature.ui.documents.list

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.dashboardfeature.model.SearchItemUi
import eu.europa.ec.dashboardfeature.ui.component.BottomNavigationItem
import eu.europa.ec.dashboardfeature.ui.component.CredentialCard
import eu.europa.ec.dashboardfeature.ui.component.CredentialStack
import eu.europa.ec.dashboardfeature.ui.component.rememberUserIdentity
import eu.europa.ec.dashboardfeature.ui.component.HeaderCard
import eu.europa.ec.dashboardfeature.ui.component.UserAvatar
import eu.europa.ec.dashboardfeature.ui.documents.detail.model.DocumentIssuanceStateUi
import eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentUi
import eu.europa.ec.dashboardfeature.util.TestTag
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.FiltersSearchBar
import eu.europa.ec.uilogic.component.InlineSnackbar
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemSupportingContentDataUi
import eu.europa.ec.uilogic.component.ModalOptionUi
import eu.europa.ec.uilogic.component.content.BroadcastAction
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.HSpacer
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.wrap.FabDataUi
import eu.europa.ec.uilogic.component.wrap.WrapFabDefaults
import eu.europa.ec.uilogic.component.wrap.WrapPrimaryExtendedFab
import eu.europa.ec.uilogic.extension.isScrollingUp
import eu.europa.ec.uilogic.component.utils.OncePerViewModelEffect
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.BottomSheetTextDataUi
import eu.europa.ec.uilogic.component.wrap.BottomSheetWithOptionsList
import eu.europa.ec.uilogic.component.wrap.BottomSheetWithTwoBigIcons
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.DialogBottomSheet
import eu.europa.ec.uilogic.component.wrap.GenericBottomSheet
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapExpandableListItem
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import eu.europa.ec.uilogic.component.wrap.WrapListItem
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import eu.europa.ec.uilogic.extension.applyTestTag
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.extension.paddingFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

typealias DashboardEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event
typealias OpenSideMenuEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event.SideMenu.Open

private const val FAB_ENTER_DELAY_MILLIS = 200
private const val FAB_ENTER_DURATION_MILLIS = 300

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    navHostController: NavController,
    viewModel: DocumentsViewModel,
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val isBottomSheetOpen = state.isBottomSheetOpen
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val listScrollState = rememberLazyListState()
    var fabVisible by rememberSaveable { mutableStateOf(false) }
    var isExtendedView by rememberSaveable { mutableStateOf(false) }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_PAUSE
    ) {
        fabVisible = false
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        fabVisible = true
    }

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = { context.finish() },
        contentErrorConfig = null,
        topBar = {
            TopBar(
                onDashboardEventSent = onDashboardEventSent,
            )
        },
        fab = {
            AddDocumentFab(
                visible = fabVisible,
                expanded = listScrollState.isScrollingUp(),
                onClick = { viewModel.setEvent(Event.AddDocumentPressed) },
            )
        },
        fabPosition = FabPosition.End,
        snackbarHost = { snackbarPaddings ->
            state.error?.let { error ->
                InlineSnackbar(
                    error = error,
                    modifier = Modifier.padding(snackbarPaddings),
                )
            }
        },
        broadcastAction = BroadcastAction(
            intentFilters = listOf(
                CoreActions.REVOCATION_WORK_REFRESH_ACTION,
                CoreActions.RE_ISSUANCE_WORK_REFRESH_ACTION
            ),
            callback = {
                viewModel.setEvent(Event.GetDocuments)
            }
        )
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onEventSend = { viewModel.setEvent(it) },
            onNavigationRequested = { navigationEffect ->
                handleNavigationEffect(navigationEffect, navHostController, context)
            },
            scrollState = listScrollState,
            paddingValues = paddingValues,
            coroutineScope = scope,
            modalBottomSheetState = bottomSheetState,
            isExtendedView = isExtendedView,
            onSearchQueryChanged = { viewModel.setEvent(Event.OnSearchQueryChanged(it)) },
            onFilterClick = { viewModel.setEvent(Event.FiltersPressed) },
            onViewModeChange = { isExtendedView = it },
        )

        if (isBottomSheetOpen) {
            WrapModalBottomSheet(
                onDismissRequest = {
                    viewModel.setEvent(
                        Event.BottomSheet.UpdateBottomSheetState(
                            isOpen = false
                        )
                    )
                },
                sheetState = bottomSheetState
            ) {
                DocumentsSheetContent(
                    sheetContent = state.sheetContent,
                    state = state,
                    onEventSent = {
                        viewModel.setEvent(it)
                    }
                )
            }
        }
    }

    OncePerViewModelEffect(viewModel) {
        viewModel.setEvent(Event.Init)
    }
}

@Composable
private fun AddDocumentFab(
    visible: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(
                durationMillis = FAB_ENTER_DURATION_MILLIS,
                delayMillis = FAB_ENTER_DELAY_MILLIS,
            ),
            initialOffsetY = { fullHeight -> fullHeight }
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = FAB_ENTER_DURATION_MILLIS,
                delayMillis = FAB_ENTER_DELAY_MILLIS,
            )
        ),
        exit = ExitTransition.None,
    ) {
        WrapPrimaryExtendedFab(
            data = FabDataUi(
                text = { Text(text = stringResource(R.string.documents_screen_add_document_fab)) },
                icon = { WrapIcon(iconData = AppIcons.Add) },
                onClick = onClick
            ),
            modifier = Modifier.applyTestTag(TestTag.DocumentsScreen.PLUS_BUTTON),
            expanded = expanded,
            shape = RoundedCornerShape(SIZE_MEDIUM.dp),
        )
    }
}

private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
    context: Context,
) {
    when (navigationEffect) {
        is Effect.Navigation.Pop -> context.finish()
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                popUpTo(navigationEffect.popUpToScreenRoute) {
                    inclusive = navigationEffect.inclusive
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    HeaderCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SPACING_MEDIUM.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WrapIconButton(
                iconData = AppIcons.Menu,
                customTint = MaterialTheme.colorScheme.onSurface,
            ) {
                onDashboardEventSent(OpenSideMenuEvent)
            }

            HSpacer.Small()

            UserAvatar(initials = rememberUserIdentity().initials)

            HSpacer.Small()

            Text(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                text = stringResource(R.string.documents_screen_top_bar_title)
            )
        }
    }
}

@Composable
private fun SearchAndViewModeBar(
    searchText: String,
    isFilteringActive: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onFilterClick: () -> Unit,
    isExtendedView: Boolean,
    onViewModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        val searchItemUi =
            SearchItemUi(searchLabel = stringResource(R.string.documents_screen_search_label))
        FiltersSearchBar(
            placeholder = searchItemUi.searchLabel,
            onValueChange = onSearchQueryChanged,
            onFilterClick = onFilterClick,
            onClearClick = { onSearchQueryChanged("") },
            isFilteringActive = isFilteringActive,
            text = searchText
        )

        VSpacer.Medium()

        CredentialViewModeToggle(
            isExtendedView = isExtendedView,
            onViewModeChange = onViewModeChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSend: (Event) -> Unit,
    onNavigationRequested: (navigationEffect: Effect.Navigation) -> Unit,
    scrollState: LazyListState,
    paddingValues: PaddingValues,
    coroutineScope: CoroutineScope,
    modalBottomSheetState: SheetState,
    isExtendedView: Boolean = true,
    onSearchQueryChanged: (String) -> Unit = {},
    onFilterClick: () -> Unit = {},
    onViewModeChange: (Boolean) -> Unit = {},
) {
    // Lets any credential card in the extended (list) view be long-press-dragged to reorder it;
    // matched back to state.documentsUi by document id (not raw LazyColumn index), since the
    // "search-and-view-mode" header and "fab-spacer" footer items are part of the same list but
    // are never wrapped in ReorderableItem, so they're simply never found by that id lookup.
    val reorderableLazyListState = rememberReorderableLazyListState(scrollState) { from, to ->
        val fromIndex = state.documentsUi.indexOfFirst { it.uiData.itemId == from.key }
        val toIndex = state.documentsUi.indexOfFirst { it.uiData.itemId == to.key }
        if (fromIndex != -1 && toIndex != -1) {
            val newOrder = state.documentsUi.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            onEventSend(Event.OnCredentialOrderChanged(newOrder))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .paddingFrom(paddingValues, bottom = false)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            contentPadding = PaddingValues(top = SPACING_MEDIUM.dp, bottom = SPACING_MEDIUM.dp),
        ) {
            item(key = "search-and-view-mode") {
                SearchAndViewModeBar(
                    modifier = Modifier.fillMaxWidth(),
                    searchText = state.searchText,
                    isFilteringActive = state.isFilteringActive,
                    onSearchQueryChanged = onSearchQueryChanged,
                    onFilterClick = onFilterClick,
                    isExtendedView = isExtendedView,
                    onViewModeChange = onViewModeChange,
                )
                VSpacer.Medium()
            }

            if (state.showNoResultsFound) {
                item {
                    NoResults(modifier = Modifier.fillMaxWidth())
                }
            } else if (isExtendedView) {
                // Every credential, across all categories, gets the exact same standard spacing
                // to the next one, instead of a smaller gap within a category and a much bigger
                // one between categories.
                itemsIndexed(
                    items = state.documentsUi,
                    key = { _, documentItem -> documentItem.uiData.itemId },
                ) { index, documentItem ->
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = documentItem.uiData.itemId,
                    ) { isDragging ->
                        val categoryLabel = stringResource(documentItem.documentCategory.stringResId)
                        CredentialCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    val scale = if (isDragging) 1.03f else 1f
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .longPressDraggableHandle(),
                            item = documentItem.uiData,
                            documentIdentifier = documentItem.documentIdentifier,
                            category = documentItem.categoryOverride ?: categoryLabel,
                            positionIndex = documentItem.colorIndex,
                            onClick = { onDocumentItemClick(documentItem, onEventSend) },
                            textColorHex = documentItem.textColorHex,
                            watermarkUri = documentItem.watermarkUri,
                            portraitBase64 = documentItem.portraitBase64,
                        )
                    }

                    if (index != state.documentsUi.lastIndex) {
                        VSpacer.Medium()
                    }
                }

                item(key = "fab-spacer") {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = SPACING_SMALL.dp)
                            .height(WrapFabDefaults.Height)
                    )
                }

            } else {
                // Compact view: every credential, across all categories, is part of one single
                // deck with the same peek spacing, instead of one separate stack per category.
                item(key = "credential-deck") {
                    CredentialStack(
                        modifier = Modifier.fillMaxWidth(),
                        documents = state.documentsUi,
                        onItemClick = { onDocumentItemClick(it, onEventSend) },
                        onReorder = { newOrder ->
                            onEventSend(Event.OnCredentialOrderChanged(newOrder))
                        },
                    )
                }

                item(key = "fab-spacer") {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = SPACING_SMALL.dp)
                            .height(WrapFabDefaults.Height)
                    )
                }

            }
        }
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        onEventSend(Event.GetDocuments)
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_PAUSE
    ) {
        onEventSend(Event.OnPause)
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)

                is Effect.CloseBottomSheet -> {
                    coroutineScope.launch {
                        modalBottomSheetState.hide()
                    }.invokeOnCompletion {
                        if (!modalBottomSheetState.isVisible) {
                            onEventSend(Event.BottomSheet.UpdateBottomSheetState(isOpen = false))
                        }
                    }
                }

                is Effect.ShowBottomSheet -> {
                    onEventSend(Event.BottomSheet.UpdateBottomSheetState(isOpen = true))
                }

                is Effect.DocumentsFetched -> {
                    onEventSend(Event.TryIssuingDeferredDocuments(effect.deferredDocs))
                }

                is Effect.ResumeOnApplyFilter -> {
                    onEventSend(Event.GetDocuments)
                }
            }
        }.collect()
    }
}

/**
 * Segmented icon toggle between the extended (bank-card-style) and compact (short row)
 * credential layouts, matching the reference wallet's "collapsed vs extended" view switch.
 */
@Composable
private fun CredentialViewModeToggle(
    isExtendedView: Boolean,
    onViewModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ViewModeToggleButton(
            iconData = AppIcons.ViewExtended,
            selected = isExtendedView,
            onClick = { onViewModeChange(true) },
        )
        ViewModeToggleButton(
            iconData = AppIcons.ViewCompact,
            selected = !isExtendedView,
            onClick = { onViewModeChange(false) },
        )
    }
}

@Composable
private fun ViewModeToggleButton(
    iconData: IconDataUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        WrapIcon(
            iconData = iconData,
            customTint = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Resolves the [Event] a tap on [documentItem] should send, and forwards it to [onEventSend].
 * Shared by both the extended (per-category) and compact (single flattened deck) layouts so a
 * credential's click behavior never depends on which view mode is active.
 */
private fun onDocumentItemClick(documentItem: DocumentUi, onEventSend: (Event) -> Unit) {
    val onItemClickEvent = if (
        documentItem.documentIssuanceState == DocumentIssuanceStateUi.Pending
        || documentItem.documentIssuanceState == DocumentIssuanceStateUi.Failed
    ) {
        Event.BottomSheet.DeferredDocument.DeferredNotReadyYet.DocumentSelected(
            documentId = documentItem.uiData.itemId
        )
    } else {
        Event.GoToDocumentDetails(documentItem.uiData.itemId)
    }
    onEventSend(onItemClickEvent)
}

@Composable
private fun NoResults(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        WrapListItem(
            item = ListItemDataUi(
                itemId = DOCUMENTS_NO_RESULTS_ITEM_ID,
                mainContentData = ListItemMainContentDataUi.Text(text = stringResource(R.string.documents_screen_search_no_results)),
            ),
            onItemClick = null,
            modifier = Modifier.fillMaxWidth(),
            mainContentVerticalPadding = SPACING_MEDIUM.dp,
        )
    }
}

@Composable
private fun DocumentsSheetContent(
    sheetContent: DocumentsBottomSheetContent,
    state: State,
    onEventSent: (event: Event) -> Unit,
) {
    when (sheetContent) {
        is DocumentsBottomSheetContent.Filters -> {
            GenericBottomSheet(
                titleContent = {
                    Text(
                        text = stringResource(R.string.documents_screen_filters_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                bodyContent = {
                    var buttonsRowHeight by remember { mutableIntStateOf(0) }

                    Box {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = with(LocalDensity.current) { buttonsRowHeight.toDp() }),
                            verticalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp)
                        ) {
                            state.filtersUi.forEach { filter ->
                                if (filter.nestedItems.isNotEmpty()) {
                                    WrapExpandableListItem(
                                        header = filter.header,
                                        data = filter.nestedItems,
                                        isExpanded = filter.isExpanded,
                                        onExpandedChange = {
                                            onEventSent(
                                                Event.OnFilterGroupExpansionChanged(it.itemId)
                                            )
                                        },
                                        onItemClick = {
                                            val id = it.itemId
                                            val groupId = filter.header.itemId
                                            onEventSent(Event.OnFilterSelectionChanged(id, groupId))
                                        },
                                        addDivider = false,
                                        collapsedMainContentVerticalPadding = 18.dp,
                                        expandedMainContentVerticalPadding = 18.dp,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .onGloballyPositioned { coordinates ->
                                    buttonsRowHeight = coordinates.size.height
                                }
                                .padding(top = SPACING_LARGE.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            WrapButton(
                                modifier = Modifier.weight(1f),
                                buttonConfig = ButtonConfig(
                                    type = ButtonType.SECONDARY,
                                    onClick = {
                                        onEventSent(Event.OnFiltersReset)
                                    }
                                )
                            ) {
                                Text(text = stringResource(R.string.documents_screen_filters_reset))
                            }
                            HSpacer.Small()
                            WrapButton(
                                modifier = Modifier.weight(1f),
                                buttonConfig = ButtonConfig(
                                    type = ButtonType.PRIMARY,
                                    onClick = {
                                        onEventSent(Event.OnFiltersApply)
                                    }
                                )
                            ) {
                                Text(text = stringResource(R.string.documents_screen_filters_apply))
                            }
                        }
                    }
                }
            )
        }

        is DocumentsBottomSheetContent.AddDocument -> {
            BottomSheetWithTwoBigIcons(
                textData = BottomSheetTextDataUi(
                    title = stringResource(R.string.documents_screen_add_document_title),
                    message = stringResource(R.string.documents_screen_add_document_description)
                ),
                options = listOf(
                    ModalOptionUi(
                        title = stringResource(R.string.documents_screen_add_document_option_list),
                        leadingIcon = AppIcons.AddDocumentFromList,
                        event = Event.BottomSheet.AddDocument.FromList,
                    ),
                    ModalOptionUi(
                        title = stringResource(R.string.documents_screen_add_document_option_qr),
                        leadingIcon = AppIcons.AddDocumentFromQr,
                        event = Event.BottomSheet.AddDocument.ScanQr,
                    )
                ),
                onEventSent = onEventSent,
                hostTab = BottomNavigationItem.Documents.route.lowercase(),
            )
        }

        is DocumentsBottomSheetContent.DeferredDocumentPressed -> {
            DialogBottomSheet(
                textData = BottomSheetTextDataUi(
                    title = stringResource(
                        id = R.string.dashboard_bottom_sheet_deferred_document_pressed_title
                    ),
                    message = stringResource(
                        id = R.string.dashboard_bottom_sheet_deferred_document_pressed_subtitle
                    ),
                    positiveButtonText = stringResource(id = R.string.dashboard_bottom_sheet_deferred_document_pressed_primary_button_text),
                    negativeButtonText = stringResource(id = R.string.dashboard_bottom_sheet_deferred_document_pressed_secondary_button_text),
                ),
                onPositiveClick = {
                    onEventSent(
                        Event.BottomSheet.DeferredDocument.DeferredNotReadyYet.PrimaryButtonPressed(
                            documentId = sheetContent.documentId
                        )
                    )
                },
                onNegativeClick = {
                    onEventSent(
                        Event.BottomSheet.DeferredDocument.DeferredNotReadyYet.SecondaryButtonPressed(
                            documentId = sheetContent.documentId
                        )
                    )
                }
            )
        }

        is DocumentsBottomSheetContent.DeferredDocumentsReady -> {
            BottomSheetWithOptionsList(
                textData = BottomSheetTextDataUi(
                    title = stringResource(
                        id = R.string.dashboard_bottom_sheet_deferred_documents_ready_title
                    ),
                    message = stringResource(
                        id = R.string.dashboard_bottom_sheet_deferred_documents_ready_subtitle
                    ),
                ),
                options = sheetContent.options,
                onEventSent = onEventSent,
            )
        }
    }
}

private const val DOCUMENTS_NO_RESULTS_ITEM_ID = "noResultsId"

@OptIn(ExperimentalMaterial3Api::class)
@ThemeModePreviews
@Composable
private fun DocumentsScreenPreview() {
    PreviewTheme {
        val scope = rememberCoroutineScope()
        val bottomSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )
        ContentScreen(
            isLoading = false,
            navigatableAction = ScreenNavigateAction.NONE,
            onBack = { },
            topBar = {
                TopBar(
                    onDashboardEventSent = {},
                )
            },
        ) { paddingValues ->
            val issuerName = "Issuer name"
            val validUntil = "Valid Until"
            val documentsList = listOf(
                DocumentUi(
                    documentIssuanceState = DocumentIssuanceStateUi.Issued,
                    uiData = ListItemDataUi(
                        itemId = "id1",
                        mainContentData = ListItemMainContentDataUi.Text(text = "Document 1"),
                        overlineText = issuerName,
                        supportingContentData = ListItemSupportingContentDataUi.Text(
                            text = validUntil,
                        ),
                        leadingContentData = null,
                        trailingContentData = null
                    ),
                    documentIdentifier = DocumentIdentifier.MdocPid,
                    documentCategory = DocumentCategory.Government
                ),
                DocumentUi(
                    documentIssuanceState = DocumentIssuanceStateUi.Issued,
                    uiData = ListItemDataUi(
                        itemId = "id2",
                        mainContentData = ListItemMainContentDataUi.Text(text = "Document 2"),
                        overlineText = issuerName,
                        supportingContentData = ListItemSupportingContentDataUi.Text(
                            text = validUntil,
                        ),
                        leadingContentData = null,
                        trailingContentData = null
                    ),
                    documentIdentifier = DocumentIdentifier.MdocPid,
                    documentCategory = DocumentCategory.Government
                ),
                DocumentUi(
                    documentIssuanceState = DocumentIssuanceStateUi.Issued,
                    uiData = ListItemDataUi(
                        itemId = "id3",
                        mainContentData = ListItemMainContentDataUi.Text(text = "Document 3"),
                        overlineText = issuerName,
                        supportingContentData = ListItemSupportingContentDataUi.Text(
                            text = validUntil,
                        ),
                        leadingContentData = null,
                        trailingContentData = null
                    ),
                    documentIdentifier = DocumentIdentifier.OTHER(formatType = ""),
                    documentCategory = DocumentCategory.Finance
                ),
                DocumentUi(
                    documentIssuanceState = DocumentIssuanceStateUi.Issued,
                    uiData = ListItemDataUi(
                        itemId = "id4",
                        mainContentData = ListItemMainContentDataUi.Text(text = "Document 4"),
                        overlineText = issuerName,
                        supportingContentData = ListItemSupportingContentDataUi.Text(
                            text = validUntil,
                        ),
                        leadingContentData = null,
                        trailingContentData = null
                    ),
                    documentIdentifier = DocumentIdentifier.OTHER(formatType = ""),
                    documentCategory = DocumentCategory.Other
                ),
            )
            Content(
                state = State(
                    isLoading = false,
                    isFilteringActive = false,
                    documentsUi = documentsList,
                ),
                effectFlow = Channel<Effect>().receiveAsFlow(),
                onEventSend = {},
                onNavigationRequested = {},
                scrollState = rememberLazyListState(),
                paddingValues = paddingValues,
                coroutineScope = scope,
                modalBottomSheetState = bottomSheetState,
            )
        }
    }
}
