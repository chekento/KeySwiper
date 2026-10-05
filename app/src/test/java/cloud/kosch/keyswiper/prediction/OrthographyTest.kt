package cloud.kosch.keyswiper.prediction

import org.junit.Assert.*
import org.junit.Test

class OrthographyTest {
    @Test fun germanNounsAndAmbiguousWordsUseContext() {
        assertEquals("Tastatur", Orthography.display("tastatur", "de", "die "))
        assertEquals("Morgen", Orthography.display("morgen", "de", "Guten "))
        assertEquals("morgen", Orthography.display("morgen", "de", "bis "))
        assertEquals("frage", Orthography.display("frage", "de", "ich "))
        assertEquals("Frage", Orthography.display("frage", "de", "eine "))
        assertEquals("fragen", Orthography.display("fragen", "de", "wir "))
    }
    @Test fun sentenceBoundariesRespectCommonAbbreviations() {
        assertTrue(Orthography.sentenceStart("Hallo! "))
        assertTrue(Orthography.sentenceStart("Zeile\n"))
        assertFalse(Orthography.sentenceStart("z. B. "))
        assertFalse(Orthography.sentenceStart("Dr. "))
        assertFalse(Orthography.sentenceStart("ich "))
    }
    @Test fun phrasesPreserveFormalPronounsAndCapitalNouns() {
        assertEquals("Ihre Rückmeldung", Orthography.phrase("Ihre Rückmeldung", "de", "Danke für ", true))
        assertEquals("Sie mir die Unterlagen", Orthography.phrase("Sie mir die Unterlagen", "de", "Bitte senden ", true))
        assertEquals("I", Orthography.display("i", "en", "and "))
        assertEquals("KeySwiper", Orthography.display("KeySwiper", "de", "mit ", personal = true))
    }
}
