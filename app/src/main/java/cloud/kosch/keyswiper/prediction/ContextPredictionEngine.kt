package cloud.kosch.keyswiper.prediction

class ContextPredictionEngine(
    private val learningStore: PredictionMemory
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

    private val languageWords = mapOf(
        "de" to listOf(
            "aber","alle","also","auch","auf","aus","bei","bin","bitte","danke","das","dein","deine",
            "den","der","die","doch","ein","eine","einen","einer","es","für","gut","habe","haben","hallo",
            "heute","hier","ich","ist","ja","jetzt","kann","können","machen","man","mehr","mein","meine",
            "mit","morgen","möchte","muss","nach","nicht","noch","oder","sehr","so","später","super","und",
            "uns","von","was","weiter","wenn","wie","wir","wird","würde","zu","zum"
        ),
        "en" to listOf(
            "about","also","and","are","because","can","could","do","for","from","good","great","have","hello",
            "here","how","i","if","is","it","later","like","make","more","need","now","of","on","or","please",
            "really","so","that","the","then","there","this","today","tomorrow","very","want","we","what",
            "when","with","would","yes","you","your"
        ),
        "it" to listOf(
            "anche","bene","buongiorno","ciao","come","con","domani","e","fare","grazie","ho","io","ma","molto",
            "non","oggi","ora","per","perché","più","posso","questo","se","si","sono","tu","un","una","voglio"
        ),
        "fr" to listOf(
            "alors","aussi","avec","bien","bonjour","ce","comme","demain","et","faire","je","maintenant","mais",
            "merci","non","nous","oui","parce","pas","plus","pour","peux","quand","que","très","tu","un","une",
            "vous","veux"
        ),
        "es" to listOf(
            "ahora","bien","como","con","cuando","de","gracias","hola","hoy","mañana","más","muy","no","para",
            "pero","porque","puedo","que","quiero","si","sí","también","un","una","y","yo"
        )
    )

    private val transitions = mapOf(
        "de" to mapOf(
            "ich" to listOf("möchte","kann","habe","bin","würde","muss"),
            "wir" to listOf("können","haben","sollten","machen","brauchen"),
            "das" to listOf("ist","wäre","kann","sollte","funktioniert"),
            "es" to listOf("ist","gibt","wäre","kann","soll"),
            "bitte" to listOf("weiter","prüfen","machen","noch","auch"),
            "sehr" to listOf("gut","gerne","schön","wichtig"),
            "nicht" to listOf("nur","mehr","ganz","so"),
            "wie" to listOf("kann","sieht","geht","wäre")
        ),
        "en" to mapOf(
            "i" to listOf("want","would","can","have","need","think"),
            "we" to listOf("can","should","need","have","want"),
            "this" to listOf("is","would","can","looks","works"),
            "it" to listOf("is","would","can","looks","works"),
            "please" to listOf("continue","check","make","add","also"),
            "very" to listOf("good","important","useful","nice"),
            "not" to listOf("only","yet","really","just")
        ),
        "it" to mapOf(
            "io" to listOf("voglio","posso","ho","sono"),
            "noi" to listOf("possiamo","vogliamo","abbiamo"),
            "questo" to listOf("è","può","sarebbe"),
            "molto" to listOf("bene","importante","utile")
        ),
        "fr" to mapOf(
            "je" to listOf("veux","peux","suis","pense"),
            "nous" to listOf("pouvons","devons","avons"),
            "ce" to listOf("est","serait","peut"),
            "très" to listOf("bien","important","utile")
        ),
        "es" to mapOf(
            "yo" to listOf("quiero","puedo","tengo","soy"),
            "nosotros" to listOf("podemos","queremos","tenemos"),
            "esto" to listOf("es","puede","sería"),
            "muy" to listOf("bien","importante","útil")
        )
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
        maxSuggestions: Int = 5
    ): List<PredictionSuggestion> {
        val partial = currentToken(beforeCursor)
        val completedWords = completedWords(beforeCursor)
        val languages = preferredLanguages(languageHints)
        val contextWords = completedWords.takeLast(4)
        val scored = mutableMapOf<String, ScoredSuggestion>()

        if (partial.isNotBlank()) {
            for (language in languages) {
                languageWords[language].orEmpty()
                    .filter {
                        it.startsWith(partial, ignoreCase = true) &&
                            !it.equals(partial, ignoreCase = true)
                    }
                    .forEachIndexed { index, candidate ->
                        add(
                            scored,
                            PredictionSuggestion(
                                display = candidate,
                                commitText = candidate,
                                kind = PredictionKind.COMPLETION,
                                replacesCurrentToken = true,
                                confidence = (0.94f - index * 0.02f).coerceAtLeast(0.45f)
                            ),
                            900 - index * 12 + learningStore.boost(contextWords, candidate)
                        )
                    }
            }
        } else {
            learningStore.learnedFollowers(contextWords).forEach { (candidate, learnedScore) ->
                add(
                    scored,
                    PredictionSuggestion(
                        display = candidate,
                        commitText = candidate,
                        kind = PredictionKind.NEXT_WORD,
                        confidence = 0.91f
                    ),
                    1080 + learnedScore
                )
            }

            val last = contextWords.lastOrNull()?.lowercase().orEmpty()
            for (language in languages) {
                transitions[language]?.get(last).orEmpty()
                    .forEachIndexed { index, candidate ->
                        add(
                            scored,
                            PredictionSuggestion(
                                display = candidate,
                                commitText = candidate,
                                kind = PredictionKind.NEXT_WORD,
                                confidence = (0.90f - index * 0.04f).coerceAtLeast(0.50f)
                            ),
                            920 - index * 18 + learningStore.boost(contextWords, candidate)
                        )
                    }
            }

            phraseSuggestions(contextWords, languages).forEachIndexed { index, phrase ->
                add(
                    scored,
                    PredictionSuggestion(
                        display = "→ $phrase",
                        commitText = phrase,
                        kind = PredictionKind.SENTENCE,
                        confidence = (0.88f - index * 0.05f).coerceAtLeast(0.50f)
                    ),
                    990 - index * 22 + learningStore.boost(contextWords, phrase)
                )
            }
        }

        if (scored.size < maxSuggestions) {
            for (language in languages) {
                languageWords[language].orEmpty().take(18).forEachIndexed { index, candidate ->
                    if (partial.isBlank() || candidate.startsWith(partial, ignoreCase = true)) {
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
                                confidence = 0.40f
                            ),
                            250 - index + learningStore.boost(contextWords, candidate)
                        )
                    }
                }
            }
        }

        return scored.values
            .sortedByDescending { it.score }
            .map { it.suggestion }
            .take(maxSuggestions)
    }

    private fun add(
        map: MutableMap<String, ScoredSuggestion>,
        suggestion: PredictionSuggestion,
        score: Int
    ) {
        val key = suggestion.commitText.lowercase()
        val existing = map[key]
        if (existing == null || score > existing.score) {
            map[key] = ScoredSuggestion(suggestion, score)
        }
    }

    private fun phraseSuggestions(
        contextWords: List<String>,
        languages: List<String>
    ): List<String> {
        val normalizedContext = contextWords.map { it.lowercase() }

        return phraseBank.asSequence()
            .filter { phrase -> phrase.languages.any { it in languages } }
            .filter { phrase ->
                normalizedContext.takeLast(phrase.prefix.size) == phrase.prefix
            }
            .map { it.completion }
            .distinct()
            .take(4)
            .toList()
    }

    private fun preferredLanguages(hints: List<String>): List<String> {
        val normalized = hints
            .map { it.substringBefore('-').lowercase() }
            .filter { it in languageWords.keys }
            .distinct()
        return (normalized + listOf("de","en","it","fr","es")).distinct()
    }

    private fun currentToken(text: String): String {
        val tail = text.takeLast(240)
        if (tail.isEmpty() || tail.last().isWhitespace()) return ""
        return tail.takeLastWhile { it.isLetterOrDigit() || it == '\'' || it == '-' }.lowercase()
    }

    private fun completedWords(text: String): List<String> {
        val partial = currentToken(text)
        val effective = if (partial.isNotBlank()) text.dropLast(partial.length) else text

        return Regex("[\\p{L}\\p{N}'-]+")
            .findAll(effective.takeLast(500))
            .map { it.value.lowercase() }
            .toList()
    }
}
