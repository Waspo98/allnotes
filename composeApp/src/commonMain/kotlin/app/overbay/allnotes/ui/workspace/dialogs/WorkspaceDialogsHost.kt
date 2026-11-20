package app.overbay.allnotes.ui.workspace.dialogs

import androidx.compose.runtime.Composable
import app.overbay.allnotes.data.markdown.MarkdownCodec
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.ui.editor.blocks.AttachmentLightboxDialog
import app.overbay.allnotes.ui.editor.spans.RichSpanEngine
import app.overbay.allnotes.ui.workspace.ActiveDialog
import app.overbay.allnotes.ui.workspace.WorkspaceAction
import app.overbay.allnotes.ui.workspace.WorkspaceUiState

@Composable
fun WorkspaceDialogsHost(
    state: WorkspaceUiState,
    isCompactMobile: Boolean = false,
    onAction: (WorkspaceAction) -> Unit
) {
    when (state.activeDialog) {
        ActiveDialog.NONE -> Unit
        ActiveDialog.NEW_NOTEBOOK -> NotebookDialog(
            initialTitle = "",
            initialSubtitle = "",
            initialAccent = SectionAccentKey.INDIGO,
            isEdit = false,
            canReorder = false,
            onReorder = {},
            onDismiss = { onAction(WorkspaceAction.DismissDialog) },
            onConfirm = { t, s, a -> onAction(WorkspaceAction.CreateNotebook(t, s, a)) },
            onRequestDelete = null
        )
        ActiveDialog.EDIT_NOTEBOOK -> {
            val nb = state.activeNotebook ?: return
            NotebookDialog(
                initialTitle = nb.title,
                initialSubtitle = nb.subtitle,
                initialAccent = nb.accent,
                isEdit = true,
                canReorder = state.notebooks.size > 1,
                onReorder = { delta -> onAction(WorkspaceAction.ReorderActiveNotebook(delta)) },
                onDismiss = { onAction(WorkspaceAction.DismissDialog) },
                onConfirm = { t, s, a -> onAction(WorkspaceAction.UpdateActiveNotebook(t, s, a)) },
                onRequestDelete = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.CONFIRM_DELETE_NOTEBOOK)) }
            )
        }
        ActiveDialog.CONFIRM_DELETE_NOTEBOOK -> {
            val nb = state.activeNotebook ?: return
            val secIds = state.sections.filter { it.notebookId == nb.id }.map { it.id }.toSet()
            val pageCount = state.pages.count { it.sectionId in secIds }
            ConfirmDeleteHierarchyDialog(
                title = "Delete \"${nb.title}\"?",
                message = "This permanently deletes ${secIds.size} section(s) and $pageCount page(s) across synced devices.",
                onDismiss = { onAction(WorkspaceAction.DismissDialog) },
                onConfirmDelete = { onAction(WorkspaceAction.DeleteActiveNotebook) }
            )
        }
        ActiveDialog.NEW_SECTION -> SectionDialog(
            initialTitle = "",
            initialAccent = SectionAccentKey.EMERALD,
            initialPinned = false,
            isEdit = false,
            canMoveEarlier = false,
            canMoveLater = false,
            onReorder = {},
            onDismiss = { onAction(WorkspaceAction.DismissDialog) },
            onConfirm = { t, a, _ -> onAction(WorkspaceAction.CreateSection(t, a)) },
            onRequestDelete = null
        )
        ActiveDialog.EDIT_SECTION -> {
            val sec = state.sections.firstOrNull { it.id == state.editingSectionId }
                ?: state.activeSection
                ?: return
            val partition = state.sections
                .filter { it.notebookId == sec.notebookId && it.isPinned == sec.isPinned }
                .sortedWith(app.overbay.allnotes.model.NoteSectionDisplayComparator)
            val idx = partition.indexOfFirst { it.id == sec.id }
            SectionDialog(
                initialTitle = sec.title,
                initialAccent = sec.accent,
                initialPinned = sec.isPinned,
                isEdit = true,
                canMoveEarlier = idx > 0,
                canMoveLater = idx >= 0 && idx < partition.lastIndex,
                onReorder = { delta -> onAction(WorkspaceAction.ReorderSection(sec.id, delta)) },
                onDismiss = { onAction(WorkspaceAction.DismissDialog) },
                onConfirm = { t, a, pinned -> onAction(WorkspaceAction.UpdateSection(sec.id, t, a, pinned)) },
                onRequestDelete = { onAction(WorkspaceAction.OpenDialog(ActiveDialog.CONFIRM_DELETE_SECTION)) }
            )
        }
        ActiveDialog.MANAGE_SECTIONS -> {
            val nb = state.activeNotebook ?: return
            ManageSectionsDialog(
                notebookTitle = nb.title,
                sections = state.sectionsForNotebook,
                activeSectionId = state.activeSection?.id,
                onAction = onAction,
                onDismiss = { onAction(WorkspaceAction.DismissDialog) }
            )
        }
        ActiveDialog.CONFIRM_DELETE_SECTION -> {
            val sec = state.sections.firstOrNull { it.id == state.editingSectionId }
                ?: state.activeSection
                ?: return
            val pageCount = state.pages.count { it.sectionId == sec.id }
            ConfirmDeleteHierarchyDialog(
                title = "Delete \"${sec.title}\"?",
                message = "This permanently deletes $pageCount page(s) inside this section across synced devices.",
                onDismiss = { onAction(WorkspaceAction.DismissDialog) },
                onConfirmDelete = { onAction(WorkspaceAction.DeleteSection(sec.id)) }
            )
        }
        ActiveDialog.CONFIRM_DELETE_PAGE -> {
            val targetPage = state.pages.firstOrNull { it.id == state.pendingDeletePageId }
                ?: state.activePage
                ?: return
            val childCount = state.pages.count { it.parentPageId == targetPage.id }
            val childNote = if (childCount > 0) " Its $childCount subpage(s) will be promoted to top level." else ""
            ConfirmDeleteHierarchyDialog(
                title = "Delete \"${targetPage.title.ifBlank { "Untitled Page" }}\"?",
                message = "This permanently deletes this page across synced devices.$childNote",
                onDismiss = { onAction(WorkspaceAction.DismissDialog) },
                onConfirmDelete = { onAction(WorkspaceAction.DeletePage(targetPage.id)) }
            )
        }
        ActiveDialog.MOVE_PAGE -> {
            val page = state.activePage ?: return
            MovePageDialog(
                pageTitle = page.title,
                currentSectionId = page.sectionId,
                notebooks = state.notebooks,
                sections = state.sections,
                onDismiss = { onAction(WorkspaceAction.DismissDialog) },
                onMoveToSection = { targetSecId -> onAction(WorkspaceAction.MoveActivePageToSection(targetSecId)) }
            )
        }
        ActiveDialog.INSERT_LINK -> {
            val activeParagraph = state.activeBlock as? DocumentBlock.Paragraph
            val existingUrl = if (activeParagraph != null) {
                RichSpanEngine.activeLinkAtSelection(activeParagraph, state.selectionStart, state.selectionEnd)?.url.orEmpty()
            } else ""
            InsertLinkDialog(
                initialUrl = existingUrl,
                onDismiss = { onAction(WorkspaceAction.DismissDialog) },
                onApplyLink = { url -> onAction(WorkspaceAction.ToggleSelectionSpan(RichSpanKind.LINK, url = url)) }
            )
        }
        ActiveDialog.MARKDOWN_EXPORT -> {
            val page = state.activePage ?: return
            MarkdownExportDialog(
                pageTitle = page.title,
                markdown = MarkdownCodec.exportPageToMarkdown(page),
                onCopied = { onAction(WorkspaceAction.ShowStatusBanner("Markdown copied to clipboard")) },
                onDismiss = { onAction(WorkspaceAction.DismissDialog) }
            )
        }
        ActiveDialog.MARKDOWN_IMPORT -> MarkdownImportDialog(
            onDismiss = { onAction(WorkspaceAction.DismissDialog) },
            onImport = { md -> onAction(WorkspaceAction.ImportMarkdownPage(md)) }
        )
        ActiveDialog.SYNC_SETTINGS -> WorkspaceSettingsSheet(
            state = state,
            isCompactMobile = isCompactMobile,
            onAction = onAction,
            onDismiss = { onAction(WorkspaceAction.DismissDialog) }
        )
        ActiveDialog.QUICK_JUMP -> QuickJumpDialog(
            state = state,
            onAction = onAction,
            onDismiss = { onAction(WorkspaceAction.DismissDialog) }
        )
    }

    val preview = state.previewAttachment
    if (preview != null) {
        AttachmentLightboxDialog(
            attachment = preview,
            onAction = onAction
        )
    }
}
