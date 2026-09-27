package cloud.kosch.keyswiper.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalContextAnalyzerTest {

    @Test
    fun extractsCurrentAndNeighbouringSentences() {
        val snapshot = LocalContextAnalyzer.analyze(
            beforeCursor = "Die App ist schnell. Die Vorschläge kennen jetzt den Kontext",
            selectedText = "",
            afterCursor = " und werden dadurch besser. Danach teste ich weiter.",
        )

        assertTrue(snapshot.previousSentence.contains("App ist schnell"))
        assertTrue(snapshot.currentSentenceBefore.contains("Vorschläge"))
        assertTrue(snapshot.currentSentenceAfter.startsWith("und werden"))
        assertTrue(snapshot.nextSentence.contains("Danach"))
    }

    @Test
    fun extractsTopicTermsFromSurroundingParagraph() {
        val snapshot = LocalContextAnalyzer.analyze(
            beforeCursor = "Die Tastatur braucht intelligente Prediction. Prediction soll den Kontext kennen. ",
            selectedText = "",
            afterCursor = "Die Tastatur lernt lokal."
        )

        assertTrue(snapshot.topicTerms.contains("prediction"))
        assertTrue(snapshot.topicTerms.contains("tastatur"))
    }

    @Test
    fun detectsQuestionIntent() {
        val snapshot = LocalContextAnalyzer.analyze(
            beforeCursor = "Wie können wir die Prediction",
            selectedText = "",
            afterCursor = " verbessern?"
        )

        assertTrue(snapshot.isQuestion)
        assertEquals("verbessern", snapshot.topicTerms.firstOrNull { it == "verbessern" })
    }
}
