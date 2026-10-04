package cloud.kosch.keyswiper.input

/** One complete word/phrase and its separator, with the cursor after the space. */
data class WordCommitPlan(
    val deleteBefore: Int,
    val deleteAfter: Int,
    val deletedText: String,
    val insertedText: String,
    val word: String
) {
    companion object {
        fun create(
            value: String,
            before: String,
            after: String,
            selected: String = "",
            replacesCurrentToken: Boolean = false
        ): WordCommitPlan? {
            val word = value.trim()
            if (word.isEmpty()) return null
            // deleteSurroundingText excludes a selection: never also delete the
            // preceding word when the user has explicitly selected text.
            val token = if (replacesCurrentToken && selected.isEmpty()) {
                before.takeLastWhile { it.isLetterOrDigit() || it == '\'' || it == '-' }
            } else ""
            val left = before.dropLast(token.length)
            val previous = left.lastOrNull()
            val leading = if (previous != null && !previous.isWhitespace() &&
                !previous.isISOControl() && previous !in "([{«„“\"'") " " else ""
            // Reuse one existing ordinary separator. Newlines, tabs and
            // intentional additional indentation stay intact.
            val existingSpace = if (after.startsWith(" ")) " " else ""
            return WordCommitPlan(token.length, existingSpace.length,
                token + selected + existingSpace, leading + word + " ", word)
        }
    }
}
