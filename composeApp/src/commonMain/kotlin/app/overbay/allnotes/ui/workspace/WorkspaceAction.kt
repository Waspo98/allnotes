package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.ThemeMode

sealed interface WorkspaceAction {
    // Navigation & UI Chrome
    data class SelectNotebook(val notebookId: String) : WorkspaceAction
    data class SelectSection(val sectionId: String) : WorkspaceAction
    data class SelectPage(val pageId: String) : WorkspaceAction
    data class SetSearchQuery(val query: String) : WorkspaceAction
    data object RequestSearchFocus : WorkspaceAction
    data class ConsumeSearchFocusRequest(val requestToken: Long) : WorkspaceAction
    data object ToggleSidebar : WorkspaceAction
    data class SetMobileHierarchySheetOpen(val open: Boolean) : WorkspaceAction
    data class SetPageMenuOpen(val open: Boolean) : WorkspaceAction
    data class SetMobileInsertMenuOpen(val open: Boolean) : WorkspaceAction
    data class SetBlockInsertSheetOpen(val open: Boolean) : WorkspaceAction
    data class ConsumeTitleFocusRequest(val requestToken: Long) : WorkspaceAction
    data class OpenDialog(val dialog: ActiveDialog) : WorkspaceAction
    data object DismissDialog : WorkspaceAction
    data class OpenAttachmentPreview(val attachment: DocumentBlock.Attachment) : WorkspaceAction
    data object DismissAttachmentPreview : WorkspaceAction
    data class ShowStatusBanner(val message: String) : WorkspaceAction
    data object DismissStatusBanner : WorkspaceAction
    data object TriggerSync : WorkspaceAction
    data class SaveSyncAndAuthSettings(
        val endpointUrl: String,
        val useRemoteHttp: Boolean,
        val authToken: String,
        val autoSync: Boolean
    ) : WorkspaceAction
    data class UpdateThemeSettings(val themeMode: ThemeMode, val useDynamicColor: Boolean) : WorkspaceAction

    // Hierarchy CRUD, Reordering & Organization
    data class CreateNotebook(val title: String, val subtitle: String, val accent: SectionAccentKey) : WorkspaceAction
    data class UpdateActiveNotebook(val title: String, val subtitle: String, val accent: SectionAccentKey) : WorkspaceAction
    data class ReorderActiveNotebook(val delta: Int) : WorkspaceAction
    data object DeleteActiveNotebook : WorkspaceAction
    data class CreateSection(val title: String, val accent: SectionAccentKey) : WorkspaceAction
    data class UpdateActiveSection(val title: String, val accent: SectionAccentKey) : WorkspaceAction
    data class ReorderActiveSection(val delta: Int) : WorkspaceAction
    data object DeleteActiveSection : WorkspaceAction
    data class CreatePage(val title: String, val asSubpageOfActive: Boolean = false) : WorkspaceAction
    data class CreatePageInstant(val asSubpageOfActive: Boolean = false) : WorkspaceAction
    data class TogglePageSubpageIndent(val pageId: String) : WorkspaceAction
    data class ToggleSubpageCollapse(val parentPageId: String) : WorkspaceAction
    data class MoveActivePageToSection(val targetSectionId: String) : WorkspaceAction
    data class ImportMarkdownPage(val markdown: String) : WorkspaceAction

    // Active Page Metadata & History
    data class UpdateActivePageTitle(val newTitle: String) : WorkspaceAction
    data object ToggleActivePagePin : WorkspaceAction
    data class TogglePinPage(val pageId: String) : WorkspaceAction
    data class RequestDeletePage(val pageId: String) : WorkspaceAction
    data class DeletePage(val pageId: String) : WorkspaceAction
    data object Undo : WorkspaceAction
    data object Redo : WorkspaceAction

    // Block Selection, Cursor Focus & Ordering
    data class SetActiveBlock(val blockId: String?) : WorkspaceAction
    data class UpdateActiveSelection(val blockId: String, val selectionStart: Int, val selectionEnd: Int) : WorkspaceAction
    data class ConsumeFocusRequest(val requestToken: Long) : WorkspaceAction
    data object FocusOrAppendTrailingParagraph : WorkspaceAction
    data class MoveBlock(val blockId: String, val delta: Int) : WorkspaceAction
    data class DeleteBlock(val blockId: String) : WorkspaceAction

    // Word-Style Keyboard Flow & Slash Commands
    data class EditParagraphText(val blockId: String, val newText: String, val cursorOffset: Int) : WorkspaceAction
    data class SplitBlockAtCursor(val blockId: String, val cursorOffset: Int) : WorkspaceAction
    data class BackspaceAtBlockStart(val blockId: String) : WorkspaceAction
    data class ListEnterAtItem(val blockId: String, val itemIndex: Int) : WorkspaceAction
    data class ListBackspaceOnEmptyItem(val blockId: String, val itemIndex: Int) : WorkspaceAction
    data class ExecuteSlashCommand(val blockId: String, val command: SlashCommandKind) : WorkspaceAction

    // Block Insertion
    data class InsertHeading(val level: Int = 2, val afterBlockId: String? = null) : WorkspaceAction
    data class InsertParagraph(val afterBlockId: String? = null) : WorkspaceAction
    data class InsertChecklist(val afterBlockId: String? = null) : WorkspaceAction
    data class InsertBulletList(val isNumbered: Boolean = false, val afterBlockId: String? = null) : WorkspaceAction
    data class InsertTable(val afterBlockId: String? = null) : WorkspaceAction
    data class InsertCallout(val afterBlockId: String? = null) : WorkspaceAction
    data class InsertCode(val afterBlockId: String? = null) : WorkspaceAction
    data class InsertDivider(val afterBlockId: String? = null) : WorkspaceAction
    data class InsertAttachment(
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val dataBase64: String,
        val afterBlockId: String? = null
    ) : WorkspaceAction

    // Rich Spans & Block Formatting Mutations
    data class UpdateBlockText(val blockId: String, val newText: String) : WorkspaceAction
    data class UpdateAttachmentCaption(val blockId: String, val caption: String) : WorkspaceAction
    data class ToggleSelectionSpan(
        val kind: RichSpanKind,
        val url: String? = null,
        val blockId: String? = null,
        val selStart: Int? = null,
        val selEnd: Int? = null
    ) : WorkspaceAction
    data class ToggleParagraphStyle(val blockId: String, val style: String) : WorkspaceAction
    data object ClearActiveParagraphFormatting : WorkspaceAction
    data class ConvertActiveBlock(val command: SlashCommandKind) : WorkspaceAction
    data class UpdateHeadingLevel(val blockId: String, val level: Int) : WorkspaceAction

    // Checklist Mutations
    data class UpdateChecklistTitle(val blockId: String, val title: String) : WorkspaceAction
    data class ToggleChecklistItem(val blockId: String, val itemId: String) : WorkspaceAction
    data class InsertChecklistItemAfter(val blockId: String, val afterItemId: String? = null) : WorkspaceAction
    data class UpdateChecklistItemText(val blockId: String, val itemId: String, val text: String) : WorkspaceAction
    data class DeleteChecklistItem(val blockId: String, val itemId: String) : WorkspaceAction

    // Bullet / Numbered List Mutations
    data class ToggleBulletListKind(val blockId: String) : WorkspaceAction
    data class InsertBulletListItemAfter(val blockId: String, val afterItemIndex: Int? = null) : WorkspaceAction
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
