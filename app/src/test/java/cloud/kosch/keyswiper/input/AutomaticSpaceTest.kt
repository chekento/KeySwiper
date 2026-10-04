package cloud.kosch.keyswiper.input

import org.junit.Assert.*
import org.junit.Test

class AutomaticSpaceTest {
    @Test fun onlyUnchangedAutomaticSeparatorCanMove() {
        val state = checkNotNull(AutomaticSpace.capture("Hallo ", "Welt"))
        assertTrue(state.matches("Hallo ", "Welt", ""))
        assertFalse(state.matches("Hallo  ", "Welt", ""))
        assertFalse(state.matches("Hallo ", "anders", ""))
        assertFalse(state.matches("Hallo ", "Welt", "Auswahl"))
        assertNull(AutomaticSpace.capture("Hallo", ""))
    }
    @Test fun punctuationGroupsRemainTogether() {
        listOf("!", "?", ":", ";", ",", ".", "!?", "…").forEach { assertTrue(AutomaticSpace.isPunctuation(it)) }
        listOf(" ", "a", "", ". ", "12.3").forEach { assertFalse(AutomaticSpace.isPunctuation(it)) }
    }
}
