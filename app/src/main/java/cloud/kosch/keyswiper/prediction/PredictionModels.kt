package cloud.kosch.keyswiper.prediction

enum class PredictionKind {
    COMPLETION,
    NEXT_WORD,
    SENTENCE,
    SWIPE_CORRECTION
}

data class PredictionSuggestion(
    val display: String,
    val commitText: String,
    val kind: PredictionKind,
    val replacesCurrentToken: Boolean = false,
    val confidence: Float = 0.5f
)

interface PredictionMemory {
    fun boost(contextWords: List<String>, candidate: String): Int
    fun learnedFollowers(contextWords: List<String>, limit: Int = 8): List<Pair<String, Int>>
}
