package cloud.kosch.keyswiper.input

import org.junit.Assert.assertEquals
import org.junit.Test

class TextBoundaryUtilsTest {

    @Test
    fun deletesWholeSurrogatePair() {
        assertEquals(
            2,
            TextBoundaryUtils
                .lastGraphemeUtf16Length(
                    "Hello 😀"
                )
        )
    }

    @Test
    fun deletesCombiningSequenceTogether() {
        assertEquals(
            2,
            TextBoundaryUtils
                .lastGraphemeUtf16Length(
                    "Cafe e\u0301"
                )
        )
    }

    @Test
    fun deletesEmojiVariantsAsOneVisibleCharacter() {
        val variants = listOf("👍🏽", "❤️‍🔥", "👩🏽‍💻", "🇩🇪", "1️⃣", "👨‍👩‍👧‍👦") +
            cloud.kosch.keyswiper.ui.EmojiCatalog.categories.flatMap { it.groups.flatten() }
        variants.forEach { emoji ->
            assertEquals(emoji, emoji.length, TextBoundaryUtils.lastGraphemeUtf16Length("Text $emoji"))
        }
        assertEquals(4, TextBoundaryUtils.lastGraphemeUtf16Length("🇩🇪🇫🇷"))
    }

    @Test
    fun deletesAsciiCharacterNormally() {
        assertEquals(
            1,
            TextBoundaryUtils
                .lastGraphemeUtf16Length(
                    "Hello"
                )
        )
    }
}

