package cloud.kosch.keyswiper.language

data class LanguageLane(
    val tag: String,
    val score: Float
)

object CodeSwitchLanguageResolver {

    fun resolve(
        contextText: String,
        detectedLanguages: List<String>,
        currentToken: String = "",
        maxLanes: Int = 3
    ): List<LanguageLane> {
        val scores = mutableMapOf<String, Float>()

        detectedLanguages
            .map { it.substringBefore('-').lowercase() }
            .distinct()
            .take(3)
            .forEachIndexed { index, tag ->
                if (LanguagePackRegistry.get(tag) != null) {
                    scores[tag] = maxOf(
                        scores[tag] ?: 0f,
                        listOf(1.0f, 0.72f, 0.50f)[index]
                    )
                }
            }

        val recentTokens = Regex("[\\p{L}\\p{N}'-]+")
            .findAll(contextText.takeLast(280))
            .map { it.value.lowercase() }
            .toList()
            .takeLast(10)

        recentTokens.forEachIndexed { index, token ->
            val recency = 0.12f + 0.28f *
                ((index + 1).toFloat() / recentTokens.size.coerceAtLeast(1))

            LanguagePackRegistry.languagesForWord(token).forEach { tag ->
                scores[tag] = (scores[tag] ?: 0f) + recency
            }

            distinctiveLanguage(token)?.let { tag ->
                scores[tag] = (scores[tag] ?: 0f) + 0.55f
            }
        }

        val partial = currentToken.lowercase()
        if (partial.length >= 2) {
            LanguagePackRegistry.all.forEach { pack ->
                if (pack.prefixMatches(partial, 1).isNotEmpty()) {
                    scores[pack.tag] = (scores[pack.tag] ?: 0f) + 0.32f
                }
            }
            distinctiveLanguage(partial)?.let { tag ->
                scores[tag] = (scores[tag] ?: 0f) + 0.70f
            }
        }

        // Tech vocabulary deliberately keeps German/English lanes alive together.
        val recentTech = recentTokens.any { token ->
            LanguagePackRegistry.all.any { token in it.technicalTerms }
        }
        if (recentTech) {
            scores["en"] = (scores["en"] ?: 0f) + 0.45f
            scores["de"] = (scores["de"] ?: 0f) + 0.18f
        }

        if (scores.isEmpty()) {
            scores["de"] = 0.60f
            scores["en"] = 0.55f
        }

        val ordered = scores.entries
            .sortedByDescending { it.value }
            .take(maxLanes.coerceIn(1, 5))

        val max = ordered.firstOrNull()?.value?.coerceAtLeast(0.01f) ?: 1f

        return ordered.map {
            LanguageLane(
                tag = it.key,
                score = (it.value / max).coerceIn(0.05f, 1f)
            )
        }
    }

    fun tokenLanguage(token: String): String? {
        val distinctive = distinctiveLanguage(token)
        if (distinctive != null) return distinctive

        val languages = LanguagePackRegistry.languagesForWord(token)
        return if (languages.size == 1) languages.first() else null
    }

    private fun distinctiveLanguage(token: String): String? {
        val lower = token.lowercase()

        return when {
            lower.any { it in "äöüß" } -> "de"
            lower.any { it in "ñ¿¡" } -> "es"
            lower.any { it in "œç" } -> "fr"
            lower.contains("à") || lower.contains("è") ||
                lower.contains("ê") || lower.contains("î") -> "fr"
            lower.contains("ì") || lower.contains("ò") ||
                lower.contains("ù") -> "it"
            else -> null
        }
    }
}
