package cloud.kosch.keyswiper.prediction

import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class SurroundingContextReader {

    fun read(
        connection: InputConnection?,
        editorInfo: EditorInfo?,
        sensitive: Boolean
    ): SurroundingContextSnapshot {
        if (connection == null || sensitive) {
            return SurroundingContextSnapshot()
        }

        val raw = if (Build.VERSION.SDK_INT >= 31) {
            readModern(connection, editorInfo) ?: readLegacy(connection)
        } else {
            readLegacy(connection)
        }

        return LocalContextAnalyzer.analyze(
            beforeCursor = raw.before,
            selectedText = raw.selected,
            afterCursor = raw.after
        )
    }

    private data class RawContext(
        val before: String,
        val selected: String,
        val after: String
    )

    private fun readModern(
        connection: InputConnection,
        editorInfo: EditorInfo?
    ): RawContext? {
        val surrounding = try {
            connection.getSurroundingText(1600, 600, 0)
                ?: editorInfo?.getInitialSurroundingText(1600, 600, 0)
        } catch (_: Throwable) {
            null
        } ?: return null

        val text = surrounding.text?.toString().orEmpty()
        val start = surrounding.selectionStart.coerceIn(0, text.length)
        val end = surrounding.selectionEnd.coerceIn(start, text.length)

        return RawContext(
            before = text.substring(0, start),
            selected = text.substring(start, end),
            after = text.substring(end)
        )
    }

    private fun readLegacy(connection: InputConnection): RawContext {
        val before = try {
            connection.getTextBeforeCursor(1600, 0)?.toString().orEmpty()
        } catch (_: Throwable) {
            ""
        }

        val selected = try {
            connection.getSelectedText(0)?.toString().orEmpty()
        } catch (_: Throwable) {
            ""
        }

        val after = try {
            connection.getTextAfterCursor(600, 0)?.toString().orEmpty()
        } catch (_: Throwable) {
            ""
        }

        return RawContext(
            before = before,
            selected = selected,
            after = after
        )
    }
}
