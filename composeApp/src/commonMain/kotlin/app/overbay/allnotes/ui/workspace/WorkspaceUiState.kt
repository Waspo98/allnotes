package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.data.SyncStatus
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.NoteSectionDisplayComparator
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
    val sectionAccent: SectionAccentKey,
    val matchSnippet: String? = null
)

enum class ActiveDialog {
    NONE,
    NEW_NOTEBOOK,
    EDIT_NOTEBOOK,
    CONFIRM_DELETE_NOTEBOOK,
    NEW_SECTION,
    EDIT_SECTION,
    MANAGE_SECTIONS,
    CONFIRM_DELETE_SECTION,
    CONFIRM_DELETE_PAGE,
    MOVE_PAGE,
    INSERT_LINK,
    MARKDOWN_EXPORT,
    MARKDOWN_IMPORT,
    SYNC_SETTINGS,
    QUICK_JUMP
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
    val isBlockInsertSheetOpen: Boolean = false,
    val isMobileWritingPageStripOpen: Boolean = false,
    val activeDialog: ActiveDialog = ActiveDialog.NONE,
    val editingSectionId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_READY,
    val syncEndpointUrl: String = "https://notes.overbay.app/api/v1/sync",
    val useRemoteHttpSync: Boolean = true,
    val preferences: WorkspacePreferences = WorkspacePreferences(),
    val statusBannerMessage: String? = null,
    val statusBannerActionLabel: String? = null,
    val statusBannerAction: WorkspaceAction? = null,
    val revision: Long = 1L,
    val searchFocusRequestToken: Long = 0L,
    val titleFocusRequestToken: Long = 0L,
    val previewAttachment: DocumentBlock.Attachment? = null,
    val pendingDeletePageId: String? = null
) {
    val activeNotebook: Notebook? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        notebooks.firstOrNull { it.id == selectedNotebookId } ?: notebooks.firstOrNull()
    }

    val sectionsForNotebook: List<NoteSection> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val nbId = activeNotebook?.id ?: return@lazy emptyList()
        sections.filter { it.notebookId == nbId }.sortedWith(NoteSectionDisplayComparator)
    }

    val activeSection: NoteSection? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        sectionsForNotebook.firstOrNull { it.id == selectedSectionId }
            ?: sectionsForNotebook.firstOrNull()
    }

    val parentPageIdsWithChildren: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val secId = activeSection?.id ?: return@lazy emptySet()
        val secPages = pages.filter { it.sectionId == secId }
        val validIds = secPages.mapTo(HashSet()) { it.id }
        secPages.mapNotNullTo(HashSet()) { p ->
            p.parentPageId?.takeIf { it in validIds }
        }
    }

    val pagesForSection: List<NotePage> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val secId = activeSection?.id ?: return@lazy emptyList()
        val q = searchQuery.trim().lowercase()
        val comparator = compareByDescending<NotePage> { it.isPinned }.thenByDescending { it.updatedAtEpochMs }
        val filtered = pages
            .filter { it.sectionId == secId }
            .filter { page ->
                if (q.isEmpty()) true
                else PageSnippetExtractor.pageMatchesQuery(page, q)
            }
            .sortedWith(comparator)

        if (q.isNotEmpty() || filtered.all { it.parentPageId == null }) {
            return@lazy filtered
        }

        val byId = filtered.associateBy { it.id }
        val roots = filtered.filter { it.parentPageId == null || it.parentPageId !in byId }
        val childrenByParent = filtered
            .filter { it.parentPageId != null && it.parentPageId in byId }
            .groupBy { it.parentPageId!! }

        val ordered = ArrayList<NotePage>(filtered.size)
        val visitedIds = HashSet<String>(filtered.size)
        val hiddenChildIds = HashSet<String>()

        fun markHidden(pageId: String) {
            if (!visitedIds.add(pageId)) return
            hiddenChildIds.add(pageId)
            childrenByParent[pageId].orEmpty().forEach { markHidden(it.id) }
        }

        fun appendTree(page: NotePage) {
            if (!visitedIds.add(page.id)) return
            ordered.add(page)
            val children = childrenByParent[page.id].orEmpty()
            if (page.id in collapsedParentPageIds) {
                children.forEach { markHidden(it.id) }
            } else {
                children.forEach { child -> appendTree(child) }
            }
        }
        roots.forEach { appendTree(it) }
        filtered.forEach { page ->
            if (page.id !in hiddenChildIds && page.id !in visitedIds) {
                appendTree(page)
            }
        }
        ordered
    }

    val globalSearchResults: List<GlobalSearchHit> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) return@lazy emptyList()
        val activeSecId = activeSection?.id
        val sectionMap = sections.associateBy { it.id }
        val notebookMap = notebooks.associateBy { it.id }
        val comparator = compareByDescending<NotePage> { it.isPinned }.thenByDescending { it.updatedAtEpochMs }
        pages
            .asSequence()
            .filter { it.sectionId != activeSecId }
            .filter { page -> PageSnippetExtractor.pageMatchesQuery(page, q) }
            .sortedWith(comparator)
            .mapNotNull { page ->
                val sec = sectionMap[page.sectionId] ?: return@mapNotNull null
                val nb = notebookMap[sec.notebookId] ?: return@mapNotNull null
                GlobalSearchHit(
                    page = page,
                    sectionTitle = sec.title,
                    notebookTitle = nb.title,
                    sectionAccent = sec.accent,
                    matchSnippet = PageSnippetExtractor.extractExcerpt(page, q)
                )
            }
            .toList()
    }

    val activePage: NotePage? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        pagesForSection.firstOrNull { it.id == selectedPageId }
            ?: pagesForSection.firstOrNull()
    }

    val activeBlock: DocumentBlock? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        activePage?.blocks?.firstOrNull { it.id == activeBlockId }
    }

    val wordCount: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val page = activePage ?: return@lazy 0
        PageWordCounter.countWords(page)
    }

    val readingTimeMinutes: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val count = wordCount
        if (count <= 0) 0 else maxOf(1, (count + 199) / 200)
    }
}

