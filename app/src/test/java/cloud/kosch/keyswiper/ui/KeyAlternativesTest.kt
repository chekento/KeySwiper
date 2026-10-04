package cloud.kosch.keyswiper.ui

import org.junit.Assert.*
import org.junit.Test

class KeyAlternativesTest {
    @Test fun vowelsExposeUmlautsAndShiftPreservesThem() {
        for ((base, umlaut) in listOf('a' to "ä", 'o' to "ö", 'u' to "ü")) {
            assertEquals(umlaut, KeyAlternatives.forKey(base, "de-qwertz", false, false).first())
            assertEquals(umlaut.uppercase(), KeyAlternatives.forKey(base, "de-qwertz", true, false).first())
        }
        assertEquals("ẞ", KeyAlternatives.forKey('s', "de-qwertz", true, false).first())
    }

    @Test fun everyLetterAndBothSymbolPagesHaveReachableAlternatives() {
        KeyboardLayoutProfiles.all.forEach { profile ->
            profile.letterRows.joinToString("").forEach { key ->
                assertTrue("${profile.id}: $key", KeyAlternatives.forKey(key, profile.id, false, false).isNotEmpty())
            }
        }
        (KeyboardLayoutProfiles.symbolRows + KeyboardLayoutProfiles.extraSymbolRows).joinToString("").forEach { key ->
            val values = KeyAlternatives.forKey(key, "de-qwertz", false, true)
            assertTrue("Symbol $key", values.isNotEmpty())
            assertFalse(values.contains(key.toString()))
        }
    }

    @Test fun emojiVariantsIncludeSkinTonesAndStayInTheirFamily() {
        assertTrue(EmojiCatalog.alternatives("👍").contains("👍🏽"))
        assertTrue(EmojiCatalog.alternatives("❤️‍🔥").contains("❤️‍🩹"))
        EmojiCatalog.categories.flatMap { it.keys }.forEach { base ->
            assertTrue(EmojiCatalog.alternatives(base).isNotEmpty())
            assertFalse(EmojiCatalog.alternatives(base).contains(base))
        }
    }
}
