package cloud.kosch.keyswiper.prediction

import cloud.kosch.keyswiper.language.LanguageLane
import cloud.kosch.keyswiper.language.UserVocabularyLookup
import org.junit.Assert.*
import org.junit.Test

class WordCorrectionEngineTest {
    private val memory = object : PredictionMemory {
        override fun boost(contextWords: List<String>, candidate: String) = 0
        override fun learnedFollowers(contextWords: List<String>, limit: Int) = emptyList<Pair<String, Int>>()
    }
    private val engine = WordCorrectionEngine(memory)

    @Test fun transpositionsAreCorrectedAndCaseIsPreserved() {
        for ((typo, word) in listOf("udn" to "und", "Dsa" to "Das", "tastatru" to "Tastatur")) {
            val best = engine.candidates(typo, emptyList(), "de").first()
            assertEquals(word, best.word)
            assertTrue("$typo should be unambiguous", best.automatic)
        }
    }

    @Test fun ambiguousShortTyposNeedContextOrAnExplicitChoice() {
        assertFalse(engine.candidates("bim", emptyList(), "de").first().automatic)
        val contextual = engine.candidates("bim", listOf("ich"), "de").first()
        assertEquals("bin", contextual.word)
        assertTrue(contextual.automatic)
    }

    @Test fun validWordsIdentifiersAndUnknownNamesAreNotSilentlyChanged() {
        listOf("hallo", "mochte", "more", "API", "keySwiper", "mail@example.com", "2026", "v19").forEach {
            assertTrue(it, engine.candidates(it, listOf("ich"), "de").none { candidate -> candidate.automatic })
        }
        assertTrue(engine.candidates("Mara", emptyList(), "de").none { it.automatic })
    }

    @Test fun validInflectionsAndDoubleLettersAreNotReducedToDictionaryStems() {
        listOf("dass", "esse", "isst", "war", "wer", "hatte", "lese").forEach {
            assertTrue(it, engine.candidates(it, listOf("ich"), "de").none { candidate -> candidate.automatic })
        }
        assertTrue(engine.candidates("schreib", emptyList(), "de").none { it.automatic })
    }

    @Test fun personalWordsAreProtectedEvenWhenTheyLookLikeTypos() {
        val vocabulary = object : UserVocabularyLookup {
            override fun contains(word: String) = word == "tastatru"
            override fun prefixMatches(prefix: String, lanes: List<LanguageLane>, limit: Int) = emptyList<Pair<String, Int>>()
            override fun frequentWords(lanes: List<LanguageLane>, limit: Int) = emptyList<Pair<String, Int>>()
        }
        assertTrue(WordCorrectionEngine(memory, vocabulary).candidates("tastatru", emptyList(), "de").isEmpty())
    }

    @Test fun GermanTransliterationCanBecomeAnUmlaut() {
        val best = engine.candidates("moechte", listOf("ich"), "de").first()
        assertEquals("möchte", best.word)
        assertTrue(best.automatic)
    }
    @Test fun validWordsStillOfferUmlautsWithoutChangingTheirMeaningAutomatically() {
        for ((raw, corrected) in listOf("schon" to "schön", "mochte" to "möchte", "konnte" to "könnte", "hatte" to "hätte", "wurde" to "würde")) {
            val candidates = engine.candidates(raw, listOf("ich"), "de", beforeToken = "ich ")
            assertTrue("$raw → $corrected: $candidates", candidates.any { it.word == corrected && !it.automatic })
        }
    }
    @Test fun omittedUmlautsAndDigraphsAppearAsCorrections() {
        for ((raw, corrected) in listOf("moglich" to "möglich", "fur" to "für", "uber" to "über", "groesser" to "größer", "grosser" to "größer", "bucher" to "Bücher")) {
            assertTrue(raw, engine.candidates(raw, emptyList(), "de", beforeToken = "ist ").any { it.word == corrected })
        }
    }
    @Test fun caseCorrectionsRespectSentenceNounsNamesAndFormsOfAddress() {
        assertTrue(engine.candidates("tastatur", listOf("die"), "de", beforeToken = "die ").any { it.word == "Tastatur" && it.automatic })
        assertTrue(engine.candidates("ich", emptyList(), "de", beforeToken = "Hallo. ").any { it.word == "Ich" && it.automatic })
        assertTrue(engine.candidates("Das", listOf("ist"), "de", beforeToken = "ist ").any { it.word == "das" })
        assertTrue(engine.candidates("Sie", listOf("danke"), "de", beforeToken = "danke ").none { it.word == "sie" })
        assertTrue(engine.candidates("API", emptyList(), "de", beforeToken = "").isEmpty())
    }
    @Test fun severalContextWordsHelpRankTheCorrection() {
        val candidates = engine.candidates("weren", listOf("soll", "noch", "besser"), "de", beforeToken = "soll noch besser ")
        assertEquals("werden", candidates.first().word)
    }
}
