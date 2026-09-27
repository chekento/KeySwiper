package cloud.kosch.keyswiper.ui

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KeyboardEditorModeResolverTest {

    @Test
    fun detectsSpecialEditorModes() {
        assertEquals(
            KeyboardEditorMode.NUMBER,
            KeyboardEditorModeResolver
                .fromInputType(
                    InputType.TYPE_CLASS_NUMBER
                )
        )

        assertEquals(
            KeyboardEditorMode.EMAIL,
            KeyboardEditorModeResolver
                .fromInputType(
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                )
        )

        assertEquals(
            KeyboardEditorMode.URL,
            KeyboardEditorModeResolver
                .fromInputType(
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_URI
                )
        )
    }

    @Test
    fun numericEditorsDisableWordPrediction() {
        assertFalse(
            KeyboardEditorModeResolver
                .allowsWordPrediction(
                    InputType.TYPE_CLASS_NUMBER
                )
        )
    }
}
