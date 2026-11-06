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
                            viewModel.toggleActiveParagraphStyle("bold")
                            true
                        }
                        Key.I -> {
                            viewModel.toggleActiveParagraphStyle("italic")
                            true
                        }
                        Key.F -> {
                            if (!state.isSidebarOpen) {
                                viewModel.toggleSidebar()
                            }
                            true
                        }
                        Key.S -> {
                            viewModel.triggerManualSync()
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
                            onSelectNotebook = viewModel::selectNotebook,
                            onSelectSection = viewModel::selectSection,
                            onSelectPage = viewModel::selectPage,
                            onSearchQueryChange = viewModel::setSearchQuery,
                            onOpenDialog = viewModel::openDialog,
                            onTriggerSync = viewModel::triggerManualSync,
                            modifier = Modifier.width(340.dp)
                        )
                        VerticalDivider()
                    }
                }
            }

            DocumentEditorPane(
                notebook = state.activeNotebook,
                section = state.activeSection,
                page = state.activePage,
                isCompactMobile = isCompactMobile,
                isSidebarOpen = state.isSidebarOpen,
                onToggleSidebar = viewModel::toggleSidebar,
                onOpenMobileHierarchy = { viewModel.setMobileHierarchySheetOpen(true) },
                onUpdatePageTitle = viewModel::updateActivePageTitle,
                onUpdateBlockText = viewModel::updateBlockText,
                onToggleParagraphStyle = viewModel::toggleParagraphStyle,
                onToggleChecklistItem = viewModel::toggleChecklistItem,
                onAddChecklistItem = viewModel::addChecklistItem,
                onDeleteBlock = viewModel::deleteBlock,
                onInsertHeading = { viewModel.insertHeadingBlock(2) },
                onInsertParagraph = viewModel::insertParagraphBlock,
                onInsertChecklist = viewModel::insertChecklistBlock,
                onInsertBulletList = { viewModel.insertBulletListBlock(false) },
                onInsertTable = viewModel::insertTableBlock,
                onInsertCallout = viewModel::insertCalloutBlock,
                onInsertCode = viewModel::insertCodeBlock,
                onUpdateHeadingLevel = viewModel::updateHeadingLevel,
                onAddBulletListItem = viewModel::addBulletListItem,
                onUpdateTableCell = viewModel::updateTableCell,
                onAddTableRow = viewModel::addTableRow,
                onTogglePin = viewModel::toggleActivePagePin,
                onDeletePage = viewModel::deleteActivePage,
                modifier = Modifier.weight(1f)
            )
        }

        if (isCompactMobile && state.isMobileHierarchySheetOpen) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            ModalBottomSheet(
                onDismissRequest = { viewModel.setMobileHierarchySheetOpen(false) },
                sheetState = sheetState
            ) {
                HierarchySidebar(
                    state = state,
                    onSelectNotebook = viewModel::selectNotebook,
                    onSelectSection = viewModel::selectSection,
                    onSelectPage = { pageId ->
                        coroutineScope.launch {
                            sheetState.hide()
                            viewModel.selectPage(pageId)
                        }
                    },
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onOpenDialog = viewModel::openDialog,
                    onTriggerSync = viewModel::triggerManualSync,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        WorkspaceDialogsHost(
            activeDialog = state.activeDialog,
            onDismiss = viewModel::dismissDialog,
            onCreateNotebook = viewModel::createNotebook,
            onCreateSection = viewModel::createSection,
            onCreatePage = viewModel::createPage
        )
    }
}
