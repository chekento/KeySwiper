package cloud.kosch.keyswiper.stylus

class StylusClickInterpreter(
    private val doubleClickWindowMs: Long = 280L
) {
    private var lastPrimaryPressMs: Long? = null

    /**
     * Returns true when this press completes a double click.
     * A caller should delay the single-click action by [doubleClickWindowMs].
     */
    fun registerPrimaryPress(
        eventTimeMs: Long
    ): Boolean {
        val previous = lastPrimaryPressMs

        if (
            previous != null &&
            eventTimeMs - previous in 40L..doubleClickWindowMs
        ) {
            lastPrimaryPressMs = null
            return true
        }

        lastPrimaryPressMs = eventTimeMs
        return false
    }

    fun consumePendingSingle(
        expectedPressMs: Long
    ): Boolean {
        if (lastPrimaryPressMs != expectedPressMs) {
            return false
        }

        lastPrimaryPressMs = null
        return true
    }

    fun clear() {
        lastPrimaryPressMs = null
    }
}
