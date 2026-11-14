package app.overbay.allnotes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.editor.blocks.DocumentBlockView
import app.overbay.allnotes.ui.editor.blocks.rememberSyncedFieldHolder
import app.overbay.allnotes.ui.editor.toolbar.DesktopFormattingRibbon
import app.overbay.allnotes.ui.editor.toolbar.ExpressiveInsertFabMenu
import app.overbay.allnotes.ui.editor.toolbar.MobileFloatingToolbar
import app.overbay.allnotes.ui.media.rememberPlatformAttachmentPicker
import app.overbay.allnotes.ui.theme.AllNotesIcons
import app.overbay.allnotes.ui.theme.allNotesSpring
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

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (isCompactMobile) {
                            MaterialTheme.colorScheme.background
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                        shape = if (isCompactMobile) RectangleShape else RoundedCornerShape(20.dp),
                        border = if (isCompactMobile) {
                            null
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = if (isCompactMobile) 20.dp else 44.dp,
                                end = if (isCompactMobile) 20.dp else 44.dp,
                                top = if (isCompactMobile) 8.dp else 32.dp,
                                bottom = if (isCompactMobile) 128.dp else 72.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item(key = "page-header-${page.id}") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (titleHolder.fieldValue.text.isEmpty()) {
                                            Text(
                                                text = "Untitled Page",
                                                style = MaterialTheme.typography.headlineMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                )
                                            )
                                        }
                                        BasicTextField(
                                            value = titleHolder.fieldValue,
                                            onValueChange = { updated ->
                                                val changed = updated.text != titleHolder.fieldValue.text
                                                titleHolder.applyLocalChange(updated)
                                                if (changed) onAction(WorkspaceAction.UpdateActivePageTitle(updated.text))
                                            },
                                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    if (isCompactMobile && state.wordCount > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${state.wordCount} words · ${state.readingTimeMinutes} min read",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                }
                            }

                            items(page.blocks, key = { it.id }) { block ->
                                DocumentBlockView(
                                    block = block,
                                    isActive = block.id == state.activeBlockId,
                                    isCompactMobile = isCompactMobile,
                                    searchQuery = state.searchQuery,
                                    focusRequest = state.focusRequest,
                                    onRequestMediaPicker = requestMediaPicker,
                                    onAction = onAction,
                                    modifier = Modifier.animateItem(placementSpec = allNotesSpring())
                                )
                            }

                            item(key = "trailing-canvas-${page.id}") {
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
                enter = fadeIn(allNotesSpring()),
                exit = fadeOut(allNotesSpring()),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(12.dp),
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
