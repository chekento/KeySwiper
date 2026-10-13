package cloud.kosch.keyswiper.ui

import cloud.kosch.keyswiper.prediction.PredictionKind
import cloud.kosch.keyswiper.prediction.PredictionSuggestion

data class SuggestionBarModel(
    val words: List<PredictionSuggestion>,
    val moreWords: List<PredictionSuggestion>,
    val phrases: List<PredictionSuggestion>
) {
    val hasMore: Boolean get() = moreWords.isNotEmpty() || phrases.isNotEmpty()

    companion object {
        fun from(values: List<PredictionSuggestion>): SuggestionBarModel {
            val distinct = values.distinctBy { it.commitText to it.kind }
            val phrases = distinct.filter { it.kind == PredictionKind.SENTENCE || it.kind == PredictionKind.NEURAL }
            val allWords = distinct.filterNot { it in phrases }
            val visible = allWords.take(3).toMutableList()
            // The original spelling must remain directly selectable.
            val original = allWords.firstOrNull { it.kind == PredictionKind.KEEP_TYPED }
            if (original != null && original !in visible && visible.isNotEmpty()) visible[visible.lastIndex] = original
            return SuggestionBarModel(visible, allWords.filterNot { it in visible }, phrases)
        }
    }
}
