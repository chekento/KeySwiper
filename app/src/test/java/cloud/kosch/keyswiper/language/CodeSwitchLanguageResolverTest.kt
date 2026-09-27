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
    fun registryRecognizesSharedTechnicalVocabulary() {
        val languages = LanguagePackRegistry.languagesForWord("android")

        assertTrue("de" in languages)
        assertTrue("en" in languages)
    }
}
