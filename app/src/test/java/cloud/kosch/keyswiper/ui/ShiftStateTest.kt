package cloud.kosch.keyswiper.ui

import org.junit.Assert.*
import org.junit.Test

class ShiftStateTest {
    @Test fun threePhasesCycleRegardlessOfTapTiming() {
        val state = ShiftState()
        assertFalse(state.shifted)
        state.tap()
        assertEquals(ShiftMode.UPPER, state.mode)
        assertTrue(state.shifted)
        state.tap()
        assertEquals(ShiftMode.LOCKED, state.mode)
        state.tap()
        assertEquals(ShiftMode.LOWER, state.mode)
        assertFalse(state.shifted)
    }

    @Test fun normalShiftReturnsToLowerAfterLetterButNotPunctuation() {
        val state = ShiftState()
        state.tap()
        state.consume(".")
        assertEquals(ShiftMode.UPPER, state.mode)
        state.consume("Abc")
        assertEquals(ShiftMode.LOWER, state.mode)
        assertFalse(state.shifted)
    }

    @Test fun capsLockRemainsUntilExplicitlyTurnedOff() {
        val state = ShiftState()
        state.tap(); state.tap()
        state.consume("ABC")
        state.setAutomatic(false)
        assertTrue(state.locked)
        assertTrue(state.shifted)
        state.tap()
        assertFalse(state.locked)
        assertFalse(state.shifted)
    }

    @Test fun autoShiftDoesNotOverrideLockedOrManualLower() {
        val state = ShiftState()
        state.setAutomatic(true)
        assertTrue(state.shifted)
        state.tap() // visible uppercase -> lock
        assertEquals(ShiftMode.LOCKED, state.mode)
        state.setAutomatic(false)
        assertTrue(state.shifted)
        state.tap() // lock -> explicitly lowercase
        state.setAutomatic(true)
        assertFalse(state.shifted)
        state.consume("a")
        state.setAutomatic(false)
        assertFalse(state.shifted)
    }

    @Test fun newEditorResetsExplicitState() {
        val state = ShiftState()
        state.tap(); state.tap()
        state.reset()
        assertFalse(state.shifted)
        assertEquals(ShiftMode.LOWER, state.mode)
    }
}
