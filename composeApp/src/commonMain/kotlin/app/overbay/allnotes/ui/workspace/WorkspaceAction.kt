package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.SectionAccentKey

sealed interface WorkspaceAction {
    // Navigation & UI Chrome
    data class SelectNotebook(val notebookId: String) : WorkspaceAction
    data class SelectSection(val sectionId: String) : WorkspaceAction
    data class SelectPage(val pageId: String) : WorkspaceAction
    data class SetSearchQuery(val query: String) : WorkspaceAction
    data object ToggleSidebar : WorkspaceAction
    data class SetMobileHierarchySheetOpen(val open: Boolean) : WorkspaceAction
    data class OpenDialog(val dialog: ActiveDialog) : WorkspaceAction
    data object DismissDialog : WorkspaceAction
    data object TriggerSync : WorkspaceAction
    data class ConfigureSyncServer(val endpointUrl: String, val useRemoteHttp: Boolean) : WorkspaceAction

    // Hierarchy CRUD
    data class CreateNotebook(val title: String, val subtitle: String, val accent: SectionAccentKey) : WorkspaceAction
    data class UpdateActiveNotebook(val title: String, val subtitle: String, val accent: SectionAccentKey) : WorkspaceAction
    data object DeleteActiveNotebook : WorkspaceAction
    data class CreateSection(val title: String, val accent: SectionAccentKey) : WorkspaceAction
    data class UpdateActiveSection(val title: String, val accent: SectionAccentKey) : WorkspaceAction
    data object DeleteActiveSection : WorkspaceAction
    data class CreatePage(val title: String, val asSubpageOfActive: Boolean = false) : WorkspaceAction
    data class TogglePageSubpageIndent(val pageId: String) : WorkspaceAction

    // Active Page Metadata
    data class UpdateActivePageTitle(val newTitle: String) : WorkspaceAction
    data object ToggleActivePagePin : WorkspaceAction
    data object DeleteActivePage : WorkspaceAction

    // Block Selection & Ordering
    data class SetActiveBlock(val blockId: String?) : WorkspaceAction
    data class MoveBlock(val blockId: String, val delta: Int) : WorkspaceAction
    data class DeleteBlock(val blockId: String) : WorkspaceAction

    // Block Insertion
    data class InsertHeading(val level: Int = 2, val afterBlockId: String? = null) : WorkspaceAction
    data class InsertParagraph(val afterBlockId: String? = null) : WorkspaceAction
    data class InsertChecklist(val afterBlockId: String? = null) : WorkspaceAction
    data class InsertBulletList(val isNumbered: Boolean = false, val afterBlockId: String? = null) : WorkspaceAction
    data class InsertTable(val afterBlockId: String? = null) : WorkspaceAction
    data class InsertCallout(val afterBlockId: String? = null) : WorkspaceAction
    data class InsertCode(val afterBlockId: String? = null) : WorkspaceAction

    // Block Content & Formatting Mutations
    data class UpdateBlockText(val blockId: String, val newText: String) : WorkspaceAction
    data class ToggleParagraphStyle(val blockId: String, val style: String) : WorkspaceAction
    data class ToggleActiveParagraphStyle(val style: String) : WorkspaceAction
    data class UpdateHeadingLevel(val blockId: String, val level: Int) : WorkspaceAction

    // Checklist Mutations
    data class UpdateChecklistTitle(val blockId: String, val title: String) : WorkspaceAction
    data class ToggleChecklistItem(val blockId: String, val itemId: String) : WorkspaceAction
    data class AddChecklistItem(val blockId: String, val text: String) : WorkspaceAction
    data class UpdateChecklistItemText(val blockId: String, val itemId: String, val text: String) : WorkspaceAction
    data class DeleteChecklistItem(val blockId: String, val itemId: String) : WorkspaceAction

    // Bullet / Numbered List Mutations
    data class ToggleBulletListKind(val blockId: String) : WorkspaceAction
    data class AddBulletListItem(val blockId: String, val text: String) : WorkspaceAction
    data class UpdateBulletListItem(val blockId: String, val itemIndex: Int, val text: String) : WorkspaceAction
    data class DeleteBulletListItem(val blockId: String, val itemIndex: Int) : WorkspaceAction

    // Table Mutations
    data class UpdateTableHeader(val blockId: String, val colIndex: Int, val title: String) : WorkspaceAction
    data class UpdateTableCell(val blockId: String, val rowIndex: Int, val colIndex: Int, val text: String) : WorkspaceAction
    data class AddTableRow(val blockId: String) : WorkspaceAction
    data class DeleteTableRow(val blockId: String, val rowIndex: Int) : WorkspaceAction
    data class AddTableColumn(val blockId: String, val headerTitle: String = "Column") : WorkspaceAction
    data class DeleteTableColumn(val blockId: String, val colIndex: Int) : WorkspaceAction

    // Callout & Code Mutations
    data class UpdateCalloutTitle(val blockId: String, val title: String) : WorkspaceAction
    data class UpdateCalloutTone(val blockId: String, val tone: CalloutTone) : WorkspaceAction
    data class UpdateCodeLanguage(val blockId: String, val language: String) : WorkspaceAction
}
