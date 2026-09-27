package cloud.kosch.keyswiper.prediction

import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBeamSemanticProviderTest {

    private val memory = object : PredictionMemory {
        override fun boost(contextWords: List<String>, candidate: String): Int =
            if (candidate == "gerne") 25 else 0

        override fun learnedFollowers(
            contextWords: List<String>,
            limit: Int
        ): List<Pair<String, Int>> = emptyList()
    }

    private val provider = LocalBeamSemanticProvider(memory)

    @Test
    fun beamSearchGeneratesMultiWordGermanContinuation() {
        val result = provider.predict(
            PredictionContext(
                beforeCursor = "ich möchte ",
                languageHints = listOf("de"),
                inputMode = PredictionInputMode.GENERAL,
                maxSemanticTokens = 4
            ),
            maxSuggestions = 4
        )

        assertTrue(result.any {
            it.kind == PredictionKind.SENTENCE &&
                it.commitText.split(" ").size >= 2
        })
    }

    @Test
    fun semanticProviderDoesNotRunInsidePartialWord() {
        val result = provider.predict(
            PredictionContext(
                beforeCursor = "ich mö",
                languageHints = listOf("de"),
                maxSemanticTokens = 4
            ),
            maxSuggestions = 4
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun hybridEngineKeepsWordAndSentencePredictions() {
        val instant = ContextPredictionEngine(memory)
        val hybrid = HybridPredictionEngine(instant, provider)

        val result = hybrid.predict(
            PredictionContext(
                beforeCursor = "ich ",
                languageHints = listOf("de"),
                maxSemanticTokens = 4
            ),
            maxSuggestions = 6
        )

        assertTrue(result.any { it.kind == PredictionKind.NEXT_WORD })
        assertTrue(result.any { it.kind == PredictionKind.SENTENCE })
    }
}
