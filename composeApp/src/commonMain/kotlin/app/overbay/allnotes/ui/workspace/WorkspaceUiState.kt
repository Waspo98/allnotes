package app.overbay.allnotes.ui.workspace

import app.overbay.allnotes.data.SyncStatus
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.WorkspaceSnapshot

enum class ActiveDialog {
    NONE,
    NEW_NOTEBOOK,
    EDIT_NOTEBOOK,
    NEW_SECTION,
    EDIT_SECTION,
    NEW_PAGE,
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
    val searchQuery: String = "",
    val isSidebarOpen: Boolean = true,
    val isMobileHierarchySheetOpen: Boolean = false,
    val activeDialog: ActiveDialog = ActiveDialog.NONE,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_READY,
    val syncEndpointUrl: String = "https://notes.overbay.app/api/v1/sync",
    val useRemoteHttpSync: Boolean = false,
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
            fun appendTree(page: NotePage) {
                if (ordered.any { it.id == page.id }) return
                ordered.add(page)
                childrenByParent[page.id]?.forEach { child -> appendTree(child) }
            }
            roots.forEach { appendTree(it) }
            filtered.forEach { if (ordered.none { p -> p.id == it.id }) ordered.add(it) }
            return ordered
        }

    val activePage: NotePage?
        get() = pagesForSection.firstOrNull { it.id == selectedPageId }
            ?: pagesForSection.firstOrNull()

    val activeBlock: DocumentBlock?
        get() = activePage?.blocks?.firstOrNull { it.id == activeBlockId }
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
