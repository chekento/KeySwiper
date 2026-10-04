package cloud.kosch.keyswiper.prediction

import cloud.kosch.keyswiper.language.LanguageLane
import cloud.kosch.keyswiper.language.LanguagePackRegistry
import cloud.kosch.keyswiper.language.UserVocabularyLookup
import cloud.kosch.keyswiper.ui.KeyboardLayoutProfiles
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.min

/** Local, bounded typo ranking. Uncertain corrections are choices, never silent replacements. */
class WordCorrectionEngine(
    private val memory: PredictionMemory,
    private val vocabulary: UserVocabularyLookup? = null
) {
    data class Candidate(val word: String, val cost: Float, val confidence: Float, val automatic: Boolean)
    private val lexicons = LanguagePackRegistry.all.associate { pack ->
        pack.tag to (pack.words + pack.technicalTerms + pack.commonNext.keys + pack.commonNext.values.flatten())
    }
    private val knownWords = lexicons.values.flatten().toSet()
    private val foldedLetters = knownWords.flatMap { it.toList() }.distinct().associateWith { fold(it.toString()) }

    fun candidates(token: String, contextWords: List<String>, language: String, limit: Int = 3): List<Candidate> {
        if (token.length !in 3..32 || token.any { !it.isLetter() }) return emptyList()
        val lower = token.lowercase()
        if (lower in knownWords || vocabulary?.contains(lower) == true) return emptyList()
        if (token.drop(1).any { it.isUpperCase() }) return emptyList() // Acronyms and identifiers.
        val pack = LanguagePackRegistry.get(language) ?: return emptyList()
        val next = pack.commonNext[contextWords.lastOrNull()?.lowercase()].orEmpty()
        val personal = vocabulary?.frequentWords(listOf(LanguageLane(language, 1f)), 120).orEmpty().map { it.first }
        val keys = KeyboardLayoutProfiles.forLanguage(language).letterRows.flatMapIndexed { row, letters ->
            letters.mapIndexed { column, char -> char to (column + row * 0.35f to row.toFloat()) }
        }.toMap()
        val threshold = if (lower.length <= 5) 1.05f else 1.65f
        data class Match(val word: String, val cost: Float, val rank: Float)
        val ranked = (lexicons[language].orEmpty() + personal).asSequence()
            .filter { it.length >= 3 && abs(it.length - lower.length) <= 2 && it.all(Char::isLetter) }
            .mapNotNull { word ->
                val transliterated = if (language == "de") word.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss") else word
                val cost = if (transliterated == lower) 0.3f else distance(lower, word, keys)
                if (cost > threshold) null else {
                    val contextIndex = next.indexOf(word)
                    val contextBoost = if (contextIndex >= 0) (0.32f - contextIndex * 0.035f).coerceAtLeast(0.10f) else 0f
                    val learnedBoost = memory.boost(contextWords, word).coerceIn(0, 160) / 1000f
                    Match(word, cost, cost - contextBoost - learnedBoost)
                }
            }
            .sortedWith(compareBy<Match> { it.rank }.thenBy { it.word })
            .toList()
        val best = ranked.firstOrNull() ?: return emptyList()
        val gap = ranked.getOrNull(1)?.let { it.rank - best.rank } ?: 1f
        return ranked.take(limit.coerceAtLeast(0)).mapIndexed { index, match ->
            val confidence = (0.97f - match.cost * 0.04f - if (index == 0 && gap < 0.35f) 0.18f else index * 0.13f).coerceIn(0.4f, 0.99f)
            // Capitalized unknown words may be names. Only unambiguous function-word typos
            // can be corrected automatically at the beginning of a sentence.
            val safeCase = token.first().isLowerCase() || match.word in capitalizedFunctionWords
            Candidate(matchCase(token, match.word), match.cost, confidence,
                index == 0 && safeCase && match.cost <= 1.05f && gap >= 0.35f && confidence >= 0.91f)
        }
    }

    private fun distance(a: String, b: String, keys: Map<Char, Pair<Float, Float>>): Float {
        val costs = Array(a.length + 1) { FloatArray(b.length + 1) }
        for (i in 0..a.length) costs[i][0] = i.toFloat()
        for (j in 0..b.length) costs[0][j] = j.toFloat()
        for (i in 1..a.length) for (j in 1..b.length) {
            val ac = a[i - 1]; val bc = b[j - 1]
            val ap = keys[ac]; val bp = keys[bc]
            val substitution = when {
                ac == bc -> 0f
                (foldedLetters[ac] ?: ac.toString()) == (foldedLetters[bc] ?: bc.toString()) -> 0.25f
                ap != null && bp != null && abs(ap.first - bp.first) <= 1.2f && abs(ap.second - bp.second) <= 1f -> 0.7f
                else -> 1f
            }
            costs[i][j] = minOf(costs[i - 1][j] + 1f, costs[i][j - 1] + 1f, costs[i - 1][j - 1] + substitution)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                costs[i][j] = min(costs[i][j], costs[i - 2][j - 2] + 0.65f)
            }
        }
        return costs[a.length][b.length]
    }

    companion object {
        private val capitalizedFunctionWords = setOf("das", "dass", "der", "die", "und", "ich", "wir", "bitte", "the", "and", "please", "you")
        fun fold(word: String): String = Normalizer.normalize(word.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace("ß", "ss")
        fun matchCase(typed: String, candidate: String): String = when {
            typed.length > 1 && typed.all { it.isUpperCase() } -> candidate.uppercase()
            typed.firstOrNull()?.isUpperCase() == true -> candidate.replaceFirstChar { it.uppercase() }
            else -> candidate
        }
    }
}
