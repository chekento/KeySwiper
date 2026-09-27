package cloud.kosch.keyswiper.prediction

import android.text.InputType
import android.view.inputmethod.EditorInfo

object PredictionContextClassifier {

    fun classify(info: EditorInfo?): PredictionInputMode {
        if (info == null) return PredictionInputMode.GENERAL

        val inputType = info.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        return when {
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT -> {
                PredictionInputMode.EMAIL
            }

            variation == InputType.TYPE_TEXT_VARIATION_URI ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> {
                PredictionInputMode.SEARCH
            }

            variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE ||
                variation == InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE -> {
                PredictionInputMode.MESSAGE
            }

            info.privateImeOptions?.contains("code", ignoreCase = true) == true -> {
                PredictionInputMode.CODE
            }

            else -> PredictionInputMode.GENERAL
        }
    }
}
