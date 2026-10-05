package cloud.kosch.keyswiper.prediction

import cloud.kosch.keyswiper.language.CodeSwitchLanguageResolver
import kotlin.math.ln

/** Context-matched phrases plus bounded, higher-order personal continuations. */
class LocalBeamSemanticProvider(private val memory: PredictionMemory) : PredictionProvider {
    override val id = "local-context-v3"
    private data class Choice(val text: String, val score: Double, val confidence: Float)
    private data class Beam(val history: List<String>, val words: List<String>, val score: Double)
    private val unfinished = "der die das den dem des ein eine einen einem einer eines mit für von zu zum zur im am auf an und oder weil dass the a an to with for of and or di del della il la le un una et de des du les el los las y para por".split(' ').toSet()
    private val repeatedContentExempt = "ich du er sie es wir ihr der die das den dem ein eine und oder i you we he she it the a an and or".split(' ').toSet()
    private val formal = setOf("ihnen", "ihre", "ihren", "ihrem", "ihres")
    private val informal = setOf("du", "dich", "dir", "dein", "deine", "deinen", "euch")

    override fun predict(context: PredictionContext, maxSuggestions: Int): List<PredictionSuggestion> {
        if (maxSuggestions <= 0 || context.inputMode in setOf(PredictionInputMode.CODE, PredictionInputMode.SEARCH) ||
            context.surrounding.selectedText.isNotEmpty()) return emptyList()
        if (context.beforeCursor.lastOrNull()?.let { it.isLetterOrDigit() || it in "'-’" } == true) return emptyList()
        val snapshot = context.surrounding
        val source = snapshot.currentSentenceBefore.ifBlank { context.beforeCursor.substringAfterLast('\n') }
        val history = ContinuationCorpus.words(source).takeLast(8)
        if (history.isEmpty()) return emptyList()
        val language = CodeSwitchLanguageResolver.primaryInputLanguage(context.beforeCursor, context.languageHints,
            fallbackLanguage = context.inputLanguageTag) ?: return emptyList()
        val entries = ContinuationCorpus.entries.filter { it.language == language }
        val depth = context.maxSemanticTokens.coerceIn(2, 12)
        val after = ContinuationCorpus.words(snapshot.afterCursor).take(16)
        val topics = snapshot.topicTerms.map(String::lowercase).toSet()
        val registerWords = ContinuationCorpus.words(snapshot.currentParagraph.ifBlank { context.beforeCursor }).takeLast(80)
        val useFormal = registerWords.any { it in formal } || context.inputMode == PredictionInputMode.EMAIL && registerWords.none { it in informal }
        val useInformal = registerWords.any { it in informal } && !useFormal
        val choices = mutableListOf<Choice>()
        val newSentence = Orthography.sentenceStart(context.beforeCursor)

        fun add(words: List<String>, score: Double, confidence: Float, formalPhrase: Boolean = useFormal) {
            var retained = words.take(depth)
            val normalized = retained.map(ContinuationCorpus::normalize)
            val overlap = (minOf(normalized.size, after.size) downTo 1)
                .firstOrNull { normalized.takeLast(it) == after.take(it) } ?: 0
            if (overlap > 0) retained = retained.dropLast(overlap)
            while (retained.isNotEmpty() && ContinuationCorpus.normalize(retained.last()) in unfinished) retained = retained.dropLast(1)
            if (retained.size < 2) return
            val lowered = retained.map(ContinuationCorpus::normalize)
            if (after.isNotEmpty() && lowered.first() == after.first()) return
            if (lowered.zipWithNext().any { it.first == it.second }) return
            val phrase = Orthography.phrase(retained.joinToString(" "), language, context.beforeCursor, formalPhrase)
            choices += Choice(phrase, score + if (overlap > 0) 0.8 else 0.0, confidence)
        }

        // A longer matching suffix outweighs a frequent but unrelated last word.
        for (entry in entries) {
            if (newSentence) {
                val relevant = topics.count { it in entry.topics }
                if (relevant > 0 && !context.beforeCursor.contains(entry.text, true)) {
                    add(entry.displayWords, relevant * 0.7 + if (entry.mode == context.inputMode) 0.6 else 0.0, 0.78f)
                }
                continue
            }
            for (start in 0 until entry.words.lastIndex) {
                val matched = (minOf(6, history.size, entry.words.size - start - 1) downTo 1)
                    .firstOrNull { history.takeLast(it) == entry.words.subList(start, start + it) } ?: continue
                val rest = entry.displayWords.drop(start + matched)
                if (rest.size < 2) continue
                if (useFormal && rest.any { ContinuationCorpus.normalize(it) in informal }) continue
                if (useInformal && rest.any { it in setOf("Sie", "Ihnen", "Ihre", "Ihren") }) continue
                val relevant = topics.count { it in entry.topics || it in entry.words }
                val register = if (entry.mode == context.inputMode) 0.55 else if (entry.mode == PredictionInputMode.GENERAL) 0.2 else 0.0
                val learned = memory.boost(history, rest.first()).coerceIn(0, 260) / 260.0
                val score = matched * 1.45 + relevant.coerceAtMost(4) * 0.85 + register + learned +
                    if (rest.size <= depth) 0.25 else 0.0
                add(rest, score, (0.83f + matched * 0.022f + relevant.coerceAtMost(3) * 0.012f).coerceAtMost(0.98f),
                    useFormal || entry.mode == PredictionInputMode.EMAIL)
            }
        }

        // Learned transitions can form new phrases beyond the bundled examples.
        var beams = if (newSentence) emptyList() else listOf(Beam(history, emptyList(), 0.0))
        repeat(depth) {
            val expanded = mutableListOf<Beam>()
            for (beam in beams) {
                val followers = memory.learnedFollowers(beam.history, 6)
                    .filter { (word, _) -> CodeSwitchLanguageResolver.matchesLanguage(word, language) }
                for ((candidate, strength) in followers) {
                    val lower = candidate.lowercase()
                    if (lower == beam.history.lastOrNull()) continue
                    if (lower !in repeatedContentExempt && lower in beam.words.map(String::lowercase)) continue
                    val pair = beam.history.lastOrNull() to lower
                    if (beam.words.map(String::lowercase).zipWithNext().any { it == pair }) continue
                    if (useFormal && lower in informal || useInformal && lower in formal) continue
                    val next = Beam((beam.history + lower).takeLast(8), beam.words + candidate,
                        beam.score + ln(1.0 + strength.coerceAtMost(500)) / 5.0)
                    expanded += next
                    if (next.words.size >= 2) add(next.words, 2.8 + next.score / next.words.size + next.words.size * 0.08,
                        (0.82f + next.words.size * 0.009f).coerceAtMost(0.94f))
                }
            }
            beams = expanded.sortedByDescending { it.score / it.words.size }.take(12)
        }

        // Avoid spending both sentence slots on near-identical prefixes.
        val selected = mutableListOf<Choice>()
        val ranked = choices.sortedByDescending { it.score }.distinctBy { it.text.lowercase() }
        for (choice in ranked) {
            if (selected.any { similar(it.text, choice.text) }) continue
            selected += choice
            if (selected.size >= maxSuggestions) break
        }
        return selected.map { PredictionSuggestion(it.text, it.text, PredictionKind.SENTENCE, confidence = it.confidence) }
    }

    private fun similar(a: String, b: String): Boolean {
        val left = ContinuationCorpus.words(a)
        val right = ContinuationCorpus.words(b)
        val smaller = minOf(left.size, right.size)
        val sharedPrefix = left.zip(right).takeWhile { it.first == it.second }.size
        return sharedPrefix >= smaller || sharedPrefix >= 3 && sharedPrefix.toDouble() / smaller >= 0.7
    }
}
