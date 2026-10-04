package cloud.kosch.keyswiper.input

import org.junit.Assert.*
import org.junit.Test

class WordCommitPlanTest {
    private fun apply(value: String, before: String = "", after: String = "",
        selected: String = "", completion: Boolean = false): Pair<String, Int> {
        val plan = checkNotNull(WordCommitPlan.create(value, before, after, selected, completion))
        val left = before.dropLast(plan.deleteBefore) + plan.insertedText
        return (left + after.drop(plan.deleteAfter)) to left.length
    }

    @Test fun consecutiveWordsNeedNoManualSpace() {
        val first = apply("Hallo")
        val second = apply("Welt", first.first)
        assertEquals("Hallo Welt ", second.first)
        assertEquals(second.first.length, second.second)
        assertEquals("Hallo Welt w", second.first + "w")
    }

    @Test fun switchingFromTypingToSwipeSeparatesBothWords() {
        assertEquals("Hallo Welt " to 11, apply("Welt", "Hallo"))
    }

    @Test fun acceptingCompletionReplacesThePrefixAndFinishesTheWord() {
        assertEquals("Guten Morgen " to 13, apply("Morgen", "Guten Mor", completion = true))
    }

    @Test fun existingSpaceMovesBehindTheCompletedWordWithoutDoubling() {
        val result = apply("Hallo", "Hal", " Welt", completion = true)
        assertEquals("Hallo Welt" to 6, result)
        assertEquals("Hallo schöne Welt", result.first.take(result.second) + "schöne " + result.first.drop(result.second))
    }

    @Test fun aSelectionDoesNotDeleteThePrecedingWord() {
        assertEquals("Guten Abend morgen" to 12,
            apply("Abend", "Guten ", " morgen", "Tag", completion = true))
    }

    @Test fun providerWhitespaceCannotProduceDoubleSeparators() {
        assertEquals("Hallo Welt " to 11, apply("  Welt  ", "Hallo "))
        assertNull(WordCommitPlan.create("  ", "Hallo", ""))
    }

    @Test fun lineBreaksAndOpeningPunctuationArePreserved() {
        assertEquals("(Hallo " to 7, apply("Hallo", "("))
        assertEquals("Hallo \nWelt" to 6, apply("Hallo", after = "\nWelt"))
        assertEquals("Hallo  Welt" to 6, apply("Hallo", after = "  Welt"))
    }
    @Test fun completionReplacesBothSidesOfTheCursor() {
        assertEquals("Guten Morgen Welt" to 13, apply("Morgen", "Guten Mo", "rgen Welt", completion = true))
        assertEquals("Tastatur " to 9, apply("Tastatur", "Ta", "statru", completion = true))
        assertEquals("Hallo Welt" to 6, apply("Hallo", "", "Hal Welt", completion = true))
        val plan = checkNotNull(WordCommitPlan.create("Tastatur", "Ta", "statru", replacesCurrentToken = true))
        assertEquals("Tastatru", plan.deletedText)
    }
}
