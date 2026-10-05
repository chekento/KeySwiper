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

    fun candidates(token: String, contextWords: List<String>, language: String, limit: Int = 3,
        beforeToken: String? = null): List<Candidate> {
        if (token.length !in 2..48 || token.any { !it.isLetter() } || limit <= 0) return emptyList()
        val lower = token.lowercase()
        if (token.drop(1).any { it.isUpperCase() }) return emptyList() // Acronyms and identifiers.
        val pack = LanguagePackRegistry.get(language) ?: return emptyList()
        val prefix = beforeToken ?: contextWords.joinToString(" ").let { if (it.isEmpty()) it else "$it " }
        val sentenceStart = beforeToken != null && Orthography.sentenceStart(prefix)
        val personalKnown = vocabulary?.contains(lower) == true
        val known = lower in knownWords || personalKnown
        val personal = vocabulary?.frequentWords(listOf(LanguageLane(language, 1f)), 120).orEmpty().map { it.first }
        fun spelling(word: String): String {
            val stored = personal.firstOrNull { it.equals(word, true) }
                ?.takeIf { word.lowercase() !in knownWords || it.drop(1).any(Char::isUpperCase) || vocabulary?.isExplicit(word) == true }
            val display = Orthography.display(stored ?: word, language, prefix, sentenceStart, token, stored != null)
            return if (beforeToken == null && token.first().isUpperCase()) matchCase(token, display) else display
        }
        val next = pack.commonNext[contextWords.lastOrNull()?.lowercase()].orEmpty()
        val keys = KeyboardLayoutProfiles.forLanguage(language).letterRows.flatMapIndexed { row, letters ->
            letters.mapIndexed { column, char -> char to (column + row * 0.35f to row.toFloat()) }
        }.toMap()
        val threshold = if (lower.length <= 5) 1.05f else 1.65f
        data class Match(val word: String, val cost: Float, val rank: Float)
        val ranked = (lexicons[language].orEmpty() + personal.map { it.lowercase() }).asSequence().distinct()
            .filter { it != lower && it.length >= 2 && abs(it.length - lower.length) <= 2 && it.all(Char::isLetter) }
            .mapNotNull { word ->
                val transliterated = if (language == "de") word.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss") else word
                val accentMatch = fold(word) == fold(lower) || transliterated == lower
                // Valid words and personal spellings still offer umlaut alternatives,
                // but must not be replaced automatically (schon/schön, mochte/möchte).
                if ((known || lower.length < 3) && !accentMatch) return@mapNotNull null
                val cost = if (transliterated == lower) 0.3f else if (fold(word) == fold(lower)) 0.25f else distance(lower, word, keys)
                if (cost > threshold) null else {
                    val contextIndex = next.indexOf(word)
                    val directBoost = if (contextIndex >= 0) (0.32f - contextIndex * 0.035f).coerceAtLeast(0.10f) else 0f
                    val contextBoost = maxOf(directBoost, (ContinuationCorpus.contextMatch(language, contextWords, word) * 0.18f).coerceAtMost(0.55f))
                    val learnedBoost = memory.boost(contextWords, word).coerceIn(0, 160) / 1000f
                    Match(word, cost, cost - contextBoost - learnedBoost)
                }
            }.sortedWith(compareBy<Match> { it.rank }.thenBy { it.word }).toList()
        val result = mutableListOf<Candidate>()
        val canonical = spelling(lower)
        if (canonical != token && vocabulary?.isExplicit(lower) != true && lower in lexicons[language].orEmpty()) {
            val safe = sentenceStart || lower in Orthography.germanNouns || language == "en" && lower == "i"
            result += Candidate(canonical, 0.1f, if (safe) 0.98f else 0.88f, safe)
        }
        val best = ranked.firstOrNull()
        val gap = if (best == null) 0f else ranked.getOrNull(1)?.let { it.rank - best.rank } ?: 1f
        result += ranked.take(limit).mapIndexed { index, match ->
            val confidence = (0.97f - match.cost * 0.04f - if (index == 0 && gap < 0.35f) 0.18f else index * 0.13f).coerceIn(0.4f, 0.99f)
            val safeCase = token.first().isLowerCase() || match.word in capitalizedFunctionWords
            val possibleGermanStem = language == "de" && lower.length >= 4 &&
                listOf("e", "en", "n").any { lower + it in lexicons[language].orEmpty() }
            Candidate(spelling(match.word), match.cost, confidence,
                !known && index == 0 && safeCase && !possibleGermanStem && match.cost <= 0.75f && gap >= 0.35f && confidence >= 0.91f)
        }
        return result.distinctBy { it.word }.take(limit)
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
