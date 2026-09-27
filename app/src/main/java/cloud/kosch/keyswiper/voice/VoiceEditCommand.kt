package cloud.kosch.keyswiper.voice

sealed interface VoiceEditCommand {
    data object DeleteLastWord : VoiceEditCommand
    data object DeleteLastSentence : VoiceEditCommand
    data object NewLine : VoiceEditCommand
    data object SelectAll : VoiceEditCommand
    data object Copy : VoiceEditCommand
    data object Cut : VoiceEditCommand
    data object Paste : VoiceEditCommand
    data object UndoLastSwipe : VoiceEditCommand

    data class Replace(
        val oldText: String,
        val newText: String
    ) : VoiceEditCommand

    data class TranslateSelection(
        val targetLanguageTag: String
    ) : VoiceEditCommand
}

object VoiceEditCommandParser {

    private val languageNames = mapOf(
        "deutsch" to "de",
        "deutsche" to "de",
        "german" to "de",
        "englisch" to "en",
        "englische" to "en",
        "english" to "en",
        "italienisch" to "it",
        "italienische" to "it",
        "italian" to "it",
        "französisch" to "fr",
        "französische" to "fr",
        "franzoesisch" to "fr",
        "franzoesische" to "fr",
        "french" to "fr",
        "spanisch" to "es",
        "spanische" to "es",
        "spanish" to "es",
        "portugiesisch" to "pt",
        "portugiesische" to "pt",
        "portuguese" to "pt",
        "niederländisch" to "nl",
        "niederländische" to "nl",
        "niederlaendisch" to "nl",
        "niederlaendische" to "nl",
        "dutch" to "nl",
        "polnisch" to "pl",
        "polnische" to "pl",
        "polish" to "pl",
        "türkisch" to "tr",
        "türkische" to "tr",
        "tuerkisch" to "tr",
        "tuerkische" to "tr",
        "turkish" to "tr",
        "ukrainisch" to "uk",
        "ukrainische" to "uk",
        "ukrainian" to "uk",
        "arabisch" to "ar",
        "arabische" to "ar",
        "arabic" to "ar",
        "hindi" to "hi",
        "japanisch" to "ja",
        "japanische" to "ja",
        "japanese" to "ja",
        "koreanisch" to "ko",
        "koreanische" to "ko",
        "korean" to "ko",
        "chinesisch" to "zh",
        "chinesische" to "zh",
        "chinese" to "zh"
    )

    fun parse(
        spoken: String
    ): VoiceEditCommand? {
        val cleaned =
            spoken
                .trim()
                .replace(
                    Regex("\\s+"),
                    " "
                )

        if (cleaned.isBlank()) return null

        val normalized =
            cleaned.lowercase()

        when (normalized) {
            "letztes wort löschen",
            "lösche letztes wort",
            "letztes wort entfernen",
            "delete last word",
            "remove last word" ->
                return VoiceEditCommand.DeleteLastWord

            "letzten satz löschen",
            "lösche letzten satz",
            "letzten satz entfernen",
            "delete last sentence",
            "remove last sentence" ->
                return VoiceEditCommand.DeleteLastSentence

            "neue zeile",
            "zeilenumbruch",
            "new line",
            "line break" ->
                return VoiceEditCommand.NewLine

            "alles auswählen",
            "alles markieren",
            "select all" ->
                return VoiceEditCommand.SelectAll

            "kopieren",
            "copy" ->
                return VoiceEditCommand.Copy

            "ausschneiden",
            "cut" ->
                return VoiceEditCommand.Cut

            "einfügen",
            "einfuegen",
            "paste" ->
                return VoiceEditCommand.Paste

            "letzten swipe rückgängig",
            "letzten swipe rueckgaengig",
            "swipe rückgängig",
            "swipe rueckgaengig",
            "undo last swipe",
            "undo swipe" ->
                return VoiceEditCommand.UndoLastSwipe
        }

        parseReplacement(cleaned)
            ?.let {
                return it
            }

        parseTranslation(normalized)
            ?.let {
                return it
            }

        return null
    }

    private fun parseReplacement(
        spoken: String
    ): VoiceEditCommand.Replace? {
        val patterns = listOf(
            Regex(
                """(?i)^ersetze\s+(.+?)\s+durch\s+(.+)$"""
            ),
            Regex(
                """(?i)^replace\s+(.+?)\s+with\s+(.+)$"""
            )
        )

        for (pattern in patterns) {
            val match =
                pattern.matchEntire(
                    spoken
                ) ?: continue

            val oldText =
                match.groupValues[1]
                    .trim()
                    .trim('"', '\'')

            val newText =
                match.groupValues[2]
                    .trim()
                    .trim('"', '\'')

            if (
                oldText.isNotBlank() &&
                newText.isNotBlank()
            ) {
                return VoiceEditCommand.Replace(
                    oldText = oldText,
                    newText = newText
                )
            }
        }

        return null
    }

    private fun parseTranslation(
        normalized: String
    ): VoiceEditCommand.TranslateSelection? {
        val prefixes = listOf(
            "übersetze ins ",
            "uebersetze ins ",
            "übersetze auf ",
            "uebersetze auf ",
            "translate to "
        )

        val language =
            prefixes
                .firstNotNullOfOrNull { prefix ->
                    normalized
                        .takeIf {
                            it.startsWith(
                                prefix
                            )
                        }
                        ?.removePrefix(prefix)
                        ?.trim()
                }
                ?: return null

        val tag =
            languageNames[language]
                ?: language
                    .takeIf {
                        it.length in 2..3 &&
                            it.all(Char::isLetter)
                    }
                    ?.lowercase()
                ?: return null

        return VoiceEditCommand.TranslateSelection(
            targetLanguageTag = tag
        )
    }
}
