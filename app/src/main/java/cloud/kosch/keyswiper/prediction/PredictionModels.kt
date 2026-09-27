package cloud.kosch.keyswiper.prediction

enum class PredictionKind {
    COMPLETION,
    NEXT_WORD,
    SENTENCE,
    SWIPE_CORRECTION
}

enum class PredictionInputMode {
    GENERAL,
    MESSAGE,
    EMAIL,
    SEARCH,
    CODE
}

data class PredictionSuggestion(
    val display: String,
    val commitText: String,
    val kind: PredictionKind,
    val replacesCurrentToken: Boolean = false,
    val confidence: Float = 0.5f
)

data class PredictionContext(
    val beforeCursor: String,
    val languageHints: List<String>,
    val inputMode: PredictionInputMode = PredictionInputMode.GENERAL,
    val maxSemanticTokens: Int = 4
)

interface PredictionMemory {
    fun boost(contextWords: List<String>, candidate: String): Int
    fun learnedFollowers(contextWords: List<String>, limit: Int = 8): List<Pair<String, Int>>
}

interface PredictionProvider {
    val id: String
    fun predict(context: PredictionContext, maxSuggestions: Int): List<PredictionSuggestion>
}
