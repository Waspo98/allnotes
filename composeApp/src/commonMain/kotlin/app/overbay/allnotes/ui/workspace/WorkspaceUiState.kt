package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.data.SyncStatus
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspacePreferences
import app.overbay.allnotes.model.WorkspaceSnapshot

data class EditorFocusRequest(
    val blockId: String,
    val subItemIndex: Int = -1,
    val cursorOffset: Int = Int.MAX_VALUE,
    val requestToken: Long = 0L
)

data class GlobalSearchHit(
    val page: NotePage,
    val sectionTitle: String,
    val notebookTitle: String,
    val sectionAccent: SectionAccentKey
)

enum class ActiveDialog {
    NONE,
    NEW_NOTEBOOK,
    EDIT_NOTEBOOK,
    CONFIRM_DELETE_NOTEBOOK,
    NEW_SECTION,
    EDIT_SECTION,
    CONFIRM_DELETE_SECTION,
    NEW_PAGE,
    MOVE_PAGE,
    INSERT_LINK,
    MARKDOWN_EXPORT,
    MARKDOWN_IMPORT,
    SYNC_SETTINGS
}

data class WorkspaceUiState(
    val notebooks: List<Notebook> = emptyList(),
    val sections: List<NoteSection> = emptyList(),
    val pages: List<NotePage> = emptyList(),
    val selectedNotebookId: String = "",
    val selectedSectionId: String = "",
    val selectedPageId: String = "",
    val activeBlockId: String? = null,
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val focusRequest: EditorFocusRequest? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val searchQuery: String = "",
    val collapsedParentPageIds: Set<String> = emptySet(),
    val isSidebarOpen: Boolean = true,
    val isMobileHierarchySheetOpen: Boolean = false,
    val isPageMenuOpen: Boolean = false,
    val isMobileInsertMenuOpen: Boolean = false,
    val activeDialog: ActiveDialog = ActiveDialog.NONE,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_READY,
    val syncEndpointUrl: String = "https://notes.overbay.app/api/v1/sync",
    val useRemoteHttpSync: Boolean = false,
    val preferences: WorkspacePreferences = WorkspacePreferences(),
    val statusBannerMessage: String? = null,
    val revision: Long = 1L
) {
    val activeNotebook: Notebook?
        get() = notebooks.firstOrNull { it.id == selectedNotebookId } ?: notebooks.firstOrNull()

    val sectionsForNotebook: List<NoteSection>
        get() {
            val nbId = activeNotebook?.id ?: return emptyList()
            return sections.filter { it.notebookId == nbId }.sortedBy { it.sortOrder }
        }

    val activeSection: NoteSection?
        get() = sectionsForNotebook.firstOrNull { it.id == selectedSectionId }
            ?: sectionsForNotebook.firstOrNull()

    val parentPageIdsWithChildren: Set<String>
        get() {
            val secId = activeSection?.id ?: return emptySet()
            val secPages = pages.filter { it.sectionId == secId }
            val validIds = secPages.mapTo(HashSet()) { it.id }
            return secPages.mapNotNullTo(HashSet()) { p ->
                p.parentPageId?.takeIf { it in validIds }
            }
        }

    val pagesForSection: List<NotePage>
        get() {
            val secId = activeSection?.id ?: return emptyList()
            val q = searchQuery.trim().lowercase()
            val comparator = compareByDescending<NotePage> { it.isPinned }.thenByDescending { it.updatedAtEpochMs }
            val filtered = pages
                .filter { it.sectionId == secId }
                .filter { page ->
                    if (q.isEmpty()) true
                    else page.title.lowercase().contains(q) ||
                        page.blocks.any { BlockMutationEngine.blockContainsQuery(it, q) }
                }
                .sortedWith(comparator)

            if (q.isNotEmpty() || filtered.all { it.parentPageId == null }) {
                return filtered
            }

            val byId = filtered.associateBy { it.id }
            val roots = filtered.filter { it.parentPageId == null || it.parentPageId !in byId }
            val childrenByParent = filtered
                .filter { it.parentPageId != null && it.parentPageId in byId }
                .groupBy { it.parentPageId!! }

            val ordered = mutableListOf<NotePage>()
            val hiddenChildIds = HashSet<String>()
            fun appendTree(page: NotePage) {
                if (ordered.any { it.id == page.id }) return
                ordered.add(page)
                val children = childrenByParent[page.id].orEmpty()
                if (page.id in collapsedParentPageIds) {
                    children.forEach { hiddenChildIds.add(it.id) }
                } else {
                    children.forEach { child -> appendTree(child) }
                }
            }
            roots.forEach { appendTree(it) }
            filtered.forEach {
                if (it.id !in hiddenChildIds && ordered.none { p -> p.id == it.id }) {
                    ordered.add(it)
                }
            }
            return ordered
        }

    val globalSearchResults: List<GlobalSearchHit>
        get() {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) return emptyList()
            val activeSecId = activeSection?.id
            val sectionMap = sections.associateBy { it.id }
            val notebookMap = notebooks.associateBy { it.id }
            val comparator = compareByDescending<NotePage> { it.isPinned }.thenByDescending { it.updatedAtEpochMs }
            return pages
                .asSequence()
                .filter { it.sectionId != activeSecId }
                .filter { page ->
                    page.title.lowercase().contains(q) ||
                        page.blocks.any { BlockMutationEngine.blockContainsQuery(it, q) }
                }
                .sortedWith(comparator)
                .mapNotNull { page ->
                    val sec = sectionMap[page.sectionId] ?: return@mapNotNull null
                    val nb = notebookMap[sec.notebookId] ?: return@mapNotNull null
                    GlobalSearchHit(
                        page = page,
                        sectionTitle = sec.title,
                        notebookTitle = nb.title,
                        sectionAccent = sec.accent
                    )
                }
                .toList()
        }

    val activePage: NotePage?
        get() = pagesForSection.firstOrNull { it.id == selectedPageId }
            ?: pagesForSection.firstOrNull()

    val activeBlock: DocumentBlock?
        get() = activePage?.blocks?.firstOrNull { it.id == activeBlockId }

    val wordCount: Int
        get() {
            val page = activePage ?: return 0
            return PageWordCounter.countWords(page)
        }

    val readingTimeMinutes: Int
        get() {
            val count = wordCount
            return if (count <= 0) 0 else maxOf(1, (count + 199) / 200)
        }
}

