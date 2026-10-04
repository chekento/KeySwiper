package cloud.kosch.keyswiper.input

import org.junit.Assert.*
import org.junit.Test

class PendingAutoCorrectionTest {
    private val pending = PendingAutoCorrection("udn", "und ", "Hallo und ", "morgen", 1)
    @Test fun backspaceUndoRequiresTheOriginalCursorContext() {
        assertTrue(pending.matches("Hallo und ", "morgen", ""))
        assertFalse(pending.matches("Anders und ", "morgen", ""))
        assertFalse(pending.matches("Hallo und x", "morgen", ""))
        assertFalse(pending.matches("Hallo und ", "heute", ""))
        assertFalse(pending.matches("Hallo und ", "morgen", "Text"))
    }
}
