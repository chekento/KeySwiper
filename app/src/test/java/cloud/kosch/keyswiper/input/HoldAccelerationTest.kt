package cloud.kosch.keyswiper.input

import org.junit.Assert.*
import org.junit.Test

class HoldAccelerationTest {
    @Test fun defaultHoldRemovesSingleWordsEvenAfterALongHold() {
        assertEquals(DeleteUnit.CHARACTER, HoldAcceleration.step(420).unit)
        for (elapsed in listOf(1500L, 3500L, 6000L, 9500L, 30000L)) {
            assertEquals(DeleteUnit.WORD, HoldAcceleration.step(elapsed).unit)
            assertEquals("Erster Satz. Zweiter ", rest("Erster Satz. Zweiter Satz. ", HoldAcceleration.step(elapsed).unit))
        }
    }
    @Test fun largerDeletionUnitsRequireAnExplicitOptIn() {
        assertEquals(DeleteUnit.WORDS, HoldAcceleration.step(3500, advanced = true).unit)
        assertEquals(DeleteUnit.SENTENCE, HoldAcceleration.step(6000, advanced = true).unit)
        assertEquals(DeleteUnit.PARAGRAPH, HoldAcceleration.step(9500, advanced = true).unit)
    }
    private fun rest(text: String, unit: DeleteUnit) = text.dropLast(HoldAcceleration.deleteLength(text, unit))
    @Test fun characterDoesNotSplitEmoji() { assertEquals("Hallo ", rest("Hallo 👨‍👩‍👧‍👦", DeleteUnit.CHARACTER)) }
    @Test fun wordIncludesItsTrailingSeparatorAndPunctuation() { assertEquals("Guten ", rest("Guten Morgen! ", DeleteUnit.WORD)) }
    @Test fun severalWordsKeepTheEarlierContext() { assertEquals("Wir ", rest("Wir schreiben jetzt weiter ", DeleteUnit.WORDS)) }
    @Test fun sentenceStopsAtEarlierSentenceBoundary() { assertEquals("Hallo!", rest("Hallo! Wie geht es dir? ", DeleteUnit.SENTENCE)) }
    @Test fun paragraphsStopAtLineBoundaryAndEventuallyEmptyText() {
        var text = "Erster Absatz.\n\nZweiter Absatz. Noch ein Satz. "
        text = rest(text, DeleteUnit.PARAGRAPH)
        assertEquals("Erster Absatz.\n\n", text)
        assertEquals("", rest(text, DeleteUnit.PARAGRAPH))
        assertEquals(0, HoldAcceleration.deleteLength("", DeleteUnit.WORD))
    }
}
