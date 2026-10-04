package cloud.kosch.keyswiper.input

import android.view.inputmethod.InputConnection

/** Shared editor transaction for swipe words and accepted prediction chips. */
object WordCommitter {
    data class Result(val beforeCursor: String, val plan: WordCommitPlan)

    fun commit(
        connection: InputConnection,
        value: String,
        replacesCurrentToken: Boolean = false
    ): Result? {
        if (value.isBlank()) return null
        connection.beginBatchEdit()
        try {
            // A stale composing span must not replace text outside the explicit
            // completion token or swallow the next typed character.
            connection.finishComposingText()
            val before = connection.getTextBeforeCursor(1600, 0)?.toString().orEmpty()
            val after = connection.getTextAfterCursor(256, 0)?.toString().orEmpty()
            val selected = connection.getSelectedText(0)?.toString().orEmpty()
            val plan = WordCommitPlan.create(value, before, after, selected, replacesCurrentToken)
                ?: return null
            val deletes = plan.deleteBefore > 0 || plan.deleteAfter > 0
            if (deletes && !connection.deleteSurroundingText(plan.deleteBefore, plan.deleteAfter)) return null
            // newCursorPosition=1 means AFTER the complete insertion, including
            // the real trailing U+0020, ready for the next key or swipe.
            if (!connection.commitText(plan.insertedText, 1)) {
                if (deletes) connection.commitText(plan.deletedText, 1)
                return null
            }
            return Result(before, plan)
        } finally {
            connection.endBatchEdit()
        }
    }
}
