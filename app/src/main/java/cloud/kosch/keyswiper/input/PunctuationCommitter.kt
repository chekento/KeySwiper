package cloud.kosch.keyswiper.input

import android.view.inputmethod.InputConnection

/** Only moves a separator that this keyboard inserted and still owns. */
object PunctuationCommitter {
    fun commit(connection: InputConnection, value: String, space: AutomaticSpace?): AutomaticSpace? {
        val before = connection.getTextBeforeCursor(256, 0)?.toString()
        val after = connection.getTextAfterCursor(64, 0)?.toString()
        val selected = connection.getSelectedText(0)?.toString().orEmpty()
        val move = before != null && after != null && AutomaticSpace.isPunctuation(value) &&
            space?.matches(before, after, selected) == true
        if (!move) { connection.commitText(value, 1); return null }
        var committed = false
        connection.beginBatchEdit()
        try {
            if (connection.deleteSurroundingText(1, 0)) {
                committed = connection.commitText(value + " ", 1)
                if (!committed) connection.commitText(" ", 1)
            } else connection.commitText(value, 1)
        } finally { connection.endBatchEdit() }
        return if (committed) AutomaticSpace.capture(
            connection.getTextBeforeCursor(256, 0)?.toString().orEmpty(),
            connection.getTextAfterCursor(64, 0)?.toString().orEmpty()) else null
    }
}
