package cloud.kosch.keyswiper.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSizingTest {
    @Test
    fun normalKeyboardNeverExceedsOneThirdOfDisplay() {
        val budget = KeyboardSizing.calculate(
            2340,3f,false,120,12
        )
        assertTrue(budget.totalHeightPx <= 2340 / 3)
        assertTrue(budget.surfaceHeightPx > 0)
        assertTrue(budget.accentRowHeightPx > 0)
        // Three letter rows should receive at least half the usable content space.
        val usable = budget.totalHeightPx - budget.bottomInsetPx - budget.contentVerticalPaddingPx
        assertTrue(budget.surfaceHeightPx >= usable / 2)
    }

    @Test
    fun symbolModeReusesAccentRowSpaceForKeys() {
        val normal = KeyboardSizing.calculate(
            2340,3f,false,90,12
        )
        val symbols = KeyboardSizing.calculate(
            2340,3f,true,90,12
        )
        assertEquals(0, symbols.accentRowHeightPx)
        assertTrue(symbols.surfaceHeightPx > normal.surfaceHeightPx)
        assertTrue(symbols.totalHeightPx <= 2340 / 3)
    }
}
