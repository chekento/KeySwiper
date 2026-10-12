package cloud.kosch.keyswiper.ui

import android.text.InputType

data class NumericKeypadProfile(
    val leftBottom: String? = null,
    val rightBottom: String? = null,
    val upperExtras: List<String> = emptyList()
) {
    val rows: List<List<KeyboardLayoutProfiles.Slot>>
        get() {
            fun slot(value: String?) = KeyboardLayoutProfiles.Slot(value ?: EMPTY)
            return listOf(
                listOf(slot("1"), slot("2"), slot("3"), slot(KeyboardLayoutProfiles.BACKSPACE)),
                listOf(slot("4"), slot("5"), slot("6"), slot(upperExtras.getOrNull(0))),
                listOf(slot("7"), slot("8"), slot("9"), slot(upperExtras.getOrNull(1))),
                listOf(slot(leftBottom), slot("0"), slot(rightBottom), slot(ENTER))
            )
        }

    companion object {
        const val EMPTY = "__EMPTY__"
        const val ENTER = "__ENTER__"

        fun fromInputType(inputType: Int): NumericKeypadProfile? = when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> {
                val password = inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                NumericKeypadProfile(
                    leftBottom = if (!password && inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0) "-" else null,
                    rightBottom = if (!password && inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0) "." else null
                )
            }
            InputType.TYPE_CLASS_PHONE -> NumericKeypadProfile("+", "#", listOf("*"))
            InputType.TYPE_CLASS_DATETIME -> when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_DATETIME_VARIATION_TIME -> NumericKeypadProfile(":")
                InputType.TYPE_DATETIME_VARIATION_DATE -> NumericKeypadProfile("-", "/", listOf("."))
                else -> NumericKeypadProfile("/", ":", listOf("-", "."))
            }
            else -> null
        }
    }
}
