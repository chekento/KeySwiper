package cloud.kosch.keyswiper.prediction

import cloud.kosch.keyswiper.language.CodeSwitchLanguageResolver
import cloud.kosch.keyswiper.language.LanguageLane
import cloud.kosch.keyswiper.language.LanguagePackRegistry
import cloud.kosch.keyswiper.language.UserVocabularyLookup

class ContextPredictionEngine(
    private val learningStore: PredictionMemory,
    private val userVocabulary: UserVocabularyLookup? = null
) {

    private val corrections = WordCorrectionEngine(learningStore, userVocabulary)

    private data class ScoredSuggestion(
        val suggestion: PredictionSuggestion,
        val score: Int
    )

    private val continuations = LocalBeamSemanticProvider(learningStore)

    fun predict(
        beforeCursor: String,
        languageHints: List<String>,
        maxSuggestions: Int = 5,
        fallbackLanguage: String? = null,
        afterCursor: String = ""
    ): List<PredictionSuggestion> {
        val cursorWord = cloud.kosch.keyswiper.input.CursorWord.at(beforeCursor, afterCursor)
        val partial = cursorWord.prefix.ifEmpty { cursorWord.suffix }.lowercase()
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
            val raw = cursorWord.whole
            val language = lanes.firstOrNull()?.tag ?: fallbackLanguage ?: "de"
            val choices = corrections.candidates(raw, contextWords, language, beforeToken = beforeCursor.dropLast(cursorWord.prefix.length))
            val hasExactCompletion = LanguagePackRegistry.get(language)?.prefixMatches(raw, 24).orEmpty()
                .any { it.length > raw.length && it.startsWith(raw, ignoreCase = true) }
            choices.forEachIndexed { index, choice ->
                val correctionScore = when {
                    choice.automatic -> 1650
                    choice.cost <= 0.3f -> 1500
                    hasExactCompletion -> 1040
                    else -> 1450
                }
                add(scored, PredictionSuggestion(choice.word, choice.word, PredictionKind.CORRECTION,
                    replacesCurrentToken = true, confidence = choice.confidence),
                    correctionScore - index * 80)
            }
            if (choices.isNotEmpty()) {
                add(scored, PredictionSuggestion(raw, raw, PredictionKind.KEEP_TYPED,
                    replacesCurrentToken = true, confidence = 1f),
                    if (hasExactCompletion && choices.none { it.automatic || it.cost <= 0.3f }) 820 else 1250)
            }
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
            .sortedByDescending { entry ->
                val candidate = entry.suggestion.commitText.lowercase()
                val suffix = cursorWord.suffix.lowercase()
                val suffixScore = if (suffix.isNotEmpty() && candidate.endsWith(suffix)) 320 else 0
                val following = afterCursor.drop(cursorWord.suffix.length).trimStart()
                    .takeWhile { it.isLetter() }.lowercase()
                val bridge = lanes.any { lane ->
                    following.isNotBlank() && following in LanguagePackRegistry.get(lane.tag)?.commonNext?.get(candidate).orEmpty()
                }
                entry.score + suffixScore + if (bridge) 220 else 0
            }
            .map { it.suggestion }
            .take(maxSuggestions)
            .map { suggestion ->
                val typed = cursorWord.prefix
                val prefix = beforeCursor.dropLast(typed.length)
                val language = lanes.firstOrNull()?.tag ?: fallbackLanguage ?: "de"
                val value = when (suggestion.kind) {
                    PredictionKind.CORRECTION, PredictionKind.KEEP_TYPED -> suggestion.commitText
                    PredictionKind.SENTENCE -> suggestion.commitText
                    else -> Orthography.display(suggestion.commitText, language, prefix,
                        Orthography.sentenceStart(prefix), typed,
                        userVocabulary?.isExplicit(suggestion.commitText) == true ||
                            userVocabulary?.contains(suggestion.commitText) == true && LanguagePackRegistry.get(language)?.contains(suggestion.commitText) != true)
                }
                suggestion.copy(display = value, commitText = value)
            }
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
            .filter { (candidate, _) -> matchesActiveLanguage(candidate, lanes) }
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
                    1080 + personalScore.coerceIn(0, 180) + contextBoost(contextWords, candidate, lanes)
                )
            }

        lanes.forEach { lane ->
            val pack = LanguagePackRegistry.get(lane.tag)
                ?: return@forEach

            pack.prefixMatches(partial, 80)
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
                            index.coerceAtMost(25) * 3 +
                            contextBoost(contextWords, candidate, lanes) +
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
                    1120 + learnedScore.coerceIn(0, 240)
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
            .filter { (candidate, _) -> matchesActiveLanguage(candidate, lanes) }
            .forEach { (candidate, personalScore) ->
                add(
                    scored,
                    PredictionSuggestion(
                        display = candidate,
                        commitText = candidate,
                        kind = PredictionKind.NEXT_WORD,
                        confidence = 0.68f
                    ),
                    460 + personalScore.coerceIn(0, 200) +
                        learningStore.boost(contextWords, candidate)
                )
            }
    }

    private fun addSentenceContinuations(
        scored: MutableMap<String, ScoredSuggestion>,
        contextWords: List<String>,
        lanes: List<LanguageLane>
    ) {
        val before = contextWords.joinToString(" ") + " "
        continuations.predict(PredictionContext(before, lanes.map { it.tag }, lanes.firstOrNull()?.tag,
            maxSemanticTokens = 8, surrounding = LocalContextAnalyzer.analyze(before, "", "")), 2)
            .forEachIndexed { index, suggestion -> add(scored, suggestion, 1230 - index * 35) }
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
                when (lane.tag) {
                    "de" -> listOf("ich", "das", "wir", "die", "und", "bitte", "hallo", "danke", "morgen", "heute")
                    "en" -> listOf("I", "the", "we", "and", "you", "please", "hello", "thanks", "today")
                    "fr" -> listOf("je", "le", "nous", "et", "vous", "bonjour", "merci")
                    "it" -> listOf("io", "il", "noi", "e", "tu", "ciao", "grazie")
                    else -> listOf("yo", "el", "nosotros", "y", "tú", "hola", "gracias")
                }
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

    private fun contextBoost(context: List<String>, candidate: String, lanes: List<LanguageLane>): Int {
        val last = context.lastOrNull() ?: return 0
        return lanes.maxOfOrNull { lane ->
            val index = LanguagePackRegistry.get(lane.tag)?.commonNext?.get(last)?.indexOf(candidate) ?: -1
            if (index >= 0) (300 - index * 30).coerceAtLeast(90) else 0
        } ?: 0
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
        val key = if (suggestion.kind == PredictionKind.KEEP_TYPED) "keep:${suggestion.commitText}" else suggestion.commitText.lowercase()
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
