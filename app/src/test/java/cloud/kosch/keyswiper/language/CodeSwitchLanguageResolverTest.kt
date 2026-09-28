package cloud.kosch.keyswiper.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSwitchLanguageResolverTest {

    @Test
    fun keepsGermanAndEnglishLanesAliveForMixedTechText() {
        val lanes = CodeSwitchLanguageResolver.resolve(
            contextText = "Ich habe den build deployed und teste ",
            detectedLanguages = listOf("de", "en"),
            currentToken = ""
        )

        val tags = lanes.map { it.tag }

        assertTrue("de" in tags)
        assertTrue("en" in tags)
    }

    @Test
    fun distinctiveGermanCharactersPromoteGerman() {
        val lanes = CodeSwitchLanguageResolver.resolve(
            contextText = "Das wäre schön",
            detectedLanguages = emptyList(),
            currentToken = "schö"
        )

        assertEquals("de", lanes.first().tag)
    }

    @Test
    fun predictionLocksToMostRecentInputLanguage() {
        val lanes =
            CodeSwitchLanguageResolver.resolveForPrediction(
                contextText = "Das ist eine gute Tastatur und ich ",
                detectedLanguages = listOf("en", "de"),
                currentToken = "mö",
                fallbackLanguage = "en"
            )

        assertEquals(1, lanes.size)
        assertEquals("de", lanes.first().tag)
    }

    @Test
    fun predictionCanSwitchLanguageFromRecentInput() {
        val lanes =
            CodeSwitchLanguageResolver.resolveForPrediction(
                contextText = "Das Projekt is a good keyboard and this ",
                detectedLanguages = listOf("de", "en"),
                currentToken = "wou",
                fallbackLanguage = "de"
            )

        assertEquals("en", lanes.first().tag)
    }

    @Test
    fun rejectsKnownForeignSuggestionForLockedLanguage() {
        assertTrue(
            CodeSwitchLanguageResolver.matchesLanguage(
                text = "keyboard suggestions",
                languageTag = "en"
            )
        )
        assertTrue(
            !CodeSwitchLanguageResolver.matchesLanguage(
                text = "tastatur vorschläge",
                languageTag = "en"
            )
        )
    }

    @Test
    fun registryRecognizesSharedTechnicalVocabulary() {
        val languages = LanguagePackRegistry.languagesForWord("android")

        assertTrue("de" in languages)
        assertTrue("en" in languages)
    }
}
