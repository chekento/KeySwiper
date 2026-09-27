package cloud.kosch.keyswiper.handwriting

object HandwritingCommitFormatter {

    fun formatRecognition(value: String): String {
        val cleaned = value.trim()
        if (cleaned.isBlank()) return ""

        return if (
            cleaned.lastOrNull()?.isLetterOrDigit() == true
        ) {
            "$cleaned "
        } else {
            "$cleaned "
        }
    }
}
