package cloud.kosch.keyswiper.clipboard

object ClipboardTextClassifier {

    fun category(
        text: String
    ): ClipboardCategory {
        val trimmed =
            text.trim()

        if (
            Regex(
                """(?i)\bhttps?://\S+|\bwww\.\S+"""
            ).containsMatchIn(trimmed)
        ) {
            return ClipboardCategory.LINK
        }

        if (
            Regex(
                """(?i)^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$"""
            ).matches(trimmed)
        ) {
            return ClipboardCategory.EMAIL
        }

        val digits =
            trimmed.count {
                it.isDigit()
            }

        if (
            digits >= 7 &&
            trimmed.all {
                it.isDigit() ||
                    it in " +()-./"
            }
        ) {
            return ClipboardCategory.PHONE
        }

        if (
            Regex(
                """(?i)\b\d{1,5}\s+.+\b(straße|str\.?|weg|allee|platz|street|road|avenue|ave\.?|lane|ln\.?)\b"""
            ).containsMatchIn(
                trimmed
            )
        ) {
            return ClipboardCategory.ADDRESS
        }

        if (
            trimmed.contains("{") ||
            trimmed.contains("}") ||
            trimmed.contains(";") ||
            trimmed.lines()
                .any {
                    it.startsWith("    ") ||
                        it.startsWith("\t")
                }
        ) {
            return ClipboardCategory.CODE
        }

        return ClipboardCategory.TEXT
    }
}
