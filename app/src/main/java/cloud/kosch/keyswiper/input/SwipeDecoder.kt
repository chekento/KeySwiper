package cloud.kosch.keyswiper.input

import cloud.kosch.keyswiper.language.LanguagePackRegistry
import java.text.Normalizer
import kotlin.math.abs

class SwipeDecoder {

    private data class Lexeme(
        val word: String,
        val swipeForm: String,
        val languages: Set<String>
    )

    private data class CandidateScore(
        val word: String,
        val score: Float
    )

    private val lexicon: List<Lexeme> =
        LanguagePackRegistry.all
            .flatMap { pack ->
                (pack.words + pack.technicalTerms + pack.commonNext.keys + pack.commonNext.values.flatten())
                    .asSequence()
                    .filter { it.length in 2..28 }
                    .map { word ->
                        Lexeme(
                            word = word,
                            swipeForm = swipeForm(word),
                            languages = setOf(pack.tag)
                        )
                    }
                    .filter { item ->
                        item.swipeForm.length >= 2 &&
                            item.swipeForm.all { it in 'a'..'z' }
                    }
                    .toList()
            }
            .groupBy { it.word }
            .map { (word, items) ->
                Lexeme(
                    word = word,
                    swipeForm = items.first().swipeForm,
                    languages = items.flatMap { it.languages }.toSet()
                )
            }

    fun signature(trace: SwipeTrace): String =
        trace.normalizedKeys().joinToString("")

    fun decode(
        trace: SwipeTrace,
        context: String = "",
        preferredLanguages: List<String> = emptyList(),
        personalizationBoost: (
            signature: String,
            previousWord: String,
            candidate: String
        ) -> Int = { _, _, _ -> 0 },
        motorOffset: (Char) -> KeyOffset = { KeyOffset() },
        additionalWords: List<String> = emptyList()
    ): List<String> {
        val compact = trace.normalizedKeys()
        if (compact.isEmpty()) return emptyList()

        val sentenceStart = isSentenceStart(context)
        if (compact.size == 1) {
            return listOf(
                applyCase(
                    compact.first().toString(),
                    sentenceStart
                )
            )
        }

        val sig = compact.joinToString("")
        val previousWord = previousWord(context)
        val first = compact.first()
        val last = compact.last()
        val hasGeometry = trace.points.size >= 3

        val personalLexemes = additionalWords.asSequence()
            .map { it.trim().lowercase() }
            .filter { it.length in 2..48 }
            .distinct()
            .map { Lexeme(it, swipeForm(it), LanguagePackRegistry.languagesForWord(it)) }
            .filter { it.swipeForm.length >= 2 && it.swipeForm.all { c -> c in 'a'..'z' } }
            .toList()
        val candidates = (lexicon + personalLexemes).distinctBy { it.word }
        val prepared = if (hasGeometry) SwipeGeometryScorer.prepare(trace) else null
        val endpoints = if (hasGeometry) candidates.associateWith {
            SwipeGeometryScorer.endpointDistance(trace, it.swipeForm, motorOffset)
        } else emptyMap()
        val nearestEndpoints = endpoints.values.minOrNull() ?: 0f

        val ranked = candidates.asSequence()
            .filter {
                // Crossing keys adds letters to the sampled sequence. Its length
                // must not eliminate the correct geometric word before scoring.
                if (hasGeometry) endpoints.getValue(it) <= nearestEndpoints + 0.40f
                else abs(it.swipeForm.length - sig.length) <= maxOf(6, sig.length / 2 + 2)
            }
            .map { item ->
                val form = item.swipeForm
                val compactForm = form.fold(StringBuilder()) { result, c ->
                    if (result.lastOrNull() != c) result.append(c)
                    result
                }.toString()
                val distance = levenshtein(sig, compactForm)
                val languageBoost = languageBoost(item.languages, preferredLanguages)
                val contextBoost = contextualBoost(previousWord, item.word, item.languages)
                val learnedBoost = personalizationBoost(sig, previousWord, item.word).coerceIn(0, 24)
                val score = if (prepared != null) {
                    // Shape and endpoints dominate; transit letters and learned
                    // context are only tie-breakers for geometrically close words.
                    SwipeGeometryScorer.score(prepared, form, motorOffset) +
                        distance * 0.6f - languageBoost * 1.2f -
                        contextBoost * 1.0f - learnedBoost * 0.7f
                } else {
                    val endpointPenalty =
                        (if (compactForm.firstOrNull() == first) 0 else 5) +
                            (if (compactForm.lastOrNull() == last) 0 else 4)
                    distance * 7f + abs(compactForm.length - sig.length) * 2f +
                        endpointPenalty - orderedCoverage(compact, compactForm) * 2f -
                        languageBoost * 4.5f - contextBoost * 5f - learnedBoost * 6f
                }
                CandidateScore(item.word, score)
            }
            .sortedBy { it.score }
            .take(if (hasGeometry) 6 else 5)
            .map { applyCase(it.word, sentenceStart) }
            .toMutableList()

        val raw = applyCase(sig, sentenceStart)

        if (ranked.isEmpty()) {
            ranked.add(raw)
        } else if (
            !hasGeometry && ranked.none {
                it.equals(raw, ignoreCase = true)
            }
        ) {
            ranked.add(raw)
        }

        return ranked.take(6)
    }

