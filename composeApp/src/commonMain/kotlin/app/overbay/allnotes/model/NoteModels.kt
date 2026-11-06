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
        val isHighlighted: Boolean = false
    ) : DocumentBlock

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
    val revision: Long = 1L
)
