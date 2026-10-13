package cloud.kosch.keyswiper.ui

import android.text.InputType
import org.junit.Assert.*
import org.junit.Test

class NumericKeypadProfileTest {
    private fun tokens(type: Int) = checkNotNull(NumericKeypadProfile.fromInputType(type)).rows.flatten()
        .map { it.token }.filterNot { it in listOf(NumericKeypadProfile.EMPTY, NumericKeypadProfile.ENTER, KeyboardLayoutProfiles.BACKSPACE) }

    @Test fun integerAndPinFieldsShowExactlyTenDigits() {
        val digits = (0..9).map { it.toString() }.toSet()
        assertEquals(digits, tokens(InputType.TYPE_CLASS_NUMBER).toSet())
        val pin = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD or
            InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
        assertEquals(digits, tokens(pin).toSet())
        assertEquals(10, tokens(InputType.TYPE_CLASS_NUMBER).size)
    }

    @Test fun decimalAndMinusAreShownOnlyWhenRequestedByTheField() {
        val integer = tokens(InputType.TYPE_CLASS_NUMBER)
        assertFalse(integer.contains(".")); assertFalse(integer.contains("-"))
        assertTrue(tokens(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).contains("."))
        assertFalse(tokens(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).contains("-"))
        assertTrue(tokens(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED).contains("-"))
        assertFalse(tokens(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED).contains("."))
    }

    @Test fun phoneAndDateFieldsKeepTheirRequiredSeparatorsWithoutLetters() {
        assertTrue(tokens(InputType.TYPE_CLASS_PHONE).containsAll(listOf("+", "*", "#")))
        assertTrue(tokens(InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME).contains(":"))
        for (type in listOf(InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME)) {
            assertEquals(KeyboardEditorMode.NUMBER, KeyboardEditorModeResolver.fromInputType(type))
            assertFalse(KeyboardEditorModeResolver.allowsWordPrediction(type))
            assertTrue(tokens(type).none { token -> token.any { it.isLetter() } })
        }
        assertNull(NumericKeypadProfile.fromInputType(InputType.TYPE_CLASS_TEXT))
    }
}
