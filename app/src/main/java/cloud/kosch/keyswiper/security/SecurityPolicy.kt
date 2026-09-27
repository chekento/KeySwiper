package cloud.kosch.keyswiper.security

import android.text.InputType
import android.view.inputmethod.EditorInfo

object SecurityPolicy {
    fun isSensitive(info: EditorInfo?): Boolean {
        if (info == null) return false
        return isSensitiveInputType(info.inputType) ||
            (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
    }

    fun isSensitiveInputType(inputType: Int): Boolean {
        val typeClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        val textPassword =
            typeClass == InputType.TYPE_CLASS_TEXT &&
                variation in setOf(
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                )

        val numberPassword =
            typeClass == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        return textPassword || numberPassword
    }
}
