package cloud.kosch.keyswiper.input

data class EditTimelineEntry(
    val id: Long,
    val deletedText: String,
    val insertedText: String,
    val source: String,
    val timestampMs: Long
) {
    val summary: String
        get() =
            when {
                deletedText.isBlank() ->
                    "+ ${insertedText.trim()}"
                insertedText.isBlank() ->
                    "− ${deletedText.trim()}"
                else ->
                    "${deletedText.trim()} → ${insertedText.trim()}"
            }
}

data class EditTimelinePlan(
    val entry: EditTimelineEntry,
    val deleteUtf16Count: Int,
    val insertText: String
)

class EditTimeline(
    private val maxEntries: Int = 24
) {
    private val undoStack =
        mutableListOf<EditTimelineEntry>()

    private val redoStack =
        mutableListOf<EditTimelineEntry>()

    private var nextId = 1L

    val canUndo: Boolean
        get() =
            undoStack.isNotEmpty()

    val canRedo: Boolean
        get() =
            redoStack.isNotEmpty()

    fun record(
        deletedText: String,
        insertedText: String,
        source: String,
        timestampMs: Long =
            System.currentTimeMillis()
    ) {
        if (
            deletedText ==
            insertedText
        ) {
            return
        }

        undoStack.add(
            EditTimelineEntry(
                id = nextId++,
                deletedText =
                    deletedText,
                insertedText =
                    insertedText,
                source = source,
                timestampMs =
                    timestampMs
            )
        )

        while (
            undoStack.size >
            maxEntries
        ) {
            undoStack.removeAt(0)
        }

        redoStack.clear()
    }

    fun planUndo(
        beforeCursor: String
    ): EditTimelinePlan? {
        val entry =
            undoStack.lastOrNull()
                ?: return null

        if (
            !beforeCursor.endsWith(
                entry.insertedText
            )
        ) {
            return null
        }

        return EditTimelinePlan(
            entry = entry,
            deleteUtf16Count =
                entry.insertedText.length,
            insertText =
                entry.deletedText
        )
    }

    fun completeUndo(
        entryId: Long
    ): Boolean {
        val entry =
            undoStack.lastOrNull()
                ?: return false

        if (
            entry.id !=
            entryId
        ) {
            return false
        }

        undoStack.removeAt(
            undoStack.lastIndex
        )
        redoStack.add(entry)
        return true
    }

    fun planRedo(
        beforeCursor: String
    ): EditTimelinePlan? {
        val entry =
            redoStack.lastOrNull()
                ?: return null

        if (
            !beforeCursor.endsWith(
                entry.deletedText
            )
        ) {
            return null
        }

        return EditTimelinePlan(
            entry = entry,
            deleteUtf16Count =
                entry.deletedText.length,
            insertText =
                entry.insertedText
        )
    }

    fun completeRedo(
        entryId: Long
    ): Boolean {
        val entry =
            redoStack.lastOrNull()
                ?: return false

        if (
            entry.id !=
            entryId
        ) {
            return false
        }

        redoStack.removeAt(
            redoStack.lastIndex
        )
        undoStack.add(entry)
        return true
    }

    fun recent(
        limit: Int = 12
    ): List<EditTimelineEntry> =
        undoStack
            .asReversed()
            .take(
                limit.coerceAtLeast(1)
            )

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
