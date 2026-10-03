package cloud.kosch.keyswiper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import cloud.kosch.keyswiper.ui.KeyboardLayoutProfiles

class SwipeDecoderTest {

    // Real strokes visit intermediate keys rather than reporting only the intended letters.
    private fun denseTrace(word: String, layout: String = "en-qwerty", drift: Float = 0f): SwipeTrace {
        val centers = word.map { KeyboardGeometry.center(it, layout)!! }
        val points = mutableListOf<SwipePoint>()
        centers.zipWithNext().forEach { (a, b) ->
            repeat(12) { step ->
                val fraction = step / 12f
                points.add(SwipePoint(
                    a.first + (b.first - a.first) * fraction + drift,
                    a.second + (b.second - a.second) * fraction,
                    points.size * 8L
                ))
            }
        }
        points.add(SwipePoint(centers.last().first + drift, centers.last().second, points.size * 8L))
        val rows = KeyboardLayoutProfiles.byId(layout).letterRows
        val touched = points.mapNotNull { point ->
            val rowIndex = (point.y * 3).toInt().coerceIn(0, 2)
            val row = rows[rowIndex]
            val columns = row.length + if (rowIndex == 2) 1 else 0
            row.getOrNull((point.x * columns).toInt())
        }
        return SwipeTrace(points, touched, layout)
    }

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

    @Test
    fun germanDailyWordsSurviveTransitKeysAndRepeatedLetters() {
        val decoder = SwipeDecoder()
        for (word in listOf("hallo", "danke", "morgen", "tastatur", "schreiben", "test")) {
            val result = decoder.decode(denseTrace(word, "de-qwertz"), " ", listOf("de"))
            assertEquals("Wrong best candidate for $word: $result", word, result.first().lowercase())
        }
    }

    @Test
    fun englishDailyWordsHandleContinuousPathsAndSmallOffset() {
        val decoder = SwipeDecoder()
        for (word in listOf("hello", "world", "thanks", "keyboard", "tomorrow", "dad")) {
            val result = decoder.decode(denseTrace(word, drift = 0.018f), "say ", listOf("en"))
            assertEquals("Wrong best candidate for $word: $result", word, result.first())
        }
    }

    @Test
    fun learnedWordsOutsideBundledDictionaryCanBeSwiped() {
        val result = SwipeDecoder().decode(
            denseTrace("keyswiper"), "use ", listOf("en"),
            additionalWords = listOf("KeySwiper")
        )
        assertEquals("keyswiper", result.first())
    }

    @Test
    fun contextCannotOverrideAnUnrelatedClearShape() {
        val result = SwipeDecoder().decode(
            denseTrace("world"), "the ", listOf("en"),
            personalizationBoost = { _, _, candidate -> if (candidate == "would") 24 else 0 }
        )
        assertEquals("world", result.first())
    }
}
