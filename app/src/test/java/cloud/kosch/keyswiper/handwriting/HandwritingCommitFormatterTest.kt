package cloud.kosch.keyswiper.handwriting

import org.junit.Assert.assertEquals
import org.junit.Test

class HandwritingCommitFormatterTest {

    @Test
    fun appendsSpaceAfterRecognizedWord() {
        assertEquals(
            "Hallo ",
            HandwritingCommitFormatter.formatRecognition("  Hallo ")
        )
    }

    @Test
    fun emptyRecognitionStaysEmpty() {
        assertEquals(
            "",
            HandwritingCommitFormatter.formatRecognition("   ")
        )
    }
}
