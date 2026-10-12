package cloud.kosch.keyswiper.ui

import cloud.kosch.keyswiper.prediction.PredictionKind
import cloud.kosch.keyswiper.prediction.PredictionSuggestion
import org.junit.Assert.*
import org.junit.Test

class SuggestionBarModelTest {
    private fun suggestion(value: String, kind: PredictionKind = PredictionKind.NEXT_WORD) = PredictionSuggestion(value, value, kind)

    @Test fun sentencesNeverPushWordsOutOfTheThreeFixedSlots() {
        val sentence = suggestion("Ich freue mich auf morgen", PredictionKind.SENTENCE)
        val neural = suggestion("Wir sehen uns bald wieder", PredictionKind.NEURAL)
        val words = listOf(suggestion("Hallo"), suggestion("Danke"), suggestion("Morgen"))
        val bar = SuggestionBarModel.from(listOf(sentence, words[0], neural, words[1], words[2]))
        assertEquals(words, bar.words)
        assertEquals(listOf(sentence, neural), bar.phrases)
        assertTrue(bar.hasMore)
    }

    @Test fun originalSpellingRemainsDirectlySelectableAndOverflowIsRetained() {
        val original = suggestion("KoSch", PredictionKind.KEEP_TYPED)
        val values = listOf(suggestion("Kosch"), suggestion("Koch"), suggestion("Korb"), original)
        val bar = SuggestionBarModel.from(values)
        assertEquals(listOf(values[0], values[1], original), bar.words)
        assertEquals(listOf(values[2]), bar.moreWords)
    }

    @Test fun emptyOrDuplicateResultsDoNotCreateExtraChoices() {
        assertFalse(SuggestionBarModel.from(emptyList()).hasMore)
        val word = suggestion("Hallo", PredictionKind.SWIPE_CORRECTION)
        assertEquals(listOf(word), SuggestionBarModel.from(listOf(word, word)).words)
    }
}
