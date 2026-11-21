package app.overbay.allnotes.data

import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.RichSpanKind
import app.overbay.allnotes.model.RichTextSpan
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspaceSnapshot

object SampleWorkspaceFactory {
    fun createStarterSnapshot(): WorkspaceSnapshot {
        val notebooks = listOf(
            Notebook(
                id = "nb-studio",
                title = "Getting Started",
                subtitle = "Welcome guide, tips & quick reference",
                accent = SectionAccentKey.INDIGO,
                sortOrder = 0,
                updatedAtEpochMs = 1762420000000L
            ),
            Notebook(
                id = "nb-personal",
                title = "Personal Studio",
                subtitle = "Weekly focus, ideas & travel notes",
                accent = SectionAccentKey.EMERALD,
                sortOrder = 1,
                updatedAtEpochMs = 1762410000000L
            )
        )

        val sections = listOf(
            NoteSection(
                id = "sec-welcome",
                notebookId = "nb-studio",
                title = "Welcome",
                accent = SectionAccentKey.INDIGO,
                sortOrder = 0,
                updatedAtEpochMs = 1762420000000L
            ),
            NoteSection(
                id = "sec-ideas",
                notebookId = "nb-studio",
                title = "Ideas & Plans",
                accent = SectionAccentKey.TEAL,
                sortOrder = 1,
                updatedAtEpochMs = 1762415000000L
            ),
            NoteSection(
                id = "sec-focus",
                notebookId = "nb-personal",
                title = "Daily Focus",
                accent = SectionAccentKey.EMERALD,
                sortOrder = 0,
                updatedAtEpochMs = 1762410000000L
            )
        )

        val introText = "Organize your thinking into Notebooks, color-coded Sections, and calm, readable Pages—everything saved locally first and always ready offline."
        val pages = listOf(
            NotePage(
                id = "page-welcome",
                sectionId = "sec-welcome",
                title = "Welcome to AllNotes",
                isPinned = true,
                updatedAtEpochMs = 1762420000000L,
                blocks = listOf(
                    DocumentBlock.Paragraph(
                        id = "blk-1",
                        text = introText,
                        spans = listOf(
                            RichTextSpan(28, 37, RichSpanKind.BOLD),
                            RichTextSpan(51, 59, RichSpanKind.BOLD),
                            RichTextSpan(80, 85, RichSpanKind.BOLD)
                        )
                    ),
                    DocumentBlock.Callout(
                        id = "blk-2",
                        title = "Type '/' anywhere to insert blocks",
                        text = "Press Enter for line breaks or blank lines anywhere in your note. Start any line with '/', '# ', or '- '—or tap +—to insert headings, checklists, tables, callouts, images, and code.",
                        tone = CalloutTone.TIP
                    ),
                    DocumentBlock.Heading(
                        id = "blk-3",
                        text = "Try These First",
                        level = 2
                    ),
                    DocumentBlock.Checklist(
                        id = "blk-4",
                        title = "Quick Tour",
                        items = listOf(
                            ChecklistItem("chk-1", "Tap a section tab to see the page wash shift softly", isChecked = true),
                            ChecklistItem("chk-2", "Check off an item in this list", isChecked = false),
                            ChecklistItem("chk-3", "Swipe a page in the sidebar to pin or archive it", isChecked = false),
                            ChecklistItem("chk-4", "Tap New Page for instant distraction-free capture", isChecked = false)
                        )
                    ),
                    DocumentBlock.Heading(
                        id = "blk-5",
                        text = "Handy Shortcuts",
                        level = 2
                    ),
                    DocumentBlock.Table(
                        id = "blk-6",
                        headers = listOf("Action", "Shortcut"),
                        rows = listOf(
                            listOf("New Page", "Ctrl + N"),
                            listOf("Quick Jump across notebooks", "Ctrl + P"),
                            listOf("Bold / Italic / Underline", "Ctrl + B / I / U"),
                            listOf("Insert Checklist", "Ctrl + Shift + L")
                        )
                    )
                )
            ),
            NotePage(
                id = "page-formatting",
                sectionId = "sec-welcome",
                title = "Formatting & Code Notes",
                isPinned = false,
                updatedAtEpochMs = 1762418000000L,
                blocks = listOf(
                    DocumentBlock.Paragraph(
                        id = "blk-f1",
                        text = "Select any words to apply bold, italic, highlight, links, or inline code. Pages also export cleanly to GitHub-Flavored Markdown."
                    ),
                    DocumentBlock.CodeSnippet(
                        id = "blk-f2",
                        language = "kotlin",
                        code = "// Clean monospace snippets with horizontal scroll\nfun greet(name: String): String {\n    return \"Welcome back, \$name\"\n}"
                    )
                )
            ),
            NotePage(
                id = "page-trip",
                sectionId = "sec-ideas",
                title = "Weekend Escape Plan",
                isPinned = true,
                updatedAtEpochMs = 1762415000000L,
                blocks = listOf(
                    DocumentBlock.Callout(
                        id = "blk-t1",
                        title = "Departure Reminder",
                        text = "Leave before 7:30 AM Saturday to beat highway traffic and catch morning coffee in town.",
                        tone = CalloutTone.IMPORTANT
                    ),
                    DocumentBlock.BulletList(
                        id = "blk-t2",
                        isNumbered = false,
                        items = listOf(
                            "Book cabin check-in code & offline map",
                            "Pack light rain shell, camera, and charger",
                            "Reserve dinner table for 6:30 PM"
                        )
                    )
                )
            ),
            NotePage(
                id = "page-focus-1",
                sectionId = "sec-focus",
                title = "This Week's Priorities",
                isPinned = false,
                updatedAtEpochMs = 1762410000000L,
                blocks = listOf(
                    DocumentBlock.Checklist(
                        id = "blk-p1",
                        title = "Top 3 Outcomes",
                        items = listOf(
                            ChecklistItem("chk-p1", "Finish project milestone review", isChecked = true),
                            ChecklistItem("chk-p2", "Clear inbox and organize reference notes", isChecked = false),
                            ChecklistItem("chk-p3", "Plan next month's reading list", isChecked = false)
                        )
                    )
                )
            )
        )

        return WorkspaceSnapshot(
            notebooks = notebooks,
            sections = sections,
            pages = pages,
            revision = 1L
        )
    }
}
