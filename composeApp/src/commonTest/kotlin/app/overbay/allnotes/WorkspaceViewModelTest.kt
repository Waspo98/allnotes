package app.overbay.allnotes

import app.overbay.allnotes.data.NoteRepository
import app.overbay.allnotes.data.NoteStorageBackend
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspaceSnapshot
import app.overbay.allnotes.ui.workspace.WorkspaceViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class InMemoryNoteStorage : NoteStorageBackend {
    var saved: WorkspaceSnapshot? = null
    override fun loadSnapshot(): WorkspaceSnapshot? = saved
    override fun saveSnapshot(snapshot: WorkspaceSnapshot) {
        saved = snapshot
    }
}

class WorkspaceViewModelTest {

    @Test
    fun starterWorkspaceLoadsWithHierarchyAndBlocks() {
        val storage = InMemoryNoteStorage()
        val repo = NoteRepository(storage = storage)
        val vm = WorkspaceViewModel(repository = repo)

        val state = vm.uiState.value
        assertTrue(state.notebooks.isNotEmpty(), "Expected starter notebooks")
        assertTrue(state.sectionsForNotebook.isNotEmpty(), "Expected starter sections")
        assertNotNull(state.activePage, "Expected an active starter page")
        assertTrue(state.activePage!!.blocks.isNotEmpty(), "Expected starter blocks")
    }

    @Test
    fun creatingNotebookSectionAndPageUpdatesHierarchyAndPersists() {
        val storage = InMemoryNoteStorage()
        val repo = NoteRepository(storage = storage)
        val vm = WorkspaceViewModel(repository = repo)

        val nb = repo.createNotebook("Work Vault", "Q4 Planning", SectionAccentKey.TEAL)
        vm.selectNotebook(nb.id)
        assertEquals("Work Vault", vm.uiState.value.activeNotebook?.title)

        val sec = repo.createSection(nb.id, "Sprint Notes", SectionAccentKey.AMBER)
        vm.selectSection(sec.id)
        assertEquals("Sprint Notes", vm.uiState.value.activeSection?.title)

        val page = repo.createPage(sec.id, "Retrospective")
        vm.selectPage(page.id)
        assertEquals("Retrospective", vm.uiState.value.activePage?.title)

        vm.insertChecklistBlock()
        val updatedPage = repo.snapshot.value.pages.first { it.id == page.id }
        assertTrue(updatedPage.blocks.any { it is DocumentBlock.Checklist })
        assertNotNull(storage.saved)
    }
}
