package cloud.kosch.keyswiper.handwriting

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class StylusTapClassifierTest {
    @Test fun shortStationaryContactIsACursorTap() {
        assertTrue(StylusTapClassifier.isTap(listOf(StylusScreenPoint(100f, 100f, 0), StylusScreenPoint(104f, 103f, 120)), 1f))
    }
    @Test fun longStrokeAndLongPressAreNotTaps() {
        assertFalse(StylusTapClassifier.isTap(listOf(StylusScreenPoint(100f, 100f, 0), StylusScreenPoint(140f, 103f, 120)), 1f))
        assertFalse(StylusTapClassifier.isTap(listOf(StylusScreenPoint(100f, 100f, 0), StylusScreenPoint(101f, 100f, 700)), 1f))
    }
    private fun circle(radius: Float) = (0..24).map { i ->
        val angle = 2 * PI * i / 24
        StylusScreenPoint(100f + cos(angle).toFloat() * radius, 100f + sin(angle).toFloat() * radius, i * 20L)
    }
    @Test fun compactSelectionCircleIsNotHandwrittenO() {
        assertTrue(ExtendedHandwritingGestureClassifier.classify(circle(15f), 1f) is ExtendedHandwritingGesture.SelectArea)
        assertNull(ExtendedHandwritingGestureClassifier.classify(circle(5f), 1f))
    }
}
