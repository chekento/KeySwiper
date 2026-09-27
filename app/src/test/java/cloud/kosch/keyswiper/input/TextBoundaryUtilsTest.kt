package cloud.kosch.keyswiper.input

import org.junit.Assert.assertEquals
import org.junit.Test

class TextBoundaryUtilsTest {

    @Test
    fun deletesWholeSurrogatePair() {
        assertEquals(
            2,
            TextBoundaryUtils
                .lastGraphemeUtf16Length(
                    "Hello 😀"
                )
        )
    }

    @Test
    fun deletesCombiningSequenceTogether() {
        assertEquals(
            2,
            TextBoundaryUtils
                .lastGraphemeUtf16Length(
                    "Cafe e\u0301"
                )
        )
    }

    @Test
    fun deletesAsciiCharacterNormally() {
        assertEquals(
            1,
            TextBoundaryUtils
                .lastGraphemeUtf16Length(
                    "Hello"
                )
        )
    }
}
