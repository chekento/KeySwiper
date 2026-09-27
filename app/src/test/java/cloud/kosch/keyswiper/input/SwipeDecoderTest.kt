package cloud.kosch.keyswiper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeDecoderTest {

    @Test
    fun singleKeyIsPreservedAndCapitalizedAtSentenceStart() {
        assertEquals("A", SwipeDecoder().decode(listOf('a')).first())
    }

    @Test
    fun traceProducesHelloCandidate() {
        val result = SwipeDecoder().decode(
            trace = listOf('h', 'e', 'l', 'l', 'o'),
            context = "say "
        )
        assertTrue(result.contains("hello"))
    }

    @Test
    fun signatureRemovesRepeatedKeys() {
        assertEquals("helo", SwipeDecoder().signature(listOf('h', 'e', 'l', 'l', 'o')))
    }

    @Test
    fun personalizationCanPromoteARepeatedCorrection() {
        val result = SwipeDecoder().decode(
            trace = listOf('t', 'h', 'r', 'e'),
            context = "I saw ",
            preferredLanguages = listOf("en"),
            personalizationBoost = { _, _, word -> if (word == "three") 20 else 0 }
        )
        assertEquals("three", result.first())
    }

    @Test
    fun preferredLanguageIsAcceptedAsContextSignal() {
        val result = SwipeDecoder().decode(
            trace = listOf('h', 'o', 'l', 'a'),
            context = "es ",
            preferredLanguages = listOf("es")
        )
        assertTrue(result.contains("hola"))
    }
}
