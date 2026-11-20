package app.overbay.allnotes.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.editor.blocks.DocumentBlockView
import app.overbay.allnotes.ui.editor.blocks.rememberSyncedFieldHolder
import app.overbay.allnotes.ui.editor.toolbar.BlockInsertBottomSheet
import app.overbay.allnotes.ui.editor.toolbar.DesktopFormattingRibbon
import app.overbay.allnotes.ui.editor.toolbar.ExpressiveInsertFabMenu
import app.overbay.allnotes.ui.editor.toolbar.MobileFloatingToolbar
import app.overbay.allnotes.ui.editor.toolbar.MobileReadingPill
import app.overbay.allnotes.ui.media.rememberPlatformAttachmentPicker
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.theme.ambientWashColor
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState
import kotlinx.coroutines.delay

@Composable
fun DocumentEditorPane(
    state: WorkspaceUiState,
    isCompactMobile: Boolean,
    onAction: (WorkspaceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val page = state.activePage
    val density = LocalDensity.current
    val requestMediaPicker = rememberPlatformAttachmentPicker(
        onPicked = { picked ->
            onAction(
                WorkspaceAction.InsertAttachment(
                    fileName = picked.fileName,
                    mimeType = picked.mimeType,
                    sizeBytes = picked.sizeBytes,
                    dataBase64 = picked.dataBase64,
                    afterBlockId = state.activeBlockId
                )
            )
        },
        onError = { msg -> onAction(WorkspaceAction.ShowStatusBanner(msg)) }
    )

    LaunchedEffect(state.statusBannerMessage, state.statusBannerActionLabel) {
        if (state.statusBannerMessage != null) {
            delay(if (state.statusBannerAction != null) 4200L else 2800L)
            onAction(WorkspaceAction.DismissStatusBanner)
        }
    }

    var readingPillHidden by remember(page?.id) { mutableStateOf(false) }
    val readingScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -10f) {
                    readingPillHidden = true
                } else if (available.y > 10f) {
                    readingPillHidden = false
                }
                return Offset.Zero
            }
        }
    }

    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val isWritingMode = imeVisible || state.activeBlockId != null || state.isMobileInsertMenuOpen

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top +
                                if (isCompactMobile) WindowInsetsSides.Horizontal else WindowInsetsSides.End
                        )
                    )
                    .widthIn(max = if (isCompactMobile) 680.dp else 880.dp)
                    .padding(
                        horizontal = if (isCompactMobile) 0.dp else 28.dp,
                        vertical = if (isCompactMobile) 0.dp else 12.dp
                    )
            ) {
                EditorTopBar(
                    state = state,
                    isCompactMobile = isCompactMobile,
                    onAction = onAction
                )

                if (!isCompactMobile && page != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    DesktopFormattingRibbon(
                        state = state,
                        onRequestMediaPicker = requestMediaPicker,
                        onAction = onAction
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (page == null) {
                    EmptyPageStudioState(onAction = onAction)
                } else {
                    val sectionAccent = state.activeSection?.accent?.accentColor() ?: MaterialTheme.colorScheme.primary
                    val sectionAmbientWash = state.activeSection?.accent?.ambientWashColor() ?: Color.Transparent

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (isCompactMobile) {
                            MaterialTheme.colorScheme.background
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                        shape = if (isCompactMobile) RectangleShape else MaterialTheme.shapes.large,
                        shadowElevation = if (isCompactMobile) 0.dp else 1.dp,
                        border = if (isCompactMobile) {
                            null
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                        }
                    ) {
                        AnimatedContent(
                            targetState = page.id,
                            transitionSpec = {
                                fadeIn(AllNotesMotion.effectsFast()) togetherWith fadeOut(AllNotesMotion.effectsFast())
                            },
                            label = "PageFadeThrough"
                        ) { animatedPageId ->
                            val renderedPage = state.pages.firstOrNull { it.id == animatedPageId } ?: page
                            val listState = rememberLazyListState()
                            val titleHolder = rememberSyncedFieldHolder("page-title-${renderedPage.id}", renderedPage.title)

                            LaunchedEffect(state.focusRequest?.requestToken) {
                                val req = state.focusRequest ?: return@LaunchedEffect
                                val idx = renderedPage.blocks.indexOfFirst { it.id == req.blockId }
                                if (idx >= 0) {
                                    val listIdx = idx + 1
                                    val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == listIdx }
                                    if (!isVisible) {
                                        listState.animateScrollToItem(listIdx)
                                    }
                                }
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(readingScrollConnection)
                                    .drawBehind {
                                        if (sectionAmbientWash.alpha > 0f) {
                                            val washHeight = 112.dp.toPx().coerceAtMost(size.height)
                                            drawRect(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(sectionAmbientWash, Color.Transparent),
                                                    startY = 0f,
                                                    endY = washHeight
                                                ),
                                                size = Size(size.width, washHeight)
                                            )
                                        }
                                    }
                                    .navigationBarsPadding()
                                    .imePadding(),
                                contentPadding = PaddingValues(
                                    start = if (isCompactMobile) 22.dp else 52.dp,
                                    end = if (isCompactMobile) 22.dp else 52.dp,
                                    top = if (isCompactMobile) 12.dp else 36.dp,
                                    bottom = if (isCompactMobile) 136.dp else 88.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item(key = "page-header-${renderedPage.id}", contentType = "page-header") {
                                    DocumentPageHeader(
                                        titleHolder = titleHolder,
                                        updatedAtEpochMs = renderedPage.updatedAtEpochMs,
                                        wordCount = state.wordCount,
                                        readingTimeMinutes = state.readingTimeMinutes,
                                        accentColor = sectionAccent,
                                        titleFocusToken = state.titleFocusRequestToken,
                                        onTitleFocusConsumed = { tok -> onAction(WorkspaceAction.ConsumeTitleFocusRequest(tok)) },
                                        onTitleChange = { onAction(WorkspaceAction.UpdateActivePageTitle(it)) },
                                        onTitleEnter = { onAction(WorkspaceAction.FocusOrAppendTrailingParagraph) }
                                    )
                                }

                                items(
                                    items = renderedPage.blocks,
                                    key = { it.id },
                                    contentType = { it::class.simpleName }
                                ) { block ->
                                    val blockFocusRequest = state.focusRequest?.takeIf { it.blockId == block.id }
                                    DocumentBlockView(
                                        block = block,
                                        isActive = block.id == state.activeBlockId,
                                        isCompactMobile = isCompactMobile,
                                        searchQuery = state.searchQuery,
                                        focusRequest = blockFocusRequest,
                                        onRequestMediaPicker = requestMediaPicker,
                                        onAction = onAction,
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = AllNotesMotion.effectsFast(),
                                            fadeOutSpec = AllNotesMotion.effectsFast(),
                                            placementSpec = AllNotesMotion.spatialFast()
                                        )
                                    )
                                }

                                item(key = "trailing-canvas-${renderedPage.id}", contentType = "trailing-canvas") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                onAction(WorkspaceAction.FocusOrAppendTrailingParagraph)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isCompactMobile && state.isMobileInsertMenuOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onAction(WorkspaceAction.SetMobileInsertMenuOpen(false))
                        }
                )
            }

            AnimatedVisibility(
                visible = state.statusBannerMessage != null,
                enter = fadeIn(AllNotesMotion.effectsFast()) + slideInVertically(AllNotesMotion.spatialFast()) { it / 2 },
                exit = fadeOut(AllNotesMotion.effectsFast()) + slideOutVertically(AllNotesMotion.spatialFast()) { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = if (isCompactMobile) 84.dp else 20.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = if (state.statusBannerAction != null) 8.dp else 16.dp,
                            top = if (state.statusBannerAction != null) 4.dp else 11.dp,
                            bottom = if (state.statusBannerAction != null) 4.dp else 11.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = state.statusBannerMessage.orEmpty(),
                            style = MaterialTheme.typography.labelLarge
                        )
                        val bannerAction = state.statusBannerAction
                        val bannerLabel = state.statusBannerActionLabel
                        if (bannerAction != null && !bannerLabel.isNullOrBlank()) {
                            TextButton(
                                onClick = {
                                    onAction(bannerAction)
                                    onAction(WorkspaceAction.DismissStatusBanner)
                                }
                            ) {
                                Text(
                                    text = bannerLabel,
                                    color = MaterialTheme.colorScheme.inversePrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (isCompactMobile && page != null) {
                val pillVisibility by animateFloatAsState(
                    targetValue = if (!isWritingMode && readingPillHidden) 0f else 1f,
                    animationSpec = AllNotesMotion.spatialFast(),
                    label = "MobileChromeVisibility"
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = 14.dp, start = 12.dp, end = 12.dp)
                        .graphicsLayer {
                            alpha = pillVisibility
                            translationY = (1f - pillVisibility) * 84f
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AnimatedContent(
                        targetState = isWritingMode,
                        transitionSpec = {
                            (fadeIn(AllNotesMotion.effectsFast()) + slideInVertically(AllNotesMotion.spatialFast()) { it / 3 }) togetherWith
                                (fadeOut(AllNotesMotion.effectsFast()) + slideOutVertically(AllNotesMotion.spatialFast()) { it / 3 })
                        },
                        label = "MobileModeChrome"
                    ) { writing ->
                        if (writing) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                MobileFloatingToolbar(
                                    state = state,
                                    onRequestMediaPicker = requestMediaPicker,
                                    onAction = onAction,
                                    modifier = Modifier.weight(1f)
                                )
                                ExpressiveInsertFabMenu(
                                    state = state,
                                    onRequestMediaPicker = requestMediaPicker,
                                    onAction = onAction
                                )
                            }
                        } else {
                            MobileReadingPill(
                                onOpenDrawer = { onAction(WorkspaceAction.SetMobileHierarchySheetOpen(true)) },
                                onNewPage = { onAction(WorkspaceAction.CreatePageInstant(false)) },
                                onOpenBlockSheet = { onAction(WorkspaceAction.SetBlockInsertSheetOpen(true)) }
                            )
                        }
                    }
                }
            }

            if (state.isBlockInsertSheetOpen && page != null) {
                BlockInsertBottomSheet(
                    afterBlockId = state.activeBlockId,
                    onRequestMediaPicker = requestMediaPicker,
                    onAction = onAction,
                    onDismiss = { onAction(WorkspaceAction.SetBlockInsertSheetOpen(false)) }
                )
            }
        }
    }
}

@Composable
private fun EmptyPageStudioState(onAction: (WorkspaceAction) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "A Quiet Desk",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pick a note from your notebooks, or start a fresh page right away.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            FilledTonalButton(onClick = { onAction(WorkspaceAction.CreatePageInstant(false)) }) {
                Icon(AllNotesIcons.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Page")
            }
        }
    }
}
