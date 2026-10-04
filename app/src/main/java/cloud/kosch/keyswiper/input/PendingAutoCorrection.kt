package cloud.kosch.keyswiper.input

/** Backspace may undo only the immediately preceding correction at the same cursor context. */
data class PendingAutoCorrection(
    val original: String,
    val replacement: String,
    val expectedBefore: String,
    val expectedAfter: String,
    val timelineEntryId: Long
) {
    fun matches(before: String, after: String, selected: String): Boolean =
        selected.isEmpty() && before.takeLast(256) == expectedBefore && after.take(64) == expectedAfter
}
