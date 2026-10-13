package cloud.kosch.keyswiper.input

/** Cursor steps use visible characters rather than UTF-16 code units. */
object CursorMovement {
    fun offset(text: String, selectionStart: Int, selectionEnd: Int, steps: Int): Int {
        if (steps == 0) return selectionEnd.coerceIn(0, text.length)
        val direction = if (steps < 0) -1 else 1
        var remaining = kotlin.math.abs(steps.coerceIn(-64, 64))
        var position = (if (direction < 0) minOf(selectionStart, selectionEnd)
            else maxOf(selectionStart, selectionEnd)).coerceIn(0, text.length)
        if (selectionStart != selectionEnd) remaining--
        repeat(remaining) {
            position += if (direction < 0) -TextBoundaryUtils.lastGraphemeUtf16Length(text.take(position))
                else TextBoundaryUtils.firstGraphemeUtf16Length(text.substring(position))
        }
        return position
    }
}
