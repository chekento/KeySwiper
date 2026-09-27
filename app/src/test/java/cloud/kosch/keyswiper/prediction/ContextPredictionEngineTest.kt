package cloud.kosch.keyswiper.prediction

import org.junit.Assert.assertTrue
import org.junit.Test

class ContextPredictionEngineTest {

    private val memory = object : PredictionMemory {
        override fun boost(
            contextWords: List<String>,
            candidate: String
        ): Int = 0

        override fun learnedFollowers(
            contextWords: List<String>,
            limit: Int
        ): List<Pair<String, Int>> = emptyList()
    }

    private val engine = ContextPredictionEngine(memory)

    @Test
    fun completesGermanPartialWord() {
        val suggestions = engine.predict("Ich mö", listOf("de"))

        assertTrue(
            suggestions.any {
                it.kind == PredictionKind.COMPLETION &&
                    it.commitText == "möchte"
            }
        )
    }

    @Test
    fun predictsGermanNextWordFromContext() {
        val suggestions = engine.predict("ich ", listOf("de"))

        assertTrue(
            suggestions.any {
                it.kind == PredictionKind.NEXT_WORD &&
                    it.commitText == "möchte"
            }
        )
    }

    @Test
    fun offersSentenceContinuation() {
        val suggestions = engine.predict(
            "ich möchte ",
            listOf("de")
        )

        assertTrue(
            suggestions.any {
                it.kind == PredictionKind.SENTENCE &&
                    it.commitText.contains("ergänzen")
            }
        )
    }

    @Test
    fun mixedLanguageContextOffersEnglishLanePrediction() {
        val suggestions = engine.predict(
            "Ich brauche more ",
            listOf("de", "en"),
            maxSuggestions = 8
        )

        assertTrue(
            suggestions.any {
                it.commitText == "context" ||
                    it.commitText == "details" ||
                    it.commitText == "intelligence"
            }
        )
    }
}
