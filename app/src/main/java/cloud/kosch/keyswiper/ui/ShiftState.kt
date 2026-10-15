package cloud.kosch.keyswiper.ui

/**
 * The three explicit shift phases are independent of automatic sentence capitalization.
 * A second press while uppercase is displayed enables Caps Lock; the third disables it.
 */
internal enum class ShiftMode { LOWER, UPPER, LOCKED }

internal class ShiftState {
    var mode: ShiftMode = ShiftMode.LOWER
        private set
    private var automatic = false
    private var suppressAutomatic = false

    val locked: Boolean get() = mode == ShiftMode.LOCKED
    val shifted: Boolean get() = mode != ShiftMode.LOWER || (automatic && !suppressAutomatic)

    fun tap() {
        mode = when (mode) {
            ShiftMode.LOWER -> if (shifted) ShiftMode.LOCKED else ShiftMode.UPPER
            ShiftMode.UPPER -> ShiftMode.LOCKED
            ShiftMode.LOCKED -> ShiftMode.LOWER
        }
        suppressAutomatic = mode == ShiftMode.LOWER
    }

    fun setAutomatic(enabled: Boolean) {
        automatic = enabled
        if (!enabled) suppressAutomatic = false
    }

    fun consume(value: String) {
        if (!value.any(Char::isLetter)) return
        if (mode == ShiftMode.UPPER) mode = ShiftMode.LOWER
        // The service recalculates sentence capitalization after the text is committed.
        automatic = false
        suppressAutomatic = false
    }

    /** Entering another field should not silently release an explicit Caps Lock. */
    fun beginEditorSession() {
        if (mode == ShiftMode.UPPER) mode = ShiftMode.LOWER
        automatic = false
        suppressAutomatic = false
    }

    fun reset() {
        mode = ShiftMode.LOWER
        automatic = false
        suppressAutomatic = false
    }
}
