package cloud.kosch.keyswiper.input

import java.text.BreakIterator
import java.util.Locale

object TextBoundaryUtils {

    fun lastGraphemeUtf16Length(
        text: String
    ): Int {
        if (text.isEmpty()) return 0

        val iterator =
            BreakIterator.getCharacterInstance(
                Locale.ROOT
            )
        iterator.setText(text)

        val start =
            iterator.preceding(
                text.length
            )

        if (
            start == BreakIterator.DONE ||
            start < 0
        ) {
            return Character.charCount(
                text.codePointBefore(
                    text.length
                )
            )
        }

        return (
            text.length -
                start
            ).coerceAtLeast(1)
    }
}
