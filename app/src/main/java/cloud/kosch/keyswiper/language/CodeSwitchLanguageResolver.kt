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
            .map(::normalizeTag)
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

        val recentTokens = lexicalTokens(contextText, 10)

        recentTokens.forEachIndexed { index, token ->
            val recency =
                0.12f +
                    0.28f *
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

        // Swipe decoding intentionally keeps technical code-switching broad.
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

        val ordered =
            scores.entries
                .sortedByDescending { it.value }
                .take(maxLanes.coerceIn(1, 5))

        val max =
            ordered.firstOrNull()
                ?.value
                ?.coerceAtLeast(0.01f)
                ?: 1f

        return ordered.map {
            LanguageLane(
                tag = it.key,
                score = (it.value / max).coerceIn(0.05f, 1f)
            )
        }
    }

    fun resolveForPrediction(
        contextText: String,
        detectedLanguages: List<String>,
        currentToken: String = "",
        fallbackLanguage: String? = null
    ): List<LanguageLane> {
        val primary =
            primaryInputLanguage(
                contextText,
                detectedLanguages,
                currentToken,
                fallbackLanguage
            )

        return if (primary != null) {
            listOf(LanguageLane(primary, 1f))
        } else {
            resolve(
                contextText,
                detectedLanguages,
                currentToken,
                maxLanes = 1
            )
        }
    }

    fun primaryInputLanguage(
        contextText: String,
        detectedLanguages: List<String>,
        currentToken: String = "",
        fallbackLanguage: String? = null
    ): String? {
        tokenLanguage(currentToken)?.let { return it }

        lexicalTokens(contextText, 8)
            .asReversed()
            .asSequence()
            .mapNotNull(::tokenLanguage)
            .firstOrNull()
            ?.let { return it }

        detectedLanguages
            .asSequence()
            .map(::normalizeTag)
            .firstOrNull { LanguagePackRegistry.get(it) != null }
            ?.let { return it }

        return fallbackLanguage
            ?.let(::normalizeTag)
            ?.takeIf { LanguagePackRegistry.get(it) != null }
    }

    fun matchesLanguage(
        text: String,
        languageTag: String
    ): Boolean {
        val wanted = normalizeTag(languageTag)
        var matchingVotes = 0
        var foreignVotes = 0

        lexicalTokens(text, 12).forEach { token ->
            val languages = LanguagePackRegistry.languagesForWord(token)
            if (languages.isEmpty()) return@forEach

            if (wanted in languages) {
                matchingVotes++
            } else {
                foreignVotes++
            }
        }

        return when {
            matchingVotes > 0 -> matchingVotes >= foreignVotes
            foreignVotes > 0 -> false
            else -> true
        }
    }

    fun tokenLanguage(token: String): String? {
        if (token.isBlank()) return null
        distinctiveLanguage(token)?.let { return it }

        val languages = LanguagePackRegistry.languagesForWord(token)
        return if (languages.size == 1) languages.first() else null
    }

    private fun lexicalTokens(
        text: String,
        limit: Int
    ): List<String> =
        Regex("[\\p{L}\\p{N}'-]+")
            .findAll(text.takeLast(420))
            .map { it.value.lowercase() }
            .toList()
            .takeLast(limit)

    private fun normalizeTag(tag: String): String =
        tag.substringBefore('-').lowercase()

    private fun distinctiveLanguage(token: String): String? {
        val lower = token.lowercase()

        return when {
            lower.any { it in "äöüß" } -> "de"
            lower.any { it in "ñ¿¡" } -> "es"
            lower.any { it in "œç" } -> "fr"
            lower.contains("à") ||
                lower.contains("è") ||
                lower.contains("ê") ||
                lower.contains("î") -> "fr"
            lower.contains("ì") ||
                lower.contains("ò") ||
                lower.contains("ù") -> "it"
            else -> null
        }
    }
}
