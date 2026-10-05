package cloud.kosch.keyswiper.ui

import cloud.kosch.keyswiper.input.KeyboardGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutProfileTest {

    @Test
    fun germanLayoutIsQwertz() {
        val german =
            KeyboardLayoutProfiles.byId(
                "de-qwertz"
            )

        assertEquals(
            "qwertzuiop",
            german.letterRows.first()
        )
        assertEquals(
            listOf('ä', 'ö', 'ü', 'ß'),
            german.accentKeys
        )
    }

    @Test
    fun qwertzGeometryMovesZToTopRow() {
        val germanZ =
            KeyboardGeometry.center(
                'z',
                "de-qwertz"
            ) ?: error("German z missing")

        val englishZ =
            KeyboardGeometry.center(
                'z',
                "en-qwerty"
            ) ?: error("English z missing")

        assertTrue(
            germanZ.second <
                englishZ.second
        )
    }

    @Test
    fun frenchLayoutIsAzerty() {
        assertEquals(
            "azertyuiop",
            KeyboardLayoutProfiles
                .byId("fr-azerty")
                .letterRows
                .first()
        )
    }

    @Test
    fun symbolPageHasNumbersAndPunctuation() {
        val symbols =
            KeyboardLayoutProfiles
                .symbolRows
                .joinToString("")

        assertTrue(
            symbols.contains("1234567890")
        )
        assertTrue(
            symbols.contains("?")
        )
        assertTrue(
            symbols.contains("@")
        )
    }
    @Test
    fun fullWidthGeometryMatchesRenderedRows() {
        val topLeft =
            KeyboardGeometry.center(
                'q',
                "en-qwerty"
            ) ?: error(
                "q missing"
            )

        val homeLeft =
            KeyboardGeometry.center(
                'a',
                "en-qwerty"
            ) ?: error(
                "a missing"
            )

        val bottomLeft =
            KeyboardGeometry.center(
                'z',
                "en-qwerty"
            ) ?: error(
                "z missing"
            )

        assertTrue(
            topLeft.first <
                homeLeft.first
        )

        assertTrue(
            homeLeft.first <
                bottomLeft.first
        )
    }
    @Test fun punctuationIsOneKeyImmediatelyAfterM() {
        for (profile in KeyboardLayoutProfiles.all) {
            val rows = KeyboardLayoutProfiles.slots(profile)
            val row = rows.first { it.any { slot -> slot.token == "m" } }
            assertEquals(".", row[row.indexOfFirst { it.token == "m" } + 1].token)
            assertEquals(1, rows.flatten().count { it.token == "." })
        }
        val variants = KeyAlternatives.forKey('.', "de-qwertz", false, false)
        assertTrue(variants.containsAll(listOf(",", "?", "!", ":", ";", "…")))
    }
}

