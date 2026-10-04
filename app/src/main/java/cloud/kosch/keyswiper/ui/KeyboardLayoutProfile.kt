package cloud.kosch.keyswiper.ui

data class KeyboardLayoutProfile(
    val id: String,
    val label: String,
    val languageTag: String,
    val letterRows: List<String>,
    val accentKeys: List<Char>
)

object KeyboardLayoutProfiles {
    const val SHIFT = "__SHIFT__"
    const val BACKSPACE = "__BACKSPACE__"
    data class Slot(val token: String, val weight: Float = 1f)

    fun slots(profile: KeyboardLayoutProfile, symbols: Boolean = false, page: Int = 0): List<List<Slot>> {
        val rows = if (symbols) { if (page == 0) symbolRows else extraSymbolRows } else profile.letterRows
        return rows.mapIndexed { index, row ->
            buildList {
                if (!symbols && index == rows.lastIndex) add(Slot(SHIFT, 1.55f))
                addAll(row.map { Slot(it.toString()) })
                if (index == 0) add(Slot(BACKSPACE, 1.25f))
            }
        }
    }
    val all = listOf(
        KeyboardLayoutProfile(
            id = "de-qwertz",
            label = "Deutsch · QWERTZ",
            languageTag = "de",
            letterRows = listOf("qwertzuiop", "asdfghjkl", "yxcvbnm"),
            accentKeys = listOf('ä', 'ö', 'ü', 'ß')
        ),
        KeyboardLayoutProfile(
            id = "en-qwerty",
            label = "English · QWERTY",
            languageTag = "en",
            letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
            accentKeys = emptyList()
        ),
        KeyboardLayoutProfile(
            id = "fr-azerty",
            label = "Français · AZERTY",
            languageTag = "fr",
            letterRows = listOf("azertyuiop", "qsdfghjklm", "wxcvbn"),
            accentKeys = listOf('é', 'è', 'à', 'ç', 'ù')
        ),
        KeyboardLayoutProfile(
            id = "it-qwerty",
            label = "Italiano · QWERTY",
            languageTag = "it",
            letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
            accentKeys = listOf('à', 'è', 'é', 'ì', 'ò', 'ù')
        ),
        KeyboardLayoutProfile(
            id = "es-qwerty",
            label = "Español · QWERTY",
            languageTag = "es",
            letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
            accentKeys = listOf('ñ', 'á', 'é', 'í', 'ó', 'ú')
        )
    )

    fun byId(id: String?): KeyboardLayoutProfile =
        all.firstOrNull { it.id == id } ?: all.first()

    fun forLanguage(language: String?): KeyboardLayoutProfile {
        val tag = language
            ?.substringBefore('-')
            ?.lowercase()
        return all.firstOrNull {
            it.languageTag == tag
        } ?: all[1]
    }

    val symbolRows: List<String> = listOf(
        "1234567890",
        "@#€_&-+()/",
        "*\"':;!?.,"
    )

    val extraSymbolRows: List<String> = listOf(
        "[]{}<>=%^~",
        "|\\°×÷±§…",
        "•←→↑↓€$£"
    )
}
