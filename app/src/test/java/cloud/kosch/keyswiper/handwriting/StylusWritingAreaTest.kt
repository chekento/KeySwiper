package cloud.kosch.keyswiper.handwriting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StylusWritingAreaTest {
    private val writingArea = StylusWritingArea(0f, 24f, 1080f, 1400f)

    @Test
    fun strokeStaysInWritingArea() {
        val boundary = StylusStrokeBoundary()
        assertTrue(boundary.begin(200f, 300f, writingArea))
        assertTrue(boundary.continueAt(400f, 600f))
    }

    @Test
    fun strokeStartingOnKeyboardCannotBecomeHandwriting() {
        val boundary = StylusStrokeBoundary()
        assertFalse(boundary.begin(200f, 1500f, writingArea))
        assertFalse(boundary.continueAt(400f, 600f))
    }

    @Test
    fun crossingIntoKeyboardCancelsWholeStrokeUntilNextPenDown() {
        val boundary = StylusStrokeBoundary()
        assertTrue(boundary.begin(200f, 300f, writingArea))
        assertFalse(boundary.continueAt(400f, 1400f))
        assertFalse(boundary.continueAt(400f, 600f))
        assertTrue(boundary.begin(200f, 300f, writingArea))
    }

    @Test
    fun systemBarsAndScreenEdgesAreExcluded() {
        val boundary = StylusStrokeBoundary()
        assertFalse(boundary.begin(20f, 10f, writingArea))
        assertFalse(boundary.begin(1080f, 100f, writingArea))
        assertFalse(boundary.begin(-1f, 100f, writingArea))
    }
}
