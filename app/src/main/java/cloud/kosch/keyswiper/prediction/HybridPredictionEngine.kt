package cloud.kosch.keyswiper.prediction

class HybridPredictionEngine(
    private val instant: ContextPredictionEngine,
    private val semantic: PredictionProvider
) {

    fun predict(
        context: PredictionContext,
        maxSuggestions: Int = 6
    ): List<PredictionSuggestion> {
        val instantSuggestions = instant.predict(
            beforeCursor = context.beforeCursor,
            languageHints = context.languageHints,
            maxSuggestions = maxSuggestions
        )

        val semanticSuggestions = semantic.predict(
            context = context,
            maxSuggestions = 4
        )

        val singles = instantSuggestions
            .filter { it.kind != PredictionKind.SENTENCE }
            .take(4)

        val sentences = (
            instantSuggestions.filter { it.kind == PredictionKind.SENTENCE } +
                semanticSuggestions
            )
            .distinctBy { it.commitText.lowercase() }
            .sortedByDescending { it.confidence }
            .take(2)

        return (singles + sentences)
            .distinctBy { it.commitText.lowercase() }
            .take(maxSuggestions)
    }
}
