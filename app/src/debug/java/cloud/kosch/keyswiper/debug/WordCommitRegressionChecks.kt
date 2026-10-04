package cloud.kosch.keyswiper.debug

import android.content.Context
import android.text.Editable
import android.util.Log
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import cloud.kosch.keyswiper.input.WordCommitter

/** Exercises the shared IME insertion path with Android's real editable/cursor. */
object WordCommitRegressionChecks {
    fun run(context: Context) {
        val field = EditText(context)
        val connection = object : BaseInputConnection(field, true) {
            override fun getEditable(): Editable = field.editableText
        }
        fun reset(value: String, start: Int = value.length, end: Int = start) {
            field.setText(value)
            field.setSelection(start, end)
        }
        fun assertText(expected: String, cursor: Int = expected.length) {
            check(field.text.toString() == expected) { "Word spacing: expected <$expected>, got <${field.text}>" }
            check(field.selectionStart == cursor && field.selectionEnd == cursor) {
                "The cursor must follow the word's separator"
            }
        }

        reset("")
        checkNotNull(WordCommitter.commit(connection, "Hallo"))
        checkNotNull(WordCommitter.commit(connection, "Welt"))
        connection.commitText("w", 1)
        assertText("Hallo Welt w")

        reset("Guten Mor")
        connection.setComposingRegion(6, 9)
        checkNotNull(WordCommitter.commit(connection, "Morgen", true))
        connection.commitText("w", 1)
        assertText("Guten Morgen w")
        check(BaseInputConnection.getComposingSpanStart(field.text) == -1)

        reset("Hal Welt", 3)
        checkNotNull(WordCommitter.commit(connection, "Hallo", true))
        assertText("Hallo Welt", 6)
        connection.commitText("schöne ", 1)
        assertText("Hallo schöne Welt", 13)

        reset("Guten Tag morgen", 6, 9)
        checkNotNull(WordCommitter.commit(connection, "Abend", true))
        assertText("Guten Abend morgen", 12)

        reset("Hallo")
        checkNotNull(WordCommitter.commit(connection, "  Welt  "))
        assertText("Hallo Welt ")

        // Invalid connections must not turn failed deletions into appended words.
        reset("Hal")
        val refusing = object : BaseInputConnection(field, true) {
            override fun getEditable(): Editable = field.editableText
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) = false
        }
        check(WordCommitter.commit(refusing, "Hallo", true) == null)
        assertText("Hal")
        Log.i("KeySwiperInputChecks", "PASS: word spacing, completion, composition, selection and cursor position")
    }
}
