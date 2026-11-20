package app.overbay.allnotes.data

import app.overbay.allnotes.model.DeletedEntity
import app.overbay.allnotes.model.EntityKind
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.WorkspaceSnapshot

/**
 * Lossless CRDT-style LWW-Element-Set workspace snapshot merger.
 *
 * Uses per-entity `updatedAtEpochMs` timestamps paired with explicit [DeletedEntity]
 * tombstones so offline creations are never dropped when revision numbers skew and
 * intentional deletions never resurrect as zombies across peers.
 */
object WorkspaceMerger {

    private data class TombstoneKey(val id: String, val kind: EntityKind)

    fun merge(base: WorkspaceSnapshot, incoming: WorkspaceSnapshot): WorkspaceSnapshot {
        val nextRevision = maxOf(base.revision, incoming.revision) + 1L
        return mergeInternal(base = base, incoming = incoming, targetRevision = nextRevision)
    }

    /**
     * Returns true if every entity and tombstone in [second] is already present in [first]
     * with an equal-or-newer timestamp (and identical content when timestamps tie).
     */
    fun isSecondSubsumedByFirst(first: WorkspaceSnapshot, second: WorkspaceSnapshot): Boolean {
        val firstTombstones = first.tombstones.associate { TombstoneKey(it.id, it.kind) to it.deletedAtEpochMs }
        val firstNb = first.notebooks.associateBy { it.id }
        val firstSec = first.sections.associateBy { it.id }
        val firstPages = first.pages.associateBy { it.id }

        for (t in second.tombstones) {
            val ft = firstTombstones[TombstoneKey(t.id, t.kind)]
            if (ft == null || ft < t.deletedAtEpochMs) {
                val surviving = when (t.kind) {
                    EntityKind.NOTEBOOK -> firstNb[t.id]?.updatedAtEpochMs
                    EntityKind.SECTION -> firstSec[t.id]?.updatedAtEpochMs
                    EntityKind.PAGE -> firstPages[t.id]?.updatedAtEpochMs
                }
                if (surviving == null || surviving <= t.deletedAtEpochMs) return false
            }
        }

        for (nb in second.notebooks) {
            val f = firstNb[nb.id]
            val tomb = firstTombstones[TombstoneKey(nb.id, EntityKind.NOTEBOOK)]
            if (tomb != null && tomb >= nb.updatedAtEpochMs) continue
            if (f == null || f.updatedAtEpochMs < nb.updatedAtEpochMs) return false
            if (f.updatedAtEpochMs == nb.updatedAtEpochMs && f != nb) return false
        }

        for (sec in second.sections) {
            val f = firstSec[sec.id]
            val tomb = firstTombstones[TombstoneKey(sec.id, EntityKind.SECTION)]
            if (tomb != null && tomb >= sec.updatedAtEpochMs) continue
            if (f == null || f.updatedAtEpochMs < sec.updatedAtEpochMs) return false
            if (f.updatedAtEpochMs == sec.updatedAtEpochMs && f != sec) return false
        }

        for (page in second.pages) {
            val f = firstPages[page.id]
            val tomb = firstTombstones[TombstoneKey(page.id, EntityKind.PAGE)]
            if (tomb != null && tomb >= page.updatedAtEpochMs) continue
            if (f == null || f.updatedAtEpochMs < page.updatedAtEpochMs) return false
            if (f.updatedAtEpochMs == page.updatedAtEpochMs && f != page) return false
        }

        return true
    }

