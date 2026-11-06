package app.overbay.allnotes.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.allnotes.ui.editor.DocumentEditorPane
import app.overbay.allnotes.ui.hierarchy.HierarchySidebar
import app.overbay.allnotes.ui.theme.allNotesSpring

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactMobile = maxWidth < 680.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (!isCompactMobile) {
                AnimatedVisibility(
                    visible = state.isSidebarOpen,
                    enter = expandHorizontally(animationSpec = allNotesSpring()),
                    exit = shrinkHorizontally(animationSpec = allNotesSpring())
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
                onInsertTable = viewModel::insertTableBlock,
                onInsertCallout = viewModel::insertCalloutBlock,
                onInsertCode = viewModel::insertCodeBlock,
                onTogglePin = viewModel::toggleActivePagePin,
                onDeletePage = viewModel::deleteActivePage,
                modifier = Modifier.weight(1f)
            )
        }

        if (isCompactMobile && state.isMobileHierarchySheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.setMobileHierarchySheetOpen(false) }
            ) {
                HierarchySidebar(
                    state = state,
                    onSelectNotebook = viewModel::selectNotebook,
                    onSelectSection = viewModel::selectSection,
                    onSelectPage = viewModel::selectPage,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onOpenDialog = viewModel::openDialog,
                    onTriggerSync = viewModel::triggerManualSync,
                    modifier = Modifier.fillMaxSize()
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
