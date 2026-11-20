package app.overbay.allnotes.data

import app.overbay.allnotes.model.DeletedEntity
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.EntityKind
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.NoteSectionDisplayComparator
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspaceSnapshot

/**
 * Pure snapshot transformation helpers for Notebook and Section hierarchy mutations.
 */
internal object WorkspaceHierarchyMutator {

    fun upsertTombstones(
        existing: List<DeletedEntity>,
        additions: List<DeletedEntity>
    ): List<DeletedEntity> {
        if (additions.isEmpty()) return existing
        val map = LinkedHashMap<Pair<String, EntityKind>, DeletedEntity>(existing.size + additions.size)
        for (item in existing + additions) {
            val key = item.id to item.kind
            val prev = map[key]
            if (prev == null || item.deletedAtEpochMs >= prev.deletedAtEpochMs) map[key] = item
        }
        return map.values.toList()
    }

    fun createNotebook(
        current: WorkspaceSnapshot,
        title: String,
        subtitle: String,
        accent: SectionAccentKey,
        now: Long,
        nextId: (String) -> String
    ): Pair<Notebook, WorkspaceSnapshot> {
        val cleanTitle = title.trim().ifEmpty { "Untitled Notebook" }
        val newNotebook = Notebook(
            id = nextId("nb"),
            title = cleanTitle,
            subtitle = subtitle.trim(),
            accent = accent,
            sortOrder = current.notebooks.size,
            updatedAtEpochMs = now
        )
        val defaultSection = NoteSection(
            id = nextId("sec"),
            notebookId = newNotebook.id,
            title = "General Notes",
            accent = accent,
            sortOrder = 0,
            updatedAtEpochMs = now
        )
        val starterPage = NotePage(
            id = nextId("page"),
            sectionId = defaultSection.id,
            title = "Welcome to $cleanTitle",
            blocks = listOf(
                DocumentBlock.Heading(id = nextId("blk"), text = cleanTitle, level = 2),
                DocumentBlock.Paragraph(id = nextId("blk"), text = "Start typing or add structured blocks below.")
            ),
            updatedAtEpochMs = now
        )
        val nextSnapshot = current.copy(
            notebooks = current.notebooks + newNotebook,
            sections = current.sections + defaultSection,
            pages = current.pages + starterPage
        )
        return newNotebook to nextSnapshot
    }

    fun updateNotebook(
        current: WorkspaceSnapshot,
        notebookId: String,
        title: String,
        subtitle: String,
        accent: SectionAccentKey,
        now: Long
    ): WorkspaceSnapshot {
        val cleanTitle = title.trim().ifEmpty { "Untitled Notebook" }
        return current.copy(
            notebooks = current.notebooks.map { nb ->
                if (nb.id == notebookId) {
                    nb.copy(
                        title = cleanTitle,
                        subtitle = subtitle.trim(),
                        accent = accent,
                        updatedAtEpochMs = now
                    )
                } else nb
            }
        )
    }

    fun reorderNotebook(
        current: WorkspaceSnapshot,
        notebookId: String,
        delta: Int,
        now: Long
    ): WorkspaceSnapshot {
        val ordered = current.notebooks.sortedBy { it.sortOrder }.toMutableList()
        val idx = ordered.indexOfFirst { it.id == notebookId }
        if (idx < 0) return current
        val targetIdx = (idx + delta).coerceIn(0, ordered.lastIndex)
        if (targetIdx == idx) return current
        val moved = ordered.removeAt(idx)
        ordered.add(targetIdx, moved)
        val reindexed = ordered.mapIndexed { i, nb ->
            if (nb.sortOrder != i || nb.id == notebookId) {
                nb.copy(sortOrder = i, updatedAtEpochMs = now)
            } else nb
        }
        return current.copy(notebooks = reindexed)
    }

    fun deleteNotebook(
        current: WorkspaceSnapshot,
        notebookId: String,
        now: Long
    ): WorkspaceSnapshot {
        val remainingNotebooks = current.notebooks.filterNot { it.id == notebookId }
        val removedSections = current.sections.filter { it.notebookId == notebookId }
        val removedSectionIds = removedSections.map { it.id }.toSet()
        val removedPages = current.pages.filter { it.sectionId in removedSectionIds }
        val remainingSections = current.sections.filterNot { it.notebookId == notebookId }
        val remainingPages = current.pages.filterNot { it.sectionId in removedSectionIds }

        val newTombstones = buildList {
            if (current.notebooks.any { it.id == notebookId }) {
                add(DeletedEntity(notebookId, EntityKind.NOTEBOOK, now))
            }
            removedSections.forEach { add(DeletedEntity(it.id, EntityKind.SECTION, now)) }
            removedPages.forEach { add(DeletedEntity(it.id, EntityKind.PAGE, now)) }
        }

        return current.copy(
            notebooks = remainingNotebooks,
            sections = remainingSections,
            pages = remainingPages,
            tombstones = upsertTombstones(current.tombstones, newTombstones)
        )
    }

