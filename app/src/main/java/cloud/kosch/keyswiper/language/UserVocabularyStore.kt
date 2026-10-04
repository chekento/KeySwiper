package cloud.kosch.keyswiper.language

import android.content.Context

class UserVocabularyStore(context: Context) : UserVocabularyLookup {

    private val prefs =
        context.getSharedPreferences("keyswiper_user_vocabulary", Context.MODE_PRIVATE)

    override fun contains(word: String): Boolean = prefs.getInt("c|${normalize(word)}", 0) > 0

    fun observeWord(
        rawWord: String,
        languageHints: List<String>
    ) {
        val word = normalize(rawWord)
        if (word.length < 2 || word.length > 48) return
        if (word.any { it.isDigit() } && word.any { it.isLetter() }) return

        val countKey = "c|$word"
        val next = (prefs.getInt(countKey, 0) + 1).coerceAtMost(10_000)
        val language = bestLanguage(word, languageHints)

        prefs.edit()
            .putInt(countKey, next)
            .putString("l|$word", language)
            .apply()

        pruneIfNeeded()
    }

    fun rememberWord(
        rawWord: String,
        languageTag: String? = null
    ) {
        val word = normalize(rawWord)
        if (word.length < 2 || word.length > 48) return

        val language = languageTag
            ?.substringBefore('-')
            ?.lowercase()
            ?.takeIf { LanguagePackRegistry.get(it) != null }
            ?: bestLanguage(word, emptyList())

        prefs.edit()
            .putInt("c|$word", maxOf(20, prefs.getInt("c|$word", 0)))
            .putBoolean("p|$word", true)
            .putString("l|$word", language)
            .apply()
    }

    fun forgetWord(rawWord: String) {
        val word = normalize(rawWord)
        prefs.edit()
            .remove("c|$word")
            .remove("p|$word")
            .remove("l|$word")
            .apply()
    }

    override fun prefixMatches(
        prefix: String,
        lanes: List<LanguageLane>,
        limit: Int
    ): List<Pair<String, Int>> {
        val normalized = normalize(prefix)
        if (normalized.isBlank()) return emptyList()

        val laneScores = lanes.associate { it.tag to it.score }

        return prefs.all.keys.asSequence()
            .filter { it.startsWith("c|") }
            .map { it.removePrefix("c|") }
            .filter { it.startsWith(normalized) && it != normalized }
            .map { word ->
                val count = prefs.getInt("c|$word", 0)
                val pinned = prefs.getBoolean("p|$word", false)
                val language = prefs.getString("l|$word", null)
                val lane = laneScores[language] ?: 0.25f
                val score =
                    count * 4 +
                        (if (pinned) 200 else 0) +
                        (lane * 60f).toInt()
                word to score
            }
            .sortedByDescending { it.second }
            .take(limit)
            .toList()
    }

    override fun frequentWords(
        lanes: List<LanguageLane>,
        limit: Int
    ): List<Pair<String, Int>> {
        val laneScores = lanes.associate { it.tag to it.score }

        return prefs.all.keys.asSequence()
            .filter { it.startsWith("c|") }
            .map { it.removePrefix("c|") }
            .map { word ->
                val count = prefs.getInt("c|$word", 0)
                val pinned = prefs.getBoolean("p|$word", false)
                val language = prefs.getString("l|$word", null)
                val lane = laneScores[language] ?: 0.20f
                word to (
                    count * 3 +
                        (if (pinned) 180 else 0) +
                        (lane * 50f).toInt()
                    )
            }
            .sortedByDescending { it.second }
            .take(limit)
            .toList()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private fun bestLanguage(
        word: String,
        hints: List<String>
    ): String {
        val known = LanguagePackRegistry.languagesForWord(word)
        val hinted = hints
            .map { it.substringBefore('-').lowercase() }
            .firstOrNull { it in known }

        return hinted
            ?: known.firstOrNull()
            ?: CodeSwitchLanguageResolver.tokenLanguage(word)
            ?: hints.firstOrNull()
                ?.substringBefore('-')
                ?.lowercase()
                ?.takeIf { LanguagePackRegistry.get(it) != null }
            ?: "und"
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .trim { !it.isLetter() && it != '\'' && it != '-' }

    private fun pruneIfNeeded() {
        val words = prefs.all.keys.count { it.startsWith("c|") }
        if (words <= 2500) return

        val removable = prefs.all.keys.asSequence()
            .filter { it.startsWith("c|") }
            .map { it.removePrefix("c|") }
            .filter { !prefs.getBoolean("p|$it", false) }
            .map { it to prefs.getInt("c|$it", 0) }
            .sortedBy { it.second }
            .take(300)
            .toList()

        val editor = prefs.edit()
        removable.forEach { (word, _) ->
            editor.remove("c|$word")
            editor.remove("l|$word")
        }
        editor.apply()
    }
}