    private fun contextualBoost(
        previousWord: String,
        candidate: String,
        languages: Set<String>
    ): Int {
        var best = 0

        languages.forEach { language ->
            val pack = LanguagePackRegistry.get(language)
                ?: return@forEach

            val index = pack.commonNext[previousWord]
                .orEmpty()
                .indexOfFirst {
                    it.equals(
                        candidate,
                        ignoreCase = true
                    )
                }

            if (index >= 0) {
                best = maxOf(
                    best,
                    (6 - index).coerceAtLeast(1)
                )
            }
        }

        return best
    }

    private fun previousWord(context: String): String =
        context.trim()
            .split(Regex("\\s+"))
            .lastOrNull()
            ?.trim {
                !it.isLetter() &&
                    it != '\''
            }
            ?.lowercase()
            .orEmpty()

    private fun isSentenceStart(
        context: String
    ): Boolean {
        val trimmed = context.trimEnd()

        return trimmed.isEmpty() ||
            trimmed.last() in ".!?\n"
    }

    private fun applyCase(
        word: String,
        sentenceStart: Boolean
    ): String =
        if (
            sentenceStart &&
            word.isNotEmpty()
        ) {
            word.replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase()
                } else {
                    it.toString()
                }
            }
        } else {
            word
        }

    private fun languageBoost(
        candidateLanguages: Set<String>,
        preferredLanguages: List<String>
    ): Int {
        val normalized =
            preferredLanguages
                .map {
                    it.substringBefore('-')
                        .lowercase()
                }

        val index =
            normalized.indexOfFirst {
                it in candidateLanguages
            }

        return if (index < 0) {
            0
        } else {
            (5 - index).coerceAtLeast(1)
        }
    }

    private fun orderedCoverage(
        trace: List<Char>,
        word: String
    ): Int {
        // Count word letters in order while ignoring keys crossed between them.
        var traceIndex = 0
        var score = 0
        for (c in word) {
            val index = (traceIndex until trace.size).firstOrNull { trace[it] == c }
            if (index != null) {
                score++
                traceIndex = index + 1
            }
        }
        return score
    }

    private fun swipeForm(
        rawWord: String
    ): String {
        val expanded = rawWord
            .lowercase()
            .replace("ß", "ss")
            .replace("æ", "ae")
            .replace("œ", "oe")
            .replace("'", "")
            .replace("-", "")

        return Normalizer.normalize(
            expanded,
            Normalizer.Form.NFD
        )
            .replace(
                Regex("\\p{M}+"),
                ""
            )
    }

    private fun levenshtein(
        a: String,
        b: String
    ): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous =
            IntArray(b.length + 1) { it }

        var current =
            IntArray(b.length + 1)

        for (i in a.indices) {
            current[0] = i + 1

            for (j in b.indices) {
                val cost =
                    if (a[i] == b[j]) 0 else 1

                current[j + 1] =
                    minOf(
                        current[j] + 1,
                        previous[j + 1] + 1,
                        previous[j] + cost
                    )
            }

            val swap = previous
            previous = current
            current = swap
        }

        return previous[b.length]
    }
}