    fun createSection(
        current: WorkspaceSnapshot,
        notebookId: String,
        title: String,
        accent: SectionAccentKey,
        now: Long,
        nextId: (String) -> String
    ): Pair<NoteSection, WorkspaceSnapshot> {
        val cleanTitle = title.trim().ifEmpty { "New Section" }
        val newSection = NoteSection(
            id = nextId("sec"),
            notebookId = notebookId,
            title = cleanTitle,
            accent = accent,
            sortOrder = current.sections.count { it.notebookId == notebookId },
            updatedAtEpochMs = now
        )
        val firstPage = NotePage(
            id = nextId("page"),
            sectionId = newSection.id,
            title = "Untitled Page",
            blocks = listOf(DocumentBlock.Paragraph(id = nextId("blk"), text = "")),
            updatedAtEpochMs = now
        )
        val nextSnapshot = current.copy(
            sections = current.sections + newSection,
            pages = current.pages + firstPage
        )
        return newSection to nextSnapshot
    }

    fun updateSection(
        current: WorkspaceSnapshot,
        sectionId: String,
        title: String,
        accent: SectionAccentKey,
        isPinned: Boolean?,
        now: Long
    ): WorkspaceSnapshot {
        val cleanTitle = title.trim().ifEmpty { "Untitled Section" }
        val updated = current.sections.map { sec ->
            if (sec.id == sectionId) {
                sec.copy(
                    title = cleanTitle,
                    accent = accent,
                    isPinned = isPinned ?: sec.isPinned,
                    updatedAtEpochMs = now
                )
            } else sec
        }
        val target = updated.firstOrNull { it.id == sectionId } ?: return current
        val ordered = updated.filter { it.notebookId == target.notebookId }.sortedWith(NoteSectionDisplayComparator)
        val reindexedById = ordered.mapIndexed { i, sec ->
            sec.id to if (sec.sortOrder != i || sec.id == sectionId) {
                sec.copy(sortOrder = i, updatedAtEpochMs = now)
            } else sec
        }.toMap()
        return current.copy(sections = updated.map { reindexedById[it.id] ?: it })
    }

    fun toggleSectionPin(
        current: WorkspaceSnapshot,
        sectionId: String,
        now: Long
    ): WorkspaceSnapshot = mutateNotebookSectionPartitions(current, sectionId, now) { target, pinned, unpinned ->
        val updated = target.copy(isPinned = !target.isPinned, updatedAtEpochMs = now)
        pinned.removeAll { it.id == sectionId }
        unpinned.removeAll { it.id == sectionId }
        if (updated.isPinned) pinned.add(updated) else unpinned.add(0, updated)
        true
    }

    fun reorderSection(
        current: WorkspaceSnapshot,
        sectionId: String,
        delta: Int,
        now: Long
    ): WorkspaceSnapshot = mutateNotebookSectionPartitions(current, sectionId, now) { target, pinned, unpinned ->
        val partition = if (target.isPinned) pinned else unpinned
        val idx = partition.indexOfFirst { it.id == sectionId }
        val targetIdx = if (idx >= 0) (idx + delta).coerceIn(0, partition.lastIndex) else -1
        if (idx < 0 || targetIdx == idx) return@mutateNotebookSectionPartitions false
        partition.add(targetIdx, partition.removeAt(idx))
        true
    }

    private inline fun mutateNotebookSectionPartitions(
        current: WorkspaceSnapshot,
        sectionId: String,
        now: Long,
        transform: (
            target: NoteSection,
            pinned: MutableList<NoteSection>,
            unpinned: MutableList<NoteSection>
        ) -> Boolean
    ): WorkspaceSnapshot {
        val target = current.sections.firstOrNull { it.id == sectionId } ?: return current
        val siblings = current.sections.filter { it.notebookId == target.notebookId }
        val pinned = siblings.filter { it.isPinned }.sortedWith(NoteSectionDisplayComparator).toMutableList()
        val unpinned = siblings.filter { !it.isPinned }.sortedWith(NoteSectionDisplayComparator).toMutableList()
        if (!transform(target, pinned, unpinned)) return current
        val reindexedById = (pinned + unpinned).mapIndexed { i, sec ->
            val changed = sec.id == sectionId || sec.sortOrder != i
            sec.id to if (changed) sec.copy(sortOrder = i, updatedAtEpochMs = now) else sec
        }.toMap()
        return current.copy(sections = current.sections.map { reindexedById[it.id] ?: it })
    }

    fun deleteSection(
        current: WorkspaceSnapshot,
        sectionId: String,
        now: Long
    ): WorkspaceSnapshot {
        val removedPages = current.pages.filter { it.sectionId == sectionId }
        val newTombstones = buildList {
            if (current.sections.any { it.id == sectionId }) {
                add(DeletedEntity(sectionId, EntityKind.SECTION, now))
            }
            removedPages.forEach { add(DeletedEntity(it.id, EntityKind.PAGE, now)) }
        }
        return current.copy(
            sections = current.sections.filterNot { it.id == sectionId },
            pages = current.pages.filterNot { it.sectionId == sectionId },
            tombstones = upsertTombstones(current.tombstones, newTombstones)
        )
    }
}