internal object PageWordCounter {
    private val whitespaceRegex = Regex("\\s+")

    private fun wordsIn(text: String): Int {
        val t = text.trim()
        return if (t.isEmpty()) 0 else t.split(whitespaceRegex).size
    }

    fun countWords(page: NotePage): Int {
        var total = wordsIn(page.title)
        for (block in page.blocks) {
            total += when (block) {
                is DocumentBlock.Heading -> wordsIn(block.text)
                is DocumentBlock.Paragraph -> wordsIn(block.text)
                is DocumentBlock.Checklist -> wordsIn(block.title) + block.items.sumOf { wordsIn(it.text) }
                is DocumentBlock.BulletList -> block.items.sumOf { wordsIn(it) }
                is DocumentBlock.Table -> block.headers.sumOf { wordsIn(it) } +
                    block.rows.sumOf { r -> r.sumOf { wordsIn(it) } }
                is DocumentBlock.Callout -> wordsIn(block.title) + wordsIn(block.text)
                is DocumentBlock.CodeSnippet -> wordsIn(block.code)
                is DocumentBlock.Divider -> 0
                is DocumentBlock.Attachment -> wordsIn(block.caption)
            }
        }
        return total
    }
}

internal object HierarchySelectionResolver {
    fun resolveSyncedState(
        current: WorkspaceUiState,
        effectiveSnap: WorkspaceSnapshot,
        targetNotebookId: String?,
        targetSectionId: String?,
        targetPageId: String?
    ): WorkspaceUiState {
        var nbId = targetNotebookId ?: current.selectedNotebookId
        var secId = targetSectionId ?: current.selectedSectionId
        var pageId = targetPageId ?: current.selectedPageId

        if (targetPageId != null) {
            val matchedPage = effectiveSnap.pages.firstOrNull { it.id == targetPageId }
            if (matchedPage != null) {
                pageId = matchedPage.id
                secId = matchedPage.sectionId
                val matchedSec = effectiveSnap.sections.firstOrNull { it.id == secId }
                if (matchedSec != null) nbId = matchedSec.notebookId
            }
        } else if (targetSectionId != null) {
            val matchedSec = effectiveSnap.sections.firstOrNull { it.id == targetSectionId }
            if (matchedSec != null) {
                secId = matchedSec.id
                nbId = matchedSec.notebookId
                pageId = effectiveSnap.pages.firstOrNull { it.sectionId == secId }?.id.orEmpty()
            }
        } else if (targetNotebookId != null) {
            val matchedNb = effectiveSnap.notebooks.firstOrNull { it.id == targetNotebookId }
            if (matchedNb != null) {
                nbId = matchedNb.id
                secId = effectiveSnap.sections.firstOrNull { it.notebookId == nbId }?.id.orEmpty()
                pageId = effectiveSnap.pages.firstOrNull { it.sectionId == secId }?.id.orEmpty()
            }
        } else if (pageId.isNotEmpty()) {
            val matchedPage = effectiveSnap.pages.firstOrNull { it.id == pageId }
            if (matchedPage != null) {
                secId = matchedPage.sectionId
                val matchedSec = effectiveSnap.sections.firstOrNull { it.id == secId }
                if (matchedSec != null) nbId = matchedSec.notebookId
            }
        }

        if (effectiveSnap.notebooks.none { it.id == nbId }) {
            nbId = effectiveSnap.notebooks.firstOrNull()?.id.orEmpty()
        }
        if (effectiveSnap.sections.none { it.id == secId && it.notebookId == nbId }) {
            secId = effectiveSnap.sections.firstOrNull { it.notebookId == nbId }?.id.orEmpty()
        }
        if (effectiveSnap.pages.none { it.id == pageId && it.sectionId == secId }) {
            val pageElsewhere = effectiveSnap.pages.firstOrNull { it.id == pageId }
            if (pageElsewhere != null) {
                secId = pageElsewhere.sectionId
                nbId = effectiveSnap.sections.firstOrNull { it.id == secId }?.notebookId ?: nbId
            } else {
                pageId = effectiveSnap.pages.firstOrNull { it.sectionId == secId }?.id.orEmpty()
            }
        }

        val retainedActiveBlockId = if (pageId != current.selectedPageId) {
            null
        } else {
            val activePageBlocks = effectiveSnap.pages.firstOrNull { it.id == pageId }?.blocks.orEmpty()
            if (activePageBlocks.any { it.id == current.activeBlockId }) current.activeBlockId else null
        }

        return current.copy(
            notebooks = effectiveSnap.notebooks,
            sections = effectiveSnap.sections,
            pages = effectiveSnap.pages,
            selectedNotebookId = nbId,
            selectedSectionId = secId,
            selectedPageId = pageId,
            activeBlockId = retainedActiveBlockId,
            revision = maxOf(effectiveSnap.revision, current.revision)
        )
    }
}
