package cloud.kosch.keyswiper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeDecoderTest {

    private fun keyTrace(word: String): SwipeTrace {
        val points = word.mapIndexedNotNull { index, c ->
            KeyboardGeometry.center(c)?.let { center ->
                SwipePoint(center.first, center.second, index * 40L)
            }
        }
        return SwipeTrace(points = points, touchedKeys = word.toList())
    }

    @Test
    fun singleKeyIsPreservedAndCapitalizedAtSentenceStart() {
        val trace = SwipeTrace(
            points = listOf(SwipePoint(0.1f, 0.2f, 0L)),
            touchedKeys = listOf('a')
        )
        assertEquals("A", SwipeDecoder().decode(trace).first())
    }

    @Test
    fun geometricHelloTraceProducesHelloCandidate() {
        val result = SwipeDecoder().decode(
            trace = keyTrace("hello"),
            context = "say ",
            preferredLanguages = listOf("en")
        )
        assertTrue(result.contains("hello"))
    }

    @Test
    fun signatureRemovesRepeatedTouchedKeys() {
        assertEquals("helo", SwipeDecoder().signature(keyTrace("hello")))
    }

    @Test
    fun personalizationCanPromoteARepeatedCorrection() {
        val result = SwipeDecoder().decode(
            trace = keyTrace("thre"),
            context = "I saw ",
            preferredLanguages = listOf("en"),
            personalizationBoost = { _, _, word -> if (word == "three") 20 else 0 }
        )
        assertEquals("three", result.first())
    }

    @Test
    fun preferredLanguageIsAcceptedAsContextSignal() {
        val result = SwipeDecoder().decode(
            trace = keyTrace("hola"),
            context = "es ",
            preferredLanguages = listOf("es")
        )
        assertTrue(result.contains("hola"))
    }

    @Test
    fun idealGeometryScoresBetterThanWrongWordGeometry() {
        val trace = keyTrace("hello")
        val helloScore = SwipeGeometryScorer.score(trace, "hello")
        val worldScore = SwipeGeometryScorer.score(trace, "world")
        assertTrue(helloScore < worldScore)
    }

    @Test
    fun motorOffsetEstimatorDetectsShiftedTrace() {
        val base = keyTrace("hello")
        val shifted = base.copy(
            points = base.points.map { p ->
                p.copy(
                    x = (p.x + 0.03f).coerceAtMost(1f),
                    y = (p.y - 0.02f).coerceAtLeast(0f)
                )
            }
        )

        val offsets = SwipeGeometryScorer.estimateLetterOffsets(shifted, "hello")
        val h = offsets['h'] ?: error("Expected h offset")
        assertTrue(h.dx > 0f)
        assertTrue(h.dy < 0f)
    }
}
