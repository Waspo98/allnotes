package app.overbay.allnotes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.editor.blocks.DocumentBlockView
import app.overbay.allnotes.ui.editor.blocks.rememberSyncedFieldHolder
import app.overbay.allnotes.ui.editor.toolbar.DesktopFormattingRibbon
import app.overbay.allnotes.ui.editor.toolbar.ExpressiveInsertFabMenu
import app.overbay.allnotes.ui.editor.toolbar.MobileFloatingToolbar
import app.overbay.allnotes.ui.media.rememberPlatformAttachmentPicker
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.theme.accentColor
import app.overbay.allnotes.ui.theme.ambientWashColor
import app.overbay.allnotes.ui.workspace.ActiveDialog
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

    LaunchedEffect(state.statusBannerMessage) {
        if (state.statusBannerMessage != null) {
            delay(2800L)
            onAction(WorkspaceAction.DismissStatusBanner)
        }
    }

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
                    .widthIn(max = if (isCompactMobile) 680.dp else 900.dp)
                    .padding(
                        horizontal = if (isCompactMobile) 0.dp else 24.dp,
                        vertical = if (isCompactMobile) 0.dp else 12.dp
                    )
            ) {
                EditorTopBar(
                    state = state,
                    isCompactMobile = isCompactMobile,
                    onAction = onAction
                )

                if (!isCompactMobile && page != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    DesktopFormattingRibbon(
                        state = state,
                        onRequestMediaPicker = requestMediaPicker,
                        onAction = onAction
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (page == null) {
                    EmptyPageStudioState(onAction = onAction)
                } else {
                    val listState = rememberLazyListState()
                    val titleHolder = rememberSyncedFieldHolder("page-title-${page.id}", page.title)

                    LaunchedEffect(page.id) {
                        listState.scrollToItem(0)
                    }

                    LaunchedEffect(state.focusRequest?.requestToken) {
                        val req = state.focusRequest ?: return@LaunchedEffect
                        val idx = page.blocks.indexOfFirst { it.id == req.blockId }
                        if (idx >= 0) {
                            val listIdx = idx + 1
                            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == listIdx }
                            if (!isVisible) {
                                listState.animateScrollToItem(listIdx)
                            }
                        }
                    }

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
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
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
                                bottom = if (isCompactMobile) 128.dp else 84.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item(key = "page-header-${page.id}", contentType = "page-header") {
                                DocumentPageHeader(
                                    titleHolder = titleHolder,
                                    updatedAtEpochMs = page.updatedAtEpochMs,
                                    wordCount = state.wordCount,
                                    readingTimeMinutes = state.readingTimeMinutes,
                                    accentColor = sectionAccent,
                                    titleFocusToken = 0L,
                                    onTitleFocusConsumed = {},
                                    onTitleChange = { onAction(WorkspaceAction.UpdateActivePageTitle(it)) },
                                    onTitleEnter = { onAction(WorkspaceAction.FocusOrAppendTrailingParagraph) }
                                )
                            }

                            items(
                                items = page.blocks,
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

                            item(key = "trailing-canvas-${page.id}", contentType = "trailing-canvas") {
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

            AnimatedVisibility(
                visible = state.statusBannerMessage != null,
                enter = fadeIn(AllNotesMotion.effectsFast()),
                exit = fadeOut(AllNotesMotion.effectsFast()),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = MaterialTheme.shapes.small,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = state.statusBannerMessage.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            if (isCompactMobile && page != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = 14.dp, start = 12.dp, end = 12.dp),
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
                text = "No Page Selected",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select a page from the navigation drawer or create a new one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.NEW_PAGE)) }) {
                Icon(AllNotesIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Page")
            }
        }
    }
}
