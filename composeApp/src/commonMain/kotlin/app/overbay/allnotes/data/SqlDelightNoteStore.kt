package app.overbay.allnotes.data

import app.overbay.allnotes.db.AllNotesDatabase
import app.overbay.allnotes.model.DeletedEntity
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.EntityKind
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspacePreferences
import app.overbay.allnotes.model.WorkspaceSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local-first SQLite storage adapter implementing [NoteStorageBackend] via SQLDelight.
 *
 * Persists the OneNote hierarchy (Notebooks, Sections, Pages) and polymorphic document blocks
 * with zero network latency, ensuring local storage is the single source of truth.
 */
class SqlDelightNoteStore(
    private val database: AllNotesDatabase
) : NoteStorageBackend {

    private val queries = database.allNotesQueries

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * Loads the complete workspace snapshot from the local SQLite database.
     * Reconstructs the hierarchy (Notebooks -> Sections -> Pages) and deserializes polymorphic blocks.
     * Returns null if the database is uninitialized (fresh install), prompting starter data creation.
     */
    override fun loadSnapshot(): WorkspaceSnapshot? {
        val meta = queries.getWorkspaceMeta().executeAsOneOrNull()
        val dbNotebooks = queries.selectAllNotebooks().executeAsList()

        // If meta is missing and notebooks are empty, database is fresh/uninitialized
        if (meta == null && dbNotebooks.isEmpty()) {
            return null
        }

        val notebooks = dbNotebooks.map { row ->
            Notebook(
                id = row.id,
                title = row.title,
                subtitle = row.subtitle,
                accent = parseAccent(row.accent),
                sortOrder = row.sort_order.toInt(),
                updatedAtEpochMs = row.updated_at_epoch_ms
            )
        }

        val sections = queries.selectAllSections().executeAsList().map { row ->
            NoteSection(
                id = row.id,
                notebookId = row.notebook_id,
                title = row.title,
                accent = parseAccent(row.accent),
                sortOrder = row.sort_order.toInt(),
                updatedAtEpochMs = row.updated_at_epoch_ms
            )
        }

        val pages = queries.selectAllPages().executeAsList().map { row ->
            val blocks = parseBlocks(row.blocks_json)
            NotePage(
                id = row.id,
                sectionId = row.section_id,
                parentPageId = row.parent_page_id,
                title = row.title,
                blocks = blocks,
                isPinned = row.is_pinned != 0L,
                updatedAtEpochMs = row.updated_at_epoch_ms
            )
        }

        val tombstones = queries.selectAllDeletedEntities().executeAsList().mapNotNull { row ->
            val kind = runCatching { EntityKind.valueOf(row.kind) }.getOrNull() ?: return@mapNotNull null
            DeletedEntity(
                id = row.id,
                kind = kind,
                deletedAtEpochMs = row.deleted_at_epoch_ms
            )
        }

        return WorkspaceSnapshot(
            notebooks = notebooks,
            sections = sections,
            pages = pages,
            revision = meta?.revision ?: 1L,
            tombstones = tombstones
        )
    }

    /**
     * Atomically persists the complete workspace snapshot within a single database transaction.
     * Safely updates all notebooks, sections, pages (with polymorphic blocks as JSON), tombstones, and revision.
     */
    override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        database.transaction {
            // Delete child entities first to respect foreign key constraints
            queries.deleteAllPages()
            queries.deleteAllSections()
            queries.deleteAllNotebooks()
            queries.deleteAllDeletedEntities()

            // Insert notebooks
            for (notebook in snapshot.notebooks) {
                queries.insertNotebook(
                    id = notebook.id,
                    title = notebook.title,
                    subtitle = notebook.subtitle,
                    accent = notebook.accent.name,
                    sortOrder = notebook.sortOrder.toLong(),
                    updatedAtEpochMs = notebook.updatedAtEpochMs
                )
            }

            // Insert sections
            for (section in snapshot.sections) {
                queries.insertSection(
                    id = section.id,
                    notebookId = section.notebookId,
                    title = section.title,
                    accent = section.accent.name,
                    sortOrder = section.sortOrder.toLong(),
                    updatedAtEpochMs = section.updatedAtEpochMs
                )
            }

            // Insert pages in two passes so out-of-order subpages never trip FK checks
            for (page in snapshot.pages) {
                val blocksJson = json.encodeToString(page.blocks)
                queries.insertPage(
                    id = page.id,
                    sectionId = page.sectionId,
                    parentPageId = null,
                    title = page.title,
                    blocksJson = blocksJson,
                    isPinned = if (page.isPinned) 1L else 0L,
                    updatedAtEpochMs = page.updatedAtEpochMs
                )
            }
            for (page in snapshot.pages) {
                if (page.parentPageId != null) {
                    queries.updatePageParent(
                        parentPageId = page.parentPageId,
                        updatedAtEpochMs = page.updatedAtEpochMs,
                        id = page.id
                    )
                }
            }

            // Insert deletion tombstones
            for (tombstone in snapshot.tombstones) {
                queries.upsertDeletedEntity(
                    id = tombstone.id,
                    kind = tombstone.kind.name,
                    deletedAtEpochMs = tombstone.deletedAtEpochMs
                )
            }

            // Update workspace revision metadata
            queries.updateMetaRevision(
                revision = snapshot.revision,
                updatedAtEpochMs = snapshot.pages.maxOfOrNull { it.updatedAtEpochMs } ?: 0L
            )
        }
    }

    override fun savePage(page: NotePage, revision: Long) {
        database.transaction {
            savePage(page)
            queries.deleteTombstone(id = page.id, kind = EntityKind.PAGE.name)
            queries.updateMetaRevision(
                revision = revision,
                updatedAtEpochMs = page.updatedAtEpochMs
            )
        }
    }

    override fun deletePage(pageId: String, revision: Long, deletedAtEpochMs: Long) {
        database.transaction {
            queries.deletePage(pageId)
            if (deletedAtEpochMs > 0L) {
                queries.upsertDeletedEntity(
                    id = pageId,
                    kind = EntityKind.PAGE.name,
                    deletedAtEpochMs = deletedAtEpochMs
                )
            }
            queries.updateMetaRevision(
                revision = revision,
                updatedAtEpochMs = deletedAtEpochMs
            )
        }
    }

    override fun loadPreferences(): WorkspacePreferences {
        val raw = queries.getWorkspacePreferences().executeAsOneOrNull() ?: return WorkspacePreferences()
        return runCatching { json.decodeFromString<WorkspacePreferences>(raw) }
            .getOrDefault(WorkspacePreferences())
    }

    override fun savePreferences(preferences: WorkspacePreferences) {
        queries.upsertWorkspacePreferences(prefsJson = json.encodeToString(preferences))
    }

    /**
     * Directly inserts or updates a single page without rewriting the whole workspace.
     */
    fun savePage(page: NotePage) {
        val blocksJson = json.encodeToString(page.blocks)
        queries.insertPage(
            id = page.id,
            sectionId = page.sectionId,
            parentPageId = page.parentPageId,
            title = page.title,
            blocksJson = blocksJson,
            isPinned = if (page.isPinned) 1L else 0L,
            updatedAtEpochMs = page.updatedAtEpochMs
        )
    }

    /**
     * Retrieves a single page by ID, deserializing its document blocks.
     */
    fun getPage(pageId: String): NotePage? {
        val row = queries.selectPageById(pageId).executeAsOneOrNull() ?: return null
        return NotePage(
            id = row.id,
            sectionId = row.section_id,
            parentPageId = row.parent_page_id,
            title = row.title,
            blocks = parseBlocks(row.blocks_json),
            isPinned = row.is_pinned != 0L,
            updatedAtEpochMs = row.updated_at_epoch_ms
        )
    }

    /**
     * Deletes a single page by ID.
     */
    fun deletePage(pageId: String) {
        queries.deletePage(pageId)
    }

    /**
     * Retrieves pages matching a text search query across title and block contents.
     */
    fun searchPages(query: String): List<NotePage> {
        return queries.searchPages(query).executeAsList().map { row ->
            NotePage(
                id = row.id,
                sectionId = row.section_id,
                parentPageId = row.parent_page_id,
                title = row.title,
                blocks = parseBlocks(row.blocks_json),
                isPinned = row.is_pinned != 0L,
                updatedAtEpochMs = row.updated_at_epoch_ms
            )
        }
    }

    private fun parseAccent(accentStr: String): SectionAccentKey {
        return runCatching { SectionAccentKey.valueOf(accentStr) }.getOrDefault(SectionAccentKey.INDIGO)
    }

    private fun parseBlocks(blocksJson: String): List<DocumentBlock> {
        return try {
            json.decodeFromString<List<DocumentBlock>>(blocksJson)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
