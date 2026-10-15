package cloud.kosch.keyswiper.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceTranscriptBufferTest {
    @Test fun combinesSentencesWithoutDuplicatingPartial() {
        val buffer = VoiceTranscriptBuffer()
        assertTrue(buffer.add("Guten Morgen."))
        assertEquals("Guten Morgen. Wie geht es dir", buffer.preview("Wie geht es dir"))
        assertTrue(buffer.add("Wie geht es dir?"))
        assertEquals("Guten Morgen. Wie geht es dir?", buffer.text)
        assertFalse(buffer.add("Wie geht es dir?"))
    }
    @Test fun enforcesBoundedSession() {
        val buffer = VoiceTranscriptBuffer(12)
        assertTrue(buffer.add("Hallo"))
        assertFalse(buffer.add("dies ist lang"))
        assertEquals("Hallo", buffer.text)
    }
}
