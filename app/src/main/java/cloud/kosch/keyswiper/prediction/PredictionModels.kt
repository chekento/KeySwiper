package cloud.kosch.keyswiper.prediction

enum class PredictionKind {
    COMPLETION,
    NEXT_WORD,
    SENTENCE,
    SWIPE_CORRECTION,
    NEURAL
}

enum class PredictionInputMode {
    GENERAL,
    MESSAGE,
    EMAIL,
    SEARCH,
    CODE
}

data class SurroundingContextSnapshot(
    val beforeCursor: String = "",
    val selectedText: String = "",
    val afterCursor: String = "",
    val previousSentence: String = "",
    val currentSentenceBefore: String = "",
    val currentSentenceAfter: String = "",
    val nextSentence: String = "",
    val currentParagraph: String = "",
    val topicTerms: List<String> = emptyList(),
    val isQuestion: Boolean = false
)

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
    val maxSemanticTokens: Int = 4,
    val surrounding: SurroundingContextSnapshot = SurroundingContextSnapshot(
        beforeCursor = beforeCursor
    )
)

interface PredictionMemory {
    fun boost(contextWords: List<String>, candidate: String): Int
    fun learnedFollowers(contextWords: List<String>, limit: Int = 8): List<Pair<String, Int>>
}

interface PredictionProvider {
    val id: String
    fun predict(context: PredictionContext, maxSuggestions: Int): List<PredictionSuggestion>
}
