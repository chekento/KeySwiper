package cloud.kosch.keyswiper.prediction

import cloud.kosch.keyswiper.language.CodeSwitchLanguageResolver
import cloud.kosch.keyswiper.language.LanguageLane
import cloud.kosch.keyswiper.language.LanguagePackRegistry
import cloud.kosch.keyswiper.language.UserVocabularyLookup

class ContextPredictionEngine(
    private val learningStore: PredictionMemory,
    private val userVocabulary: UserVocabularyLookup? = null
) {

    private data class Phrase(
        val prefix: List<String>,
        val completion: String,
        val languages: Set<String>
    )

    private data class ScoredSuggestion(
        val suggestion: PredictionSuggestion,
        val score: Int
    )

    private val phraseBank = listOf(
        Phrase(listOf("ich","möchte"), "gerne noch etwas ergänzen", setOf("de")),
        Phrase(listOf("wir","können"), "das direkt umsetzen", setOf("de")),
        Phrase(listOf("das","ist"), "eine gute Idee", setOf("de")),
        Phrase(listOf("bitte"), "mach damit weiter", setOf("de")),
        Phrase(listOf("ich","denke"), "dass das gut funktionieren kann", setOf("de")),
        Phrase(listOf("wenn","wir"), "das so machen", setOf("de")),
        Phrase(listOf("i","would"), "like to add one more thing", setOf("en")),
        Phrase(listOf("we","can"), "do that directly", setOf("en")),
        Phrase(listOf("this","is"), "a good idea", setOf("en")),
        Phrase(listOf("please"), "continue with that", setOf("en")),
        Phrase(listOf("io","voglio"), "continuare così", setOf("it")),
        Phrase(listOf("je","veux"), "continuer comme ça", setOf("fr")),
        Phrase(listOf("yo","quiero"), "seguir así", setOf("es"))
    )

    fun predict(
        beforeCursor: String,
        languageHints: List<String>,
        maxSuggestions: Int = 5,
        fallbackLanguage: String? = null
    ): List<PredictionSuggestion> {
        val partial = currentToken(beforeCursor)
        val completedWords = completedWords(beforeCursor)
        val contextWords = completedWords.takeLast(5)

        val lanes = CodeSwitchLanguageResolver.resolveForPrediction(
            contextText = beforeCursor,
            detectedLanguages = languageHints,
            currentToken = partial,
            fallbackLanguage = fallbackLanguage
        )

        val scored = mutableMapOf<String, ScoredSuggestion>()

        if (partial.isNotBlank()) {
            addCompletions(
                scored = scored,
                partial = partial,
                contextWords = contextWords,
                lanes = lanes
            )
        } else {
            addNextWords(
                scored = scored,
                contextWords = contextWords,
                lanes = lanes
            )

            addSentenceContinuations(
                scored = scored,
                contextWords = contextWords,
                lanes = lanes
            )
        }

        if (scored.size < maxSuggestions) {
            addFallbacks(
                scored = scored,
                partial = partial,
                contextWords = contextWords,
                lanes = lanes
            )
        }

        return scored.values
            .sortedByDescending { it.score }
            .map { it.suggestion }
            .take(maxSuggestions)
    }

    private fun addCompletions(
        scored: MutableMap<String, ScoredSuggestion>,
        partial: String,
        contextWords: List<String>,
        lanes: List<LanguageLane>
    ) {
        userVocabulary
            ?.prefixMatches(partial, lanes, 8)
            .orEmpty()
            .forEach { (candidate, personalScore) ->
                add(
                    scored,
                    PredictionSuggestion(
                        display = candidate,
                        commitText = candidate,
                        kind = PredictionKind.COMPLETION,
                        replacesCurrentToken = true,
                        confidence = 0.96f
                    ),
                    1160 + personalScore
                )
            }

        lanes.forEach { lane ->
            val pack = LanguagePackRegistry.get(lane.tag)
                ?: return@forEach

            pack.prefixMatches(partial, 20)
                .forEachIndexed { index, candidate ->
                    val technicalBoost =
                        if (candidate in pack.technicalTerms) 35 else 0

                    add(
                        scored,
                        PredictionSuggestion(
                            display = candidate,
                            commitText = candidate,
                            kind = PredictionKind.COMPLETION,
                            replacesCurrentToken = true,
                            confidence = (
                                0.94f -
                                    index * 0.015f +
                                    lane.score * 0.03f
                                ).coerceIn(0.45f, 0.98f)
                        ),
                        900 +
                            (lane.score * 180f).toInt() -
                            index * 9 +
                            technicalBoost +
                            learningStore.boost(contextWords, candidate)
                    )
                }
        }
    }

    private fun addNextWords(
        scored: MutableMap<String, ScoredSuggestion>,
        contextWords: List<String>,
        lanes: List<LanguageLane>
    ) {
        learningStore.learnedFollowers(contextWords, limit = 12)
            .filter { (candidate, _) ->
                matchesActiveLanguage(candidate, lanes)
            }
            .forEach { (candidate, learnedScore) ->
                add(
                    scored,
                    PredictionSuggestion(
                        display = candidate,
                        commitText = candidate,
                        kind = PredictionKind.NEXT_WORD,
                        confidence = 0.94f
                    ),
                    1220 + learnedScore
                )
            }

        val last = contextWords.lastOrNull().orEmpty()

        lanes.forEach { lane ->
            val pack = LanguagePackRegistry.get(lane.tag)
                ?: return@forEach

            pack.commonNext[last].orEmpty()
                .forEachIndexed { index, candidate ->
                    add(
                        scored,
                        PredictionSuggestion(
                            display = candidate,
                            commitText = candidate,
                            kind = PredictionKind.NEXT_WORD,
                            confidence = (
                                0.90f -
                                    index * 0.035f +
                                    lane.score * 0.035f
                                ).coerceIn(0.50f, 0.97f)
                        ),
                        950 +
                            (lane.score * 210f).toInt() -
                            index * 16 +
                            learningStore.boost(contextWords, candidate)
                    )
                }
        }

        userVocabulary
            ?.frequentWords(lanes, 8)
            .orEmpty()
            .forEach { (candidate, personalScore) ->
                add(
                    scored,
                    PredictionSuggestion(
                        display = candidate,
                        commitText = candidate,
                        kind = PredictionKind.NEXT_WORD,
                        confidence = 0.68f
                    ),
                    460 + personalScore +
                        learningStore.boost(contextWords, candidate)
                )
            }
    }

    private fun addSentenceContinuations(
        scored: MutableMap<String, ScoredSuggestion>,
        contextWords: List<String>,
        lanes: List<LanguageLane>
    ) {
        val activeTags = lanes.map { it.tag }.toSet()
        val normalizedContext = contextWords.map { it.lowercase() }

        phraseBank.asSequence()
            .filter { phrase ->
                phrase.languages.any { it in activeTags }
            }
            .filter { phrase ->
                normalizedContext.takeLast(phrase.prefix.size) ==
                    phrase.prefix
            }
            .take(4)
            .forEachIndexed { index, phrase ->
                val languageWeight = lanes
                    .filter { it.tag in phrase.languages }
                    .maxOfOrNull { it.score }
                    ?: 0.4f

                add(
                    scored,
                    PredictionSuggestion(
                        display = "→ ${phrase.completion}",
                        commitText = phrase.completion,
                        kind = PredictionKind.SENTENCE,
                        confidence = (
                            0.88f +
                                languageWeight * 0.04f -
                                index * 0.05f
                            ).coerceIn(0.50f, 0.95f)
                    ),
                    990 +
                        (languageWeight * 120f).toInt() -
                        index * 22 +
                        learningStore.boost(
                            contextWords,
                            phrase.completion
                        )
                )
            }
    }

    private fun addFallbacks(
        scored: MutableMap<String, ScoredSuggestion>,
        partial: String,
        contextWords: List<String>,
        lanes: List<LanguageLane>
    ) {
        lanes.forEach { lane ->
            val pack = LanguagePackRegistry.get(lane.tag)
                ?: return@forEach

            val candidates = if (partial.isBlank()) {
                pack.words
                    .asSequence()
                    .filter { it.length in 2..12 }
                    .sorted()
                    .take(22)
                    .toList()
            } else {
                pack.prefixMatches(partial, 22)
            }

            candidates.forEachIndexed { index, candidate ->
                add(
                    scored,
                    PredictionSuggestion(
                        display = candidate,
                        commitText = candidate,
                        kind = if (partial.isBlank()) {
                            PredictionKind.NEXT_WORD
                        } else {
                            PredictionKind.COMPLETION
                        },
                        replacesCurrentToken = partial.isNotBlank(),
                        confidence = (
                            0.38f + lane.score * 0.08f
                            ).coerceAtMost(0.55f)
                    ),
                    250 +
                        (lane.score * 100f).toInt() -
                        index +
                        learningStore.boost(contextWords, candidate)
                )
            }
        }
    }

    private fun matchesActiveLanguage(
        candidate: String,
        lanes: List<LanguageLane>
    ): Boolean {
        val primary = lanes.firstOrNull()?.tag ?: return true

        return CodeSwitchLanguageResolver.matchesLanguage(
            text = candidate,
            languageTag = primary
        )
    }

    private fun add(
        map: MutableMap<String, ScoredSuggestion>,
        suggestion: PredictionSuggestion,
        score: Int
    ) {
        val key = suggestion.commitText.lowercase()
        val existing = map[key]

        if (existing == null || score > existing.score) {
            map[key] = ScoredSuggestion(
                suggestion = suggestion,
                score = score
            )
        }
    }

    private fun currentToken(text: String): String {
        val tail = text.takeLast(280)

        if (
            tail.isEmpty() ||
            tail.last().isWhitespace()
        ) {
            return ""
        }

        return tail
            .takeLastWhile {
                it.isLetterOrDigit() ||
                    it == '\'' ||
                    it == '-'
            }
            .lowercase()
    }

    private fun completedWords(text: String): List<String> {
        val partial = currentToken(text)
        val effective = if (partial.isNotBlank()) {
            text.dropLast(partial.length)
        } else {
            text
        }

        return Regex("[\\p{L}\\p{N}'-]+")
            .findAll(effective.takeLast(700))
            .map { it.value.lowercase() }
            .toList()
    }
}
