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
        for ((typo, word) in listOf("udn" to "und", "Dsa" to "Das", "tastatru" to "tastatur")) {
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
            assertTrue(it, engine.candidates(it, listOf("ich"), "de").isEmpty())
        }
        assertTrue(engine.candidates("Mara", emptyList(), "de").none { it.automatic })
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
}
