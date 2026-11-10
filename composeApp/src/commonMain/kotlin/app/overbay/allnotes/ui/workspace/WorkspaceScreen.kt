package app.overbay.allnotes.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.ui.editor.DocumentEditorPane
import app.overbay.allnotes.ui.hierarchy.CollapsedWorkspaceRail
import app.overbay.allnotes.ui.hierarchy.HierarchySidebar
import app.overbay.allnotes.ui.theme.allNotesSpring
import app.overbay.allnotes.ui.workspace.dialogs.WorkspaceDialogsHost
import kotlinx.coroutines.launch

@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent { event ->
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
                            if (!state.isSidebarOpen) {
                                viewModel.dispatch(WorkspaceAction.ToggleSidebar)
                            }
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
        val isCompactMobile = maxWidth < 680.dp

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
                        modifier = Modifier.width(310.dp),
                        drawerShape = RectangleShape,
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = state.isSidebarOpen,
                    enter = expandHorizontally(animationSpec = allNotesSpring()) + fadeIn(animationSpec = allNotesSpring()),
                    exit = shrinkHorizontally(animationSpec = allNotesSpring()) + fadeOut(animationSpec = allNotesSpring())
                ) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        HierarchySidebar(
                            state = state,
                            onAction = viewModel::dispatch,
                            modifier = Modifier.width(320.dp)
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }

                AnimatedVisibility(
                    visible = !state.isSidebarOpen,
                    enter = expandHorizontally(animationSpec = allNotesSpring()) + fadeIn(animationSpec = allNotesSpring()),
                    exit = shrinkHorizontally(animationSpec = allNotesSpring()) + fadeOut(animationSpec = allNotesSpring())
                ) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        CollapsedWorkspaceRail(
                            state = state,
                            onAction = viewModel::dispatch
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }

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
