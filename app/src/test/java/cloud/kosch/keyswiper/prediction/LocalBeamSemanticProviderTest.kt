package cloud.kosch.keyswiper.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBeamSemanticProviderTest {

    private val memory = object : PredictionMemory {
        override fun boost(
            contextWords: List<String>,
            candidate: String
        ): Int =
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
                maxSemanticTokens = 4,
                surrounding = LocalContextAnalyzer.analyze(
                    "ich möchte ",
                    "",
                    ""
                )
            ),
            maxSuggestions = 4
        )

        assertTrue(
            result.any {
                it.kind == PredictionKind.SENTENCE &&
                    it.commitText.split(" ").size >= 2
            }
        )
    }

    @Test
    fun semanticProviderDoesNotRunInsidePartialWord() {
        val result = provider.predict(
            PredictionContext(
                beforeCursor = "ich mö",
                languageHints = listOf("de"),
                maxSemanticTokens = 4,
                surrounding = LocalContextAnalyzer.analyze(
                    "ich mö",
                    "",
                    ""
                )
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
                maxSemanticTokens = 4,
                surrounding = LocalContextAnalyzer.analyze(
                    "ich ",
                    "",
                    ""
                )
            ),
            maxSuggestions = 6
        )

        assertTrue(result.any { it.kind == PredictionKind.NEXT_WORD })
        assertTrue(result.any { it.kind == PredictionKind.SENTENCE })
    }

    @Test
    fun contextTopicsCanProduceContextRelatedCandidates() {
        val snapshot = LocalContextAnalyzer.analyze(
            beforeCursor = "Die Tastatur braucht bessere Prediction. kontext ",
            selectedText = "",
            afterCursor = ""
        )

        val result = provider.predict(
            PredictionContext(
                beforeCursor = snapshot.beforeCursor,
                languageHints = listOf("de"),
                surrounding = snapshot,
                maxSemanticTokens = 4
            ),
            maxSuggestions = 8
        )

        assertTrue(
            result.any {
                it.commitText.contains("kennen") ||
                    it.commitText.contains("nutzen") ||
                    it.commitText.contains("verstehen") ||
                    it.commitText.contains("berücksichtigen")
            }
        )
    }

    @Test
    fun neuralMergePreservesFastWordsAndAddsNeuralSuggestion() {
        val instant = ContextPredictionEngine(memory)
        val hybrid = HybridPredictionEngine(instant, provider)

        val base = listOf(
            PredictionSuggestion(
                "möchte",
                "möchte",
                PredictionKind.NEXT_WORD,
                confidence = 0.9f
            ),
            PredictionSuggestion(
                "kann",
                "kann",
                PredictionKind.NEXT_WORD,
                confidence = 0.8f
            ),
            PredictionSuggestion(
                "→ gerne weiter",
                "gerne weiter",
                PredictionKind.SENTENCE,
                confidence = 0.7f
            )
        )

        val neural = listOf(
            PredictionSuggestion(
                "✦ den aktuellen Kontext nutzen",
                "den aktuellen Kontext nutzen",
                PredictionKind.NEURAL,
                confidence = 0.95f
            )
        )

        val merged = hybrid.mergeNeural(base, neural, 6)

        assertEquals("möchte", merged.first().commitText)
        assertTrue(merged.any { it.kind == PredictionKind.NEURAL })
        assertTrue(merged.any { it.kind == PredictionKind.SENTENCE })
    }
}
