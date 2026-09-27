package cloud.kosch.keyswiper.stylus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StylusClickInterpreterTest {

    @Test
    fun secondPressInsideWindowIsDoubleClick() {
        val interpreter = StylusClickInterpreter(280L)

        assertFalse(
            interpreter.registerPrimaryPress(1_000L)
        )
        assertTrue(
            interpreter.registerPrimaryPress(1_180L)
        )
    }

    @Test
    fun lateSecondPressStartsNewSingle() {
        val interpreter = StylusClickInterpreter(280L)

        assertFalse(
            interpreter.registerPrimaryPress(1_000L)
        )
        assertFalse(
            interpreter.registerPrimaryPress(1_500L)
        )
    }

    @Test
    fun pendingSingleCanOnlyBeConsumedOnce() {
        val interpreter = StylusClickInterpreter(280L)

        interpreter.registerPrimaryPress(1_000L)

        assertTrue(
            interpreter.consumePendingSingle(1_000L)
        )
        assertFalse(
            interpreter.consumePendingSingle(1_000L)
        )
    }
}