internal object PageWordCounter {
    private fun wordsIn(text: String): Int {
        var count = 0
        var inWord = false
        for (i in text.indices) {
            if (text[i].isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                count++
            }
        }
        return count
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

internal object PageSnippetExtractor {
    fun pageMatchesQuery(page: NotePage, lowerQuery: String): Boolean =
        page.title.lowercase().contains(lowerQuery) ||
            page.blocks.any { BlockMutationEngine.blockContainsQuery(it, lowerQuery) }

    fun extractExcerpt(page: NotePage, rawQuery: String): String? {
        val q = rawQuery.trim().lowercase()
        if (q.isEmpty()) return null
        val matchedText = page.blocks.firstNotNullOfOrNull { block ->
            firstMatchingText(block, q)
        } ?: return null

        val hit = matchedText.lowercase().indexOf(q)
        if (hit < 0) return null
        val start = maxOf(0, hit - 28)
        val end = minOf(matchedText.length, hit + q.length + 52)
        val window = buildString(end - start + 4) {
            if (start > 0) append('…')
            var prevSpace = false
            for (i in start until end) {
                val ch = matchedText[i]
                if (ch.isWhitespace()) {
                    if (!prevSpace) {
                        append(' ')
                        prevSpace = true
                    }
                } else {
                    append(ch)
                    prevSpace = false
                }
            }
            if (end < matchedText.length) append('…')
        }.trim()
        return window.ifEmpty { null }
    }

    private fun firstMatchingText(block: DocumentBlock, q: String): String? = when (block) {
        is DocumentBlock.Heading -> block.text.takeIf { it.lowercase().contains(q) }
        is DocumentBlock.Paragraph -> block.text.takeIf { it.lowercase().contains(q) }
        is DocumentBlock.Checklist -> sequenceOf(block.title)
            .plus(block.items.asSequence().map { it.text })
            .firstOrNull { it.lowercase().contains(q) }
        is DocumentBlock.BulletList -> block.items.firstOrNull { it.lowercase().contains(q) }
        is DocumentBlock.Table -> block.headers.asSequence()
            .plus(block.rows.asSequence().flatMap { it.asSequence() })
            .firstOrNull { it.lowercase().contains(q) }
        is DocumentBlock.Callout -> sequenceOf(block.title, block.text)
            .firstOrNull { it.lowercase().contains(q) }
        is DocumentBlock.CodeSnippet -> block.code.takeIf { it.lowercase().contains(q) }
            ?: block.language.takeIf { it.lowercase().contains(q) }
        is DocumentBlock.Divider -> null
        is DocumentBlock.Attachment -> block.caption.takeIf { it.lowercase().contains(q) }
            ?: block.fileName.takeIf { it.lowercase().contains(q) }
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
                secId = effectiveSnap.sections
                    .filter { it.notebookId == nbId }
                    .sortedWith(NoteSectionDisplayComparator)
                    .firstOrNull()?.id.orEmpty()
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
            secId = effectiveSnap.sections
                .filter { it.notebookId == nbId }
                .sortedWith(NoteSectionDisplayComparator)
                .firstOrNull()?.id.orEmpty()
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

        val pageChanged = pageId != current.selectedPageId
        val retainedActiveBlockId = if (pageChanged) {
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
            isMobileWritingPageStripOpen = if (pageChanged) false else current.isMobileWritingPageStripOpen,
            revision = maxOf(effectiveSnap.revision, current.revision)
        )
    }
}
