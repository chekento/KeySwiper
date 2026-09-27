package cloud.kosch.keyswiper.ui

import android.text.InputType

enum class KeyboardEditorMode {
    TEXT,
    EMAIL,
    URL,
    NUMBER
}

object KeyboardEditorModeResolver {

    fun fromInputType(
        inputType: Int
    ): KeyboardEditorMode {
        val typeClass =
            inputType and
                InputType.TYPE_MASK_CLASS

        if (
            typeClass ==
                InputType.TYPE_CLASS_NUMBER ||
            typeClass ==
                InputType.TYPE_CLASS_PHONE
        ) {
            return KeyboardEditorMode.NUMBER
        }

        if (
            typeClass !=
            InputType.TYPE_CLASS_TEXT
        ) {
            return KeyboardEditorMode.TEXT
        }

        val variation =
            inputType and
                InputType.TYPE_MASK_VARIATION

        return when (variation) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ->
                KeyboardEditorMode.EMAIL

            InputType.TYPE_TEXT_VARIATION_URI ->
                KeyboardEditorMode.URL

            else ->
                KeyboardEditorMode.TEXT
        }
    }

    fun allowsWordPrediction(
        inputType: Int
    ): Boolean {
        val typeClass =
            inputType and
                InputType.TYPE_MASK_CLASS

        return typeClass !=
            InputType.TYPE_CLASS_NUMBER &&
            typeClass !=
            InputType.TYPE_CLASS_PHONE
    }
}
