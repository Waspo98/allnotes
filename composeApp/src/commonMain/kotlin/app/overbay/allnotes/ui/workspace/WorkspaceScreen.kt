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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.editor.DocumentEditorPane
import app.overbay.allnotes.ui.hierarchy.HierarchySidebar
import app.overbay.allnotes.ui.theme.allNotesSpring
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
                            viewModel.dispatch(WorkspaceAction.ToggleActiveParagraphStyle("bold"))
                            true
                        }
                        Key.I -> {
                            viewModel.dispatch(WorkspaceAction.ToggleActiveParagraphStyle("italic"))
                            true
                        }
                        Key.U -> {
                            viewModel.dispatch(WorkspaceAction.ToggleActiveParagraphStyle("underline"))
                            true
                        }
                        Key.L -> {
                            if (event.isShiftPressed) {
                                viewModel.dispatch(WorkspaceAction.InsertChecklist(state.activeBlockId))
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
                                viewModel.dispatch(WorkspaceAction.ToggleActiveParagraphStyle("strikethrough"))
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

        Row(modifier = Modifier.fillMaxSize()) {
            if (!isCompactMobile) {
                AnimatedVisibility(
                    visible = state.isSidebarOpen,
                    enter = expandHorizontally(animationSpec = allNotesSpring()) + fadeIn(animationSpec = allNotesSpring()),
                    exit = shrinkHorizontally(animationSpec = allNotesSpring()) + fadeOut(animationSpec = allNotesSpring())
                ) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        HierarchySidebar(
                            state = state,
                            onAction = viewModel::dispatch,
                            modifier = Modifier.width(340.dp)
                        )
                        VerticalDivider()
                    }
                }
            }

            DocumentEditorPane(
                state = state,
                isCompactMobile = isCompactMobile,
                onAction = viewModel::dispatch,
                modifier = Modifier.weight(1f)
            )
        }

        if (isCompactMobile && state.isMobileHierarchySheetOpen) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            ModalBottomSheet(
                onDismissRequest = { viewModel.dispatch(WorkspaceAction.SetMobileHierarchySheetOpen(false)) },
                sheetState = sheetState
            ) {
                HierarchySidebar(
                    state = state,
                    onAction = { action ->
                        if (action is WorkspaceAction.SelectPage) {
                            coroutineScope.launch {
                                sheetState.hide()
                                viewModel.dispatch(action)
                            }
                        } else {
                            viewModel.dispatch(action)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        WorkspaceDialogsHost(
            state = state,
            onAction = viewModel::dispatch
        )
    }
}
