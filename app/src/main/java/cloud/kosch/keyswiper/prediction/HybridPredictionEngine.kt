package cloud.kosch.keyswiper.prediction

import cloud.kosch.keyswiper.language.CodeSwitchLanguageResolver

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
            maxSuggestions = maxSuggestions,
            fallbackLanguage = context.inputLanguageTag,
            afterCursor = context.surrounding.afterCursor
        ).filterNot { suggestion ->
            (context.inputMode == PredictionInputMode.CODE || context.inputMode == PredictionInputMode.EMAIL) &&
                (suggestion.kind == PredictionKind.CORRECTION || suggestion.kind == PredictionKind.KEEP_TYPED) ||
                context.inputMode in setOf(PredictionInputMode.CODE, PredictionInputMode.SEARCH) && suggestion.kind == PredictionKind.SENTENCE
        }

        val semanticSuggestions = semantic.predict(
            context = context,
            maxSuggestions = 4
        )

        val singles = instantSuggestions
            .filter { it.kind != PredictionKind.SENTENCE }
            .take(4)

        val sentences = (
            semanticSuggestions + instantSuggestions.filter { it.kind == PredictionKind.SENTENCE }
            )
            .distinctBy { it.commitText }
            .take(2)

        return (singles + sentences)
            .distinctBy { it.commitText }
            .take(maxSuggestions)
    }

    fun mergeNeural(
        base: List<PredictionSuggestion>,
        neural: List<PredictionSuggestion>,
        maxSuggestions: Int = 6,
        inputLanguageTag: String? = null
    ): List<PredictionSuggestion> {
        if (neural.isEmpty()) return base.take(maxSuggestions)

        val languageSafeNeural =
            inputLanguageTag
                ?.let { tag ->
                    neural.filter {
                        CodeSwitchLanguageResolver.matchesLanguage(
                            text = it.commitText,
                            languageTag = tag
                        )
                    }
                }
                ?: neural

        val words = base
            .filter {
                it.kind == PredictionKind.CORRECTION ||
                    it.kind == PredictionKind.KEEP_TYPED ||
                    it.kind == PredictionKind.COMPLETION ||
                    it.kind == PredictionKind.NEXT_WORD
            }
            .take(3)

        val neuralUnique = languageSafeNeural
            .distinctBy { it.commitText }
            .take(2)

        val localSentence = base
            .firstOrNull { it.kind == PredictionKind.SENTENCE }

        return buildList {
            addAll(words)
            addAll(neuralUnique)

            if (
                localSentence != null &&
                none { it.commitText.equals(localSentence.commitText, ignoreCase = true) }
            ) {
                add(localSentence)
            }

            base.forEach { suggestion ->
                if (
                    size < maxSuggestions &&
                    none { it.commitText == suggestion.commitText }
                ) {
                    add(suggestion)
                }
            }
        }.take(maxSuggestions)
    }
}