    private fun mergeInternal(
        base: WorkspaceSnapshot,
        incoming: WorkspaceSnapshot,
        targetRevision: Long
    ): WorkspaceSnapshot {
        val preferIncomingOnTie = incoming.revision >= base.revision

        val tombstoneMap = LinkedHashMap<TombstoneKey, DeletedEntity>()
        for (t in base.tombstones + incoming.tombstones) {
            val key = TombstoneKey(t.id, t.kind)
            val existing = tombstoneMap[key]
            if (existing == null || t.deletedAtEpochMs > existing.deletedAtEpochMs) {
                tombstoneMap[key] = t
            }
        }

        // 1. Merge Notebooks
        val rawNotebooks = mergeEntitiesById(
            baseList = base.notebooks,
            incomingList = incoming.notebooks,
            idSelector = { it.id },
            timestampSelector = { it.updatedAtEpochMs },
            preferIncomingOnTie = preferIncomingOnTie
        )
        val survivingNotebooks = rawNotebooks.filter { nb ->
            val key = TombstoneKey(nb.id, EntityKind.NOTEBOOK)
            val tomb = tombstoneMap[key]
            if (tomb != null) {
                if (nb.updatedAtEpochMs > tomb.deletedAtEpochMs) {
                    tombstoneMap.remove(key)
                    true
                } else {
                    false
                }
            } else {
                true
            }
        }
        val validNotebookIds = survivingNotebooks.map { it.id }.toSet()

        // 2. Merge Sections (cascading tombstones if parent notebook was deleted)
        val rawSections = mergeEntitiesById(
            baseList = base.sections,
            incomingList = incoming.sections,
            idSelector = { it.id },
            timestampSelector = { it.updatedAtEpochMs },
            preferIncomingOnTie = preferIncomingOnTie
        )
        val survivingSections = rawSections.filter { sec ->
            val key = TombstoneKey(sec.id, EntityKind.SECTION)
            val parentTomb = tombstoneMap[TombstoneKey(sec.notebookId, EntityKind.NOTEBOOK)]
            if (sec.notebookId !in validNotebookIds) {
                if (parentTomb != null) {
                    val cascadeTs = maxOf(parentTomb.deletedAtEpochMs, sec.updatedAtEpochMs)
                    val existing = tombstoneMap[key]
                    if (existing == null || cascadeTs > existing.deletedAtEpochMs) {
                        tombstoneMap[key] = DeletedEntity(sec.id, EntityKind.SECTION, cascadeTs)
                    }
                }
                false
            } else {
                val tomb = tombstoneMap[key]
                if (tomb != null) {
                    if (sec.updatedAtEpochMs > tomb.deletedAtEpochMs) {
                        tombstoneMap.remove(key)
                        true
                    } else {
                        false
                    }
                } else {
                    true
                }
            }
        }
        val validSectionIds = survivingSections.map { it.id }.toSet()

        // 3. Merge Pages (cascading tombstones if parent section was deleted, normalizing orphaned subpages)
        val rawPages = mergeEntitiesById(
            baseList = base.pages,
            incomingList = incoming.pages,
            idSelector = { it.id },
            timestampSelector = { it.updatedAtEpochMs },
            preferIncomingOnTie = preferIncomingOnTie
        )
        val preFilteredPages = rawPages.filter { page ->
            val key = TombstoneKey(page.id, EntityKind.PAGE)
            val parentSecTomb = tombstoneMap[TombstoneKey(page.sectionId, EntityKind.SECTION)]
            if (page.sectionId !in validSectionIds) {
                if (parentSecTomb != null) {
                    val cascadeTs = maxOf(parentSecTomb.deletedAtEpochMs, page.updatedAtEpochMs)
                    val existing = tombstoneMap[key]
                    if (existing == null || cascadeTs > existing.deletedAtEpochMs) {
                        tombstoneMap[key] = DeletedEntity(page.id, EntityKind.PAGE, cascadeTs)
                    }
                }
                false
            } else {
                val tomb = tombstoneMap[key]
                if (tomb != null) {
                    if (page.updatedAtEpochMs > tomb.deletedAtEpochMs) {
                        tombstoneMap.remove(key)
                        true
                    } else {
                        false
                    }
                } else {
                    true
                }
            }
        }
        val pageMap = preFilteredPages.associateBy { it.id }
        val repairedParentByPageId = LinkedHashMap<String, String?>(preFilteredPages.size)
        for (page in preFilteredPages) {
            val initialParent = page.parentPageId
            if (initialParent == null || initialParent == page.id || initialParent !in pageMap) {
                repairedParentByPageId[page.id] = null
            } else {
                val visited = HashSet<String>()
                visited.add(page.id)
                var cursor: String? = initialParent
                var cyclic = false
                while (cursor != null) {
                    if (!visited.add(cursor)) {
                        cyclic = true
                        break
                    }
                    val nextParent = if (cursor in repairedParentByPageId) {
                        repairedParentByPageId[cursor]
                    } else {
                        pageMap[cursor]?.parentPageId
                    }
                    cursor = if (nextParent != null && nextParent in pageMap) nextParent else null
                }
                repairedParentByPageId[page.id] = if (cyclic) null else initialParent
            }
        }
        val survivingPages = preFilteredPages.map { page ->
            val sanitizedParent = repairedParentByPageId[page.id]
            if (page.parentPageId != sanitizedParent) {
                page.copy(parentPageId = sanitizedParent)
            } else {
                page
            }
        }

        return WorkspaceSnapshot(
            notebooks = survivingNotebooks,
            sections = survivingSections,
            pages = survivingPages,
            revision = targetRevision,
            tombstones = tombstoneMap.values.toList()
        )
    }

    private fun <T> mergeEntitiesById(
        baseList: List<T>,
        incomingList: List<T>,
        idSelector: (T) -> String,
        timestampSelector: (T) -> Long,
        preferIncomingOnTie: Boolean
    ): List<T> {
        val map = LinkedHashMap<String, T>()
        for (item in baseList) {
            map[idSelector(item)] = item
        }
        for (inc in incomingList) {
            val id = idSelector(inc)
            val existing = map[id]
            if (existing == null) {
                map[id] = inc
            } else {
                val incTs = timestampSelector(inc)
                val baseTs = timestampSelector(existing)
                if (incTs > baseTs || (incTs == baseTs && preferIncomingOnTie)) {
                    map[id] = inc
                }
            }
        }
        return map.values.toList()
    }
}
