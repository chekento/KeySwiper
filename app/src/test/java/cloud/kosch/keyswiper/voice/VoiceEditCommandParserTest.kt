package cloud.kosch.keyswiper.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceEditCommandParserTest {

    @Test
    fun parsesGermanAndEnglishEditingCommands() {
        assertEquals(
            VoiceEditCommand.DeleteLastWord,
            VoiceEditCommandParser.parse(
                "Lösche letztes Wort"
            )
        )

        assertEquals(
            VoiceEditCommand.NewLine,
            VoiceEditCommandParser.parse(
                "new line"
            )
        )

        assertEquals(
            VoiceEditCommand.SelectAll,
            VoiceEditCommandParser.parse(
                "alles auswählen"
            )
        )
    }

    @Test
    fun parsesReplacement() {
        val result =
            VoiceEditCommandParser.parse(
                "Ersetze Peter durch Markus"
            )

        assertTrue(
            result is VoiceEditCommand.Replace
        )

        result as VoiceEditCommand.Replace

        assertEquals(
            "Peter",
            result.oldText
        )
        assertEquals(
            "Markus",
            result.newText
        )
    }

    @Test
    fun parsesTranslationTarget() {
        assertEquals(
            VoiceEditCommand.TranslateSelection(
                "en"
            ),
            VoiceEditCommandParser.parse(
                "Übersetze ins Englische"
            )
        )

        assertEquals(
            VoiceEditCommand.TranslateSelection(
                "de"
            ),
            VoiceEditCommandParser.parse(
                "translate to german"
            )
        )
    }

    @Test
    fun unknownSpeechIsNotInsertedAsCommand() {
        assertEquals(
            null,
            VoiceEditCommandParser.parse(
                "das ist ein normaler diktierter Satz"
            )
        )
    }
}
