package cloud.kosch.keyswiper.clipboard

import org.junit.Assert.*
import org.junit.Test

class ClipboardRetentionTest {
    private fun entry(expires: Long?, pinned: Boolean = false, saved: Boolean = false) =
        ClipboardEntry("id", "Text", ClipboardCategory.TEXT, 1000, expires, pinned, saved)
    @Test fun temporaryEntriesExpireAtTheirStoredDeadline() {
        assertTrue(ClipboardRetention.alive(entry(2000), 1999))
        assertFalse(ClipboardRetention.alive(entry(2000), 2000))
        assertFalse(ClipboardRetention.alive(entry(null), 2000))
    }
    @Test fun pinsAndSavedSnippetsAreIndependentPermanentChoices() {
        assertTrue(ClipboardRetention.alive(entry(null, pinned = true), Long.MAX_VALUE))
        assertTrue(ClipboardRetention.alive(entry(null, saved = true), Long.MAX_VALUE))
        assertFalse(ClipboardRetention.alive(entry(2000), 3000))
    }
    @Test fun retentionSupportsThirtyDaysWithoutChangingAnExistingDeadline() {
        assertEquals(1000L + 30L * 24 * 60 * 60_000, ClipboardRetention.expiresAt(1000, 43200))
        assertEquals(1000L + 10 * 60_000, ClipboardRetention.expiresAt(1000, 0))
    }
}
