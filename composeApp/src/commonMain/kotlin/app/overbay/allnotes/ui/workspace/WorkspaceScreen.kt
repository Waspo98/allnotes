package app.overbay.allnotes.ui.workspace

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.ui.editor.DocumentEditorPane
import app.overbay.allnotes.ui.hierarchy.CollapsedWorkspaceRail
import app.overbay.allnotes.ui.hierarchy.HierarchySidebar
import app.overbay.allnotes.ui.theme.AllNotesMotion
import app.overbay.allnotes.ui.workspace.dialogs.WorkspaceDialogsHost
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    fun dismissActiveOverlayOrSearch(): Boolean = when {
        state.previewAttachment != null -> {
            viewModel.dispatch(WorkspaceAction.DismissAttachmentPreview)
            true
        }
        state.activeDialog != ActiveDialog.NONE -> {
            viewModel.dispatch(WorkspaceAction.DismissDialog)
            true
        }
        state.isMobileInsertMenuOpen -> {
            viewModel.dispatch(WorkspaceAction.SetMobileInsertMenuOpen(false))
            true
        }
        state.isMobileHierarchySheetOpen -> {
            viewModel.dispatch(WorkspaceAction.SetMobileHierarchySheetOpen(false))
            true
        }
        state.isPageMenuOpen -> {
            viewModel.dispatch(WorkspaceAction.SetPageMenuOpen(false))
            true
        }
        state.searchQuery.isNotEmpty() -> {
            viewModel.dispatch(WorkspaceAction.SetSearchQuery(""))
            true
        }
        else -> false
    }

    PlatformBackHandler(
        enabled = state.previewAttachment != null ||
            state.activeDialog != ActiveDialog.NONE ||
            state.isMobileInsertMenuOpen ||
            state.isMobileHierarchySheetOpen ||
            state.isPageMenuOpen ||
            state.searchQuery.isNotEmpty()
    ) {
        dismissActiveOverlayOrSearch()
    }

    val density = LocalDensity.current
    val containerWidthPx = LocalWindowInfo.current.containerSize.width
    val windowWidthDp = remember(containerWidthPx, density) {
        if (containerWidthPx > 0) with(density) { containerWidthPx.toDp() } else 1080.dp
    }
    val isCompactMobile = windowWidthDp < 680.dp
    val isMediumOverlay = !isCompactMobile && windowWidthDp < 1040.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    !event.isCtrlPressed &&
                    !event.isMetaPressed &&
                    event.key == Key.Escape
                ) {
                    return@onPreviewKeyEvent dismissActiveOverlayOrSearch()
                }
                if (event.type == KeyEventType.KeyDown && (event.isCtrlPressed || event.isMetaPressed)) {
                    when (event.key) {
                        Key.B -> {
                            viewModel.dispatch(WorkspaceAction.ToggleSelectionSpan(RichSpanKind.BOLD))
                            true
                        }
                        Key.I -> {
                            viewModel.dispatch(WorkspaceAction.ToggleSelectionSpan(RichSpanKind.ITALIC))
                            true
                        }
                        Key.U -> {
                            viewModel.dispatch(WorkspaceAction.ToggleSelectionSpan(RichSpanKind.UNDERLINE))
                            true
                        }
                        Key.K -> {
                            viewModel.dispatch(WorkspaceAction.OpenDialog(ActiveDialog.INSERT_LINK))
                            true
                        }
                        Key.Z -> {
                            if (event.isShiftPressed) {
                                viewModel.dispatch(WorkspaceAction.Redo)
                            } else {
                                viewModel.dispatch(WorkspaceAction.Undo)
                            }
                            true
                        }
                        Key.Y -> {
                            viewModel.dispatch(WorkspaceAction.Redo)
                            true
                        }
                        Key.N -> {
                            viewModel.dispatch(WorkspaceAction.OpenDialog(ActiveDialog.NEW_PAGE))
                            true
                        }
                        Key.L -> {
                            if (event.isShiftPressed) {
                                viewModel.dispatch(WorkspaceAction.InsertChecklist(state.activeBlockId))
                                true
                            } else false
                        }
                        Key.M -> {
                            if (event.isShiftPressed && state.activePage != null) {
                                viewModel.dispatch(WorkspaceAction.OpenDialog(ActiveDialog.MARKDOWN_EXPORT))
                                true
                            } else false
                        }
                        Key.F -> {
                            viewModel.dispatch(WorkspaceAction.RequestSearchFocus)
                            true
                        }
                        Key.S -> {
                            if (event.isShiftPressed) {
                                viewModel.dispatch(WorkspaceAction.ToggleSelectionSpan(RichSpanKind.STRIKETHROUGH))
                            } else {
                                viewModel.dispatch(WorkspaceAction.TriggerSync)
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        if (isCompactMobile) {
            val drawerState = rememberDrawerState(
                initialValue = if (state.isMobileHierarchySheetOpen) DrawerValue.Open else DrawerValue.Closed
            )

            LaunchedEffect(state.isMobileHierarchySheetOpen) {
                if (state.isMobileHierarchySheetOpen && !drawerState.isOpen) {
                    drawerState.open()
                } else if (!state.isMobileHierarchySheetOpen && drawerState.isOpen) {
                    drawerState.close()
                }
            }

            LaunchedEffect(drawerState.currentValue) {
                val isOpen = drawerState.currentValue == DrawerValue.Open
                if (isOpen != state.isMobileHierarchySheetOpen) {
                    viewModel.dispatch(WorkspaceAction.SetMobileHierarchySheetOpen(isOpen))
                }
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(316.dp),
                        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        HierarchySidebar(
                            state = state,
                            onAction = { action ->
                                if (action is WorkspaceAction.SelectPage) {
                                    coroutineScope.launch {
                                        viewModel.dispatch(action)
                                        drawerState.close()
                                    }
                                } else {
                                    viewModel.dispatch(action)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            ) {
                DocumentEditorPane(
                    state = state,
                    isCompactMobile = true,
                    onAction = viewModel::dispatch,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (isMediumOverlay) {
            val sidebarProgress = animateFloatAsState(
                targetValue = if (state.isSidebarOpen) 1f else 0f,
                animationSpec = AllNotesMotion.spatialDefault(),
                label = "MediumSidebarOverlay"
            )
            val sidebarWidthPx = with(density) { 320.dp.toPx() }
            val scrimColor = MaterialTheme.colorScheme.scrim

            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    CollapsedWorkspaceRail(
                        state = state,
                        onAction = viewModel::dispatch
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    DocumentEditorPane(
                        state = state,
                        isCompactMobile = false,
                        onAction = viewModel::dispatch,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (state.isSidebarOpen || sidebarProgress.value > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = (sidebarProgress.value * 0.28f).coerceIn(0f, 0.28f) }
                            .background(scrimColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                viewModel.dispatch(WorkspaceAction.ToggleSidebar)
                            }
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(320.dp)
                            .graphicsLayer {
                                translationX = -(1f - sidebarProgress.value) * sidebarWidthPx
                            },
                        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        HierarchySidebar(
                            state = state,
                            onAction = viewModel::dispatch,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            val sidebarProgress = animateFloatAsState(
                targetValue = if (state.isSidebarOpen) 1f else 0f,
                animationSpec = AllNotesMotion.spatialDefault(),
                label = "DesktopSidebarWidth"
            )
            val railWidthPx = with(density) { 60.dp.roundToPx() }
            val expandedWidthPx = with(density) { 320.dp.roundToPx() }

            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val t = sidebarProgress.value.coerceIn(0f, 1f)
                            val targetW = (railWidthPx + (expandedWidthPx - railWidthPx) * t).roundToInt()
                            val placeable = measurable.measure(
                                constraints.copy(minWidth = targetW, maxWidth = targetW)
                            )
                            layout(targetW, placeable.height) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                ) {
                    if (sidebarProgress.value < 0.99f) {
                        CollapsedWorkspaceRail(
                            state = state,
                            onAction = viewModel::dispatch,
                            modifier = Modifier.graphicsLayer {
                                alpha = (1f - sidebarProgress.value * 1.4f).coerceIn(0f, 1f)
                            }
                        )
                    }
                    if (sidebarProgress.value > 0.01f) {
                        HierarchySidebar(
                            state = state,
                            onAction = viewModel::dispatch,
                            modifier = Modifier
                                .width(320.dp)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    alpha = ((sidebarProgress.value - 0.15f) / 0.85f).coerceIn(0f, 1f)
                                    translationX = -(1f - sidebarProgress.value) * 28f
                                }
                        )
                    }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                DocumentEditorPane(
                    state = state,
                    isCompactMobile = false,
                    onAction = viewModel::dispatch,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        WorkspaceDialogsHost(
            state = state,
            onAction = viewModel::dispatch
        )
    }
}
