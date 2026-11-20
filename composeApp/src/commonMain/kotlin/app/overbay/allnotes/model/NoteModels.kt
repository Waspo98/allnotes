package app.overbay.allnotes.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SectionAccentKey(val label: String) {
    INDIGO("Indigo"),
    EMERALD("Emerald"),
    AMBER("Amber"),
    ROSE("Rose"),
    TEAL("Teal"),
    VIOLET("Violet")
}

@Serializable
enum class CalloutTone(val label: String) {
    INFO("Note"),
    TIP("Tip"),
    IMPORTANT("Key Point")
}

@Serializable
enum class EntityKind {
    NOTEBOOK,
    SECTION,
    PAGE
}

@Serializable
data class DeletedEntity(
    val id: String,
    val kind: EntityKind,
    val deletedAtEpochMs: Long
)

@Serializable
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

@Serializable
data class WorkspacePreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val syncEndpointUrl: String = "https://notes.overbay.app/api/v1/sync",
    val useRemoteHttpSync: Boolean = true,
    val syncAuthToken: String = "",
    val autoSyncOnEdit: Boolean = true,
    val lastSyncedEpochMs: Long = 0L,
    val syncDefaultsMigrated: Boolean = false
)

@Serializable
data class Notebook(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val accent: SectionAccentKey = SectionAccentKey.INDIGO,
    val sortOrder: Int = 0,
    val updatedAtEpochMs: Long = 0L
)

@Serializable
data class NoteSection(
    val id: String,
    val notebookId: String,
    val title: String,
    val accent: SectionAccentKey = SectionAccentKey.INDIGO,
    val sortOrder: Int = 0,
    val updatedAtEpochMs: Long = 0L
)

@Serializable
data class ChecklistItem(
    val id: String,
    val text: String,
    val isChecked: Boolean = false
)

@Serializable
enum class RichSpanKind {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    HIGHLIGHT,
    INLINE_CODE,
    COLOR_ACCENT,
    COLOR_TERTIARY,
    COLOR_ERROR,
    LINK
}

@Serializable
data class RichTextSpan(
    val start: Int,
    val end: Int,
    val kind: RichSpanKind,
    val url: String? = null
)

@Serializable
sealed interface DocumentBlock {
    val id: String

    @Serializable
    @SerialName("heading")
    data class Heading(
        override val id: String,
        val text: String,
        val level: Int = 1
    ) : DocumentBlock

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(
        override val id: String,
        val text: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderlined: Boolean = false,
        val isStrikethrough: Boolean = false,
        val isHighlighted: Boolean = false,
        val spans: List<RichTextSpan> = emptyList()
    ) : DocumentBlock {
        fun effectiveSpans(): List<RichTextSpan> {
            val len = text.length
            if (len == 0) return emptyList()
            val clamped = spans.mapNotNull { span ->
                val s = span.start.coerceIn(0, len)
                val e = span.end.coerceIn(0, len)
                if (s < e) span.copy(start = s, end = e) else null
            }
            val wholeBlock = buildList {
                if (isBold && clamped.none { it.kind == RichSpanKind.BOLD && it.start == 0 && it.end == len }) {
                    add(RichTextSpan(0, len, RichSpanKind.BOLD))
                }
                if (isItalic && clamped.none { it.kind == RichSpanKind.ITALIC && it.start == 0 && it.end == len }) {
                    add(RichTextSpan(0, len, RichSpanKind.ITALIC))
                }
                if (isUnderlined && clamped.none { it.kind == RichSpanKind.UNDERLINE && it.start == 0 && it.end == len }) {
                    add(RichTextSpan(0, len, RichSpanKind.UNDERLINE))
                }
                if (isStrikethrough && clamped.none { it.kind == RichSpanKind.STRIKETHROUGH && it.start == 0 && it.end == len }) {
                    add(RichTextSpan(0, len, RichSpanKind.STRIKETHROUGH))
                }
                if (isHighlighted && clamped.none { it.kind == RichSpanKind.HIGHLIGHT && it.start == 0 && it.end == len }) {
                    add(RichTextSpan(0, len, RichSpanKind.HIGHLIGHT))
                }
            }
            return clamped + wholeBlock
        }
    }

    @Serializable
    @SerialName("checklist")
    data class Checklist(
        override val id: String,
        val title: String = "",
        val items: List<ChecklistItem> = emptyList()
    ) : DocumentBlock

    @Serializable
    @SerialName("bullet_list")
    data class BulletList(
        override val id: String,
        val isNumbered: Boolean = false,
        val items: List<String> = emptyList()
    ) : DocumentBlock

    @Serializable
    @SerialName("table")
    data class Table(
        override val id: String,
        val headers: List<String>,
        val rows: List<List<String>>
    ) : DocumentBlock

    @Serializable
    @SerialName("callout")
    data class Callout(
        override val id: String,
        val title: String,
        val text: String,
        val tone: CalloutTone = CalloutTone.INFO
    ) : DocumentBlock

    @Serializable
    @SerialName("code")
    data class CodeSnippet(
        override val id: String,
        val language: String = "kotlin",
        val code: String
    ) : DocumentBlock

    @Serializable
    @SerialName("divider")
    data class Divider(
        override val id: String
    ) : DocumentBlock

    @Serializable
    @SerialName("attachment")
    data class Attachment(
        override val id: String,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long = 0L,
        val caption: String = "",
        val dataBase64: String = ""
    ) : DocumentBlock
}

@Serializable
data class NotePage(
    val id: String,
    val sectionId: String,
    val parentPageId: String? = null,
    val title: String,
    val blocks: List<DocumentBlock> = emptyList(),
    val isPinned: Boolean = false,
    val updatedAtEpochMs: Long = 0L
)

@Serializable
data class WorkspaceSnapshot(
    val notebooks: List<Notebook> = emptyList(),
    val sections: List<NoteSection> = emptyList(),
    val pages: List<NotePage> = emptyList(),
    val revision: Long = 1L,
    val tombstones: List<DeletedEntity> = emptyList()
)
