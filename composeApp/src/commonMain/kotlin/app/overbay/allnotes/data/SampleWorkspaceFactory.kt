package app.overbay.allnotes.data

import app.overbay.allnotes.model.CalloutTone
import app.overbay.allnotes.model.ChecklistItem
import app.overbay.allnotes.model.DocumentBlock
import app.overbay.allnotes.model.NotePage
import app.overbay.allnotes.model.NoteSection
import app.overbay.allnotes.model.Notebook
import app.overbay.allnotes.model.SectionAccentKey
import app.overbay.allnotes.model.WorkspaceSnapshot

object SampleWorkspaceFactory {
    fun createStarterSnapshot(): WorkspaceSnapshot {
        val notebooks = listOf(
            Notebook(
                id = "nb-overbay",
                title = "Overbay Studio",
                subtitle = "Architecture, apps & home infrastructure",
                accent = SectionAccentKey.INDIGO,
                sortOrder = 0,
                updatedAtEpochMs = 1762420000000L
            ),
            Notebook(
                id = "nb-personal",
                title = "Personal & Home",
                subtitle = "Projects, travel & daily reference",
                accent = SectionAccentKey.EMERALD,
                sortOrder = 1,
                updatedAtEpochMs = 1762410000000L
            )
        )

        val sections = listOf(
            NoteSection(
                id = "sec-allnotes",
                notebookId = "nb-overbay",
                title = "AllNotes Blueprint",
                accent = SectionAccentKey.INDIGO,
                sortOrder = 0,
                updatedAtEpochMs = 1762420000000L
            ),
            NoteSection(
                id = "sec-server",
                notebookId = "nb-overbay",
                title = "HAL 9000 Runbooks",
                accent = SectionAccentKey.TEAL,
                sortOrder = 1,
                updatedAtEpochMs = 1762415000000L
            ),
            NoteSection(
                id = "sec-home",
                notebookId = "nb-personal",
                title = "Home Projects",
                accent = SectionAccentKey.EMERALD,
                sortOrder = 0,
                updatedAtEpochMs = 1762410000000L
            )
        )

        val pages = listOf(
            NotePage(
                id = "page-welcome",
                sectionId = "sec-allnotes",
                title = "AllNotes: OneNote Hierarchy + Word Document Flow",
                isPinned = true,
                updatedAtEpochMs = 1762420000000L,
                blocks = listOf(
                    DocumentBlock.Callout(
                        id = "blk-1",
                        title = "Mobile-First, Word-Grade Document Studio",
                        text = "Every page flows vertically and fits your screen cleanly on mobile without 2D horizontal panning, while scaling to a full Word-style document workspace with a hierarchy sidebar on tablets, Windows (x64 & ARM64), and Web.",
                        tone = CalloutTone.IMPORTANT
                    ),
                    DocumentBlock.Heading(
                        id = "blk-2",
                        text = "Core Platform Targets",
                        level = 2
                    ),
                    DocumentBlock.Checklist(
                        id = "blk-3",
                        title = "Multiplatform Launch Checklist",
                        items = listOf(
                            ChecklistItem("chk-1", "Android M3 Expressive Phone & Foldable App", isChecked = true),
                            ChecklistItem("chk-2", "Windows Native Executable (x86_64 & ARM64)", isChecked = true),
                            ChecklistItem("chk-3", "Self-Hosted Web PWA + Sync Server (notes.overbay.app)", isChecked = true),
                            ChecklistItem("chk-4", "Rich Block Formatting, Tables & Keyboard Shortcuts", isChecked = false)
                        )
                    ),
                    DocumentBlock.Heading(
                        id = "blk-4",
                        text = "Target Matrix Overview",
                        level = 2
                    ),
                    DocumentBlock.Table(
                        id = "blk-5",
                        headers = listOf("Platform", "Runtime", "Storage Engine"),
                        rows = listOf(
                            listOf("Android", "Jetpack Compose / ART", "Local SQLite + Sync"),
                            listOf("Windows (x64 & ARM64)", "Compose Desktop / Skiko", "Local AppData Store + Sync"),
                            listOf("Web (notes.overbay.app)", "Kotlin/Wasm PWA", "Browser Local Store + Sync")
                        )
                    ),
                    DocumentBlock.Paragraph(
                        id = "blk-6",
                        text = "Use the toolbar to insert new headings, paragraphs, interactive checklists, responsive tables, callouts, or code blocks.",
                        isItalic = true
                    )
                )
            ),
            NotePage(
                id = "page-design",
                sectionId = "sec-allnotes",
                title = "Material 3 Expressive Motion & Tokens",
                isPinned = false,
                updatedAtEpochMs = 1762418000000L,
                blocks = listOf(
                    DocumentBlock.Heading(
                        id = "blk-d1",
                        text = "Kinetic Spring Transitions",
                        level = 2
                    ),
                    DocumentBlock.Paragraph(
                        id = "blk-d2",
                        text = "AllNotes uses low-bouncy physics springs and semantic Material 3 surface containers so hierarchy navigation and document editing feel tactile and immediate."
                    ),
                    DocumentBlock.CodeSnippet(
                        id = "blk-d3",
                        language = "kotlin",
                        code = "spring(\n    dampingRatio = Spring.DampingRatioLowBouncy,\n    stiffness = Spring.StiffnessLow\n)"
                    )
                )
            ),
            NotePage(
                id = "page-hal",
                sectionId = "sec-server",
                title = "Cloudflare Tunnel & Authentik Notes",
                isPinned = true,
                updatedAtEpochMs = 1762415000000L,
                blocks = listOf(
                    DocumentBlock.Paragraph(
                        id = "blk-h1",
                        text = "Self-hosted sync runs on The Overbay HAL 9000 (192.168.86.200) behind Cloudflare Tunnel and Authentik OIDC."
                    ),
                    DocumentBlock.BulletList(
                        id = "blk-h2",
                        isNumbered = false,
                        items = listOf(
                            "External Docker network: web_proxy",
                            "Identity branding: Overbay.app",
                            "Daily backups via C:\\Scripts\\DailyBackup.ps1"
                        )
                    )
                )
            ),
            NotePage(
                id = "page-home-1",
                sectionId = "sec-home",
                title = "Weekend Project Checklist",
                isPinned = false,
                updatedAtEpochMs = 1762410000000L,
                blocks = listOf(
                    DocumentBlock.Checklist(
                        id = "blk-p1",
                        title = "Priority Tasks",
                        items = listOf(
                            ChecklistItem("chk-p1", "Organize server rack cabling", isChecked = true),
                            ChecklistItem("chk-p2", "Test AllNotes on Windows ARM64 & Android", isChecked = false)
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
