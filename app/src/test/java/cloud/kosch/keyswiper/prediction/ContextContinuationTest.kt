package cloud.kosch.keyswiper.prediction

import org.junit.Assert.*
import org.junit.Test

class ContextContinuationTest {
    private val memory = object : PredictionMemory {
        override fun boost(contextWords: List<String>, candidate: String) = 0
        override fun learnedFollowers(contextWords: List<String>, limit: Int) = emptyList<Pair<String, Int>>()
    }
    private fun predict(before: String, after: String = "", mode: PredictionInputMode = PredictionInputMode.GENERAL, depth: Int = 8) =
        LocalBeamSemanticProvider(memory).predict(PredictionContext(before, listOf("de"), "de", mode, depth,
            LocalContextAnalyzer.analyze(before, "", after)), 5)

    @Test fun fullPrefixRetainsNegationAndProducesAMeaningfulClause() {
        val result = predict("Ich kann nicht ")
        assertEquals("an dem Termin teilnehmen", result.first().commitText)
    }
    @Test fun topicAndRegisterChooseAnAppropriateMeetingContinuation() {
        val result = predict("Es geht um einen Termin. Ich möchte ", mode = PredictionInputMode.EMAIL)
        assertTrue(result.take(2).any { it.commitText.contains("Termin") && it.commitText.contains("Ihnen") })
        assertTrue(result.none { it.commitText.contains("mit dir") })
    }
    @Test fun existingTextToTheRightIsNotInsertedTwice() {
        val result = predict("Ich kann nicht ", "teilnehmen")
        assertTrue(result.any { it.commitText == "an dem Termin" })
        assertTrue(result.none { it.commitText.endsWith("teilnehmen") })
    }
    @Test fun sentenceAlternativesAreDistinctAndDepthIsRespected() {
        val result = predict("ich möchte ", depth = 8)
        assertTrue(result.any { it.commitText.split(' ').size >= 6 })
        assertTrue(result.all { it.commitText.split(' ').size <= 8 })
        assertEquals(result.size, result.map { it.commitText }.distinct().size)
        assertTrue(predict("fun ", mode = PredictionInputMode.CODE).isEmpty())
        assertTrue(predict("suche ", mode = PredictionInputMode.SEARCH).isEmpty())
    }
    @Test fun learnedPhrasesCanGoBeyondTheBundledCorpusWithoutLoops() {
        val learned = object : PredictionMemory {
            override fun boost(contextWords: List<String>, candidate: String) = 30
            override fun learnedFollowers(contextWords: List<String>, limit: Int) = when (contextWords.lastOrNull()) {
                "sternwarte" -> listOf("öffnet" to 90)
                "öffnet" -> listOf("heute" to 90)
                "heute" -> listOf("wieder" to 90)
                "wieder" -> listOf("heute" to 90)
                else -> emptyList()
            }
        }
        val before = "die Sternwarte "
        val result = LocalBeamSemanticProvider(learned).predict(PredictionContext(before, listOf("de"), "de",
            surrounding = LocalContextAnalyzer.analyze(before, "", "")), 4)
        assertTrue(result.any { it.commitText == "öffnet heute wieder" })
        assertTrue(result.none { it.commitText.contains("heute wieder heute") })
    }
}
