package cloud.kosch.keyswiper.input

import java.text.BreakIterator
import java.util.Locale

object TextBoundaryUtils {
    fun firstGraphemeUtf16Length(text: String): Int {
        if (text.isEmpty()) return 0
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val standardEnd = iterator.following(0).coerceAtLeast(0)
        var end = Character.charCount(text.codePointAt(0))
        fun consumeExtensions() {
            while (end < text.length && isExtension(text.codePointAt(end))) {
                end += Character.charCount(text.codePointAt(end))
            }
        }
        consumeExtensions()
        while (end < text.length && text.codePointAt(end) == 0x200D) {
            end++
            if (end < text.length) end += Character.charCount(text.codePointAt(end))
            consumeExtensions()
        }
        if (text.codePointAt(0) in 0x1F1E6..0x1F1FF && end < text.length &&
            text.codePointAt(end) in 0x1F1E6..0x1F1FF) end += Character.charCount(text.codePointAt(end))
        return maxOf(end, standardEnd)
    }

    fun lastGraphemeUtf16Length(text: String): Int {
        if (text.isEmpty()) return 0
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val standardStart = iterator.preceding(text.length).coerceAtLeast(0)
        var start = text.length
        fun consumeBaseAndExtensions() {
            while (start > 0 && isExtension(text.codePointBefore(start))) {
                start -= Character.charCount(text.codePointBefore(start))
            }
            if (start > 0) start -= Character.charCount(text.codePointBefore(start))
        }
        consumeBaseAndExtensions()
        // Older Java/Android break iterators split skin tones and emoji ZWJ sequences.
        while (start > 0 && text.codePointBefore(start) == 0x200D) {
            start--
            consumeBaseAndExtensions()
        }
        if (text.codePointBefore(text.length) in 0x1F1E6..0x1F1FF) {
            var index = text.length
            var regionalCount = 0
            while (index > 0 && text.codePointBefore(index) in 0x1F1E6..0x1F1FF) {
                index -= Character.charCount(text.codePointBefore(index))
                regionalCount++
            }
            start = text.length - if (regionalCount % 2 == 0) 4 else 2
        }
        return text.length - minOf(start, standardStart)
    }

    private fun isExtension(codePoint: Int): Boolean =
        codePoint in 0x1F3FB..0x1F3FF || codePoint in 0xFE00..0xFE0F ||
            codePoint in 0xE0020..0xE007F || codePoint in 0xE0100..0xE01EF ||
            Character.getType(codePoint) in listOf(
                Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt()
            )
}
