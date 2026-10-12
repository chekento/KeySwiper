package cloud.kosch.keyswiper.input

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorMovementTest {
    @Test fun movesBothDirectionsWithoutChangingTheText() {
        val text = "Hallo Welt"
        assertEquals(3, CursorMovement.offset(text, 5, 5, -2))
        assertEquals(7, CursorMovement.offset(text, 5, 5, 2))
        assertEquals(0, CursorMovement.offset(text, 0, 0, -20))
        assertEquals(text.length, CursorMovement.offset(text, text.length, text.length, 20))
    }

    @Test fun selectionCollapsesTowardTheDragBeforeFurtherSteps() {
        assertEquals(2, CursorMovement.offset("abcdef", 2, 5, -1))
        assertEquals(5, CursorMovement.offset("abcdef", 5, 2, 1))
        assertEquals(1, CursorMovement.offset("abcdef", 2, 5, -2))
        assertEquals(6, CursorMovement.offset("abcdef", 2, 5, 2))
    }

    @Test fun neverStopsInsideEmojiOrCombiningCharacters() {
        for (character in listOf("👨‍👩‍👧‍👦", "👍🏽", "🇩🇪", "e\u0301", "❤️")) {
            val text = "A${character}B"
            assertEquals(1 + character.length, CursorMovement.offset(text, 1, 1, 1))
            assertEquals(1, CursorMovement.offset(text, 1 + character.length, 1 + character.length, -1))
        }
        assertEquals(4, CursorMovement.offset("🇩🇪🇫🇷", 0, 0, 1))
        assertEquals(4, CursorMovement.offset("🇩🇪🇫🇷", 8, 8, -1))
    }
}
