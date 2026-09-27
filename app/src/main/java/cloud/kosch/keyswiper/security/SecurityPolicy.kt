package cloud.kosch.keyswiper.security

import android.text.InputType
import android.view.inputmethod.EditorInfo

object SecurityPolicy {
    fun isSensitive(info: EditorInfo?): Boolean {
        if (info == null) return false
        val typeClass = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION

        val textPassword = typeClass == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        val numberPassword = typeClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val noLearning = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        return textPassword || numberPassword || noLearning
    }
}
