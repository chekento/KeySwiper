package cloud.kosch.keyswiper.input

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

object CursorMover {
    fun move(connection: InputConnection, steps: Int, readText: Boolean = true): Boolean {
        if (steps == 0) return false
        connection.finishComposingText()
        val extracted = if (readText) connection.getExtractedText(ExtractedTextRequest().apply {
            hintMaxChars = 8192
            hintMaxLines = 100
        }, 0) else null
        val text = extracted?.text?.toString()
        if (extracted != null && text != null && extracted.partialStartOffset < 0 && extracted.startOffset >= 0 &&
            extracted.selectionStart in 0..text.length && extracted.selectionEnd in 0..text.length) {
            val target = extracted.startOffset + CursorMovement.offset(text,
                extracted.selectionStart, extracted.selectionEnd, steps)
            if (connection.setSelection(target, target)) return true
        }
        // Editors that cannot provide extracted text, and private fields, own
        // their cursor behavior through ordinary navigation key events.
        val key = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        var handled = false
        repeat(kotlin.math.abs(steps.coerceIn(-64, 64))) {
            val now = SystemClock.uptimeMillis()
            handled = connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0)) || handled
            connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0))
        }
        return handled
    }
}
