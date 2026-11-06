package app.overbay.allnotes.data

import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.WorkspaceSnapshot

/**
 * Conflict-safe workspace snapshot merger using Last-Writer-Wins (`updatedAtEpochMs`)
 * per entity (Notebooks, Sections, Pages) and monotonic revision progression.
 */
object WorkspaceMerger {

    fun merge(base: WorkspaceSnapshot, incoming: WorkspaceSnapshot): WorkspaceSnapshot {
        val nextRevision = maxOf(base.revision, incoming.revision) + 1L

        // If incoming is strictly based on the latest or newer revision, incoming structure
        // (including intentional deletions) is authoritative, with newer timestamps winning on conflicts.
        if (incoming.revision >= base.revision) {
            val baseNotebooksById = base.notebooks.associateBy { it.id }
            val baseSectionsById = base.sections.associateBy { it.id }
            val basePagesById = base.pages.associateBy { it.id }

            val notebooks = incoming.notebooks.map { inc ->
                val existing = baseNotebooksById[inc.id]
                if (existing != null && existing.updatedAtEpochMs > inc.updatedAtEpochMs) existing else inc
            }
            val sections = incoming.sections.map { inc ->
                val existing = baseSectionsById[inc.id]
                if (existing != null && existing.updatedAtEpochMs > inc.updatedAtEpochMs) existing else inc
            }
            val pages = incoming.pages.map { inc ->
                val existing = basePagesById[inc.id]
                if (existing != null && existing.updatedAtEpochMs > inc.updatedAtEpochMs) existing else inc
            }

            return WorkspaceSnapshot(
                notebooks = notebooks,
                sections = sections,
                pages = pages,
                revision = nextRevision
            )
        }

        // If incoming had a stale revision, perform an additive union so neither client loses creations
        val mergedNotebooks = mergeById(base.notebooks, incoming.notebooks, { it.id }, { it.updatedAtEpochMs })
        val validNotebookIds = mergedNotebooks.map { it.id }.toSet()

        val mergedSections = mergeById(base.sections, incoming.sections, { it.id }, { it.updatedAtEpochMs })
            .filter { it.notebookId in validNotebookIds }
        val validSectionIds = mergedSections.map { it.id }.toSet()

        val mergedPages = mergeById(base.pages, incoming.pages, { it.id }, { it.updatedAtEpochMs })
            .filter { it.sectionId in validSectionIds }

        return WorkspaceSnapshot(
            notebooks = mergedNotebooks,
            sections = mergedSections,
            pages = mergedPages,
            revision = nextRevision
        )
    }

    private fun <T> mergeById(
        baseList: List<T>,
        incomingList: List<T>,
        idSelector: (T) -> String,
        timestampSelector: (T) -> Long
    ): List<T> {
        val map = LinkedHashMap<String, T>()
        for (item in baseList) {
            map[idSelector(item)] = item
        }
        for (inc in incomingList) {
            val id = idSelector(inc)
            val existing = map[id]
            if (existing == null || timestampSelector(inc) >= timestampSelector(existing)) {
                map[id] = inc
            }
        }
        return map.values.toList()
    }
}
