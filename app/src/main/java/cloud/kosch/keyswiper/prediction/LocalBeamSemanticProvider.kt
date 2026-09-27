package cloud.kosch.keyswiper.prediction

import cloud.kosch.keyswiper.language.CodeSwitchLanguageResolver
import kotlin.math.ln

class LocalBeamSemanticProvider(
    private val memory: PredictionMemory
) : PredictionProvider {

    override val id: String = "local-beam-v2-contextual"

    private data class Beam(
        val history: List<String>,
        val generated: List<String>,
        val score: Double
    )

    private val graph = mapOf(
        "de" to mapOf(
            "ich" to listOf("möchte","denke","kann","würde","habe"),
            "möchte" to listOf("gerne","noch","das","eine"),
            "gerne" to listOf("noch","mehr","wissen","weiter"),
            "wir" to listOf("können","sollten","haben","müssen"),
            "können" to listOf("das","jetzt","direkt","auch"),
            "das" to listOf("ist","so","direkt","noch"),
            "ist" to listOf("eine","sehr","gut","jetzt"),
            "eine" to listOf("gute","sehr","weitere","neue"),
            "gute" to listOf("idee","lösung","basis","frage"),
            "bitte" to listOf("mach","weiter","noch","prüfe"),
            "mach" to listOf("damit","bitte","das","weiter"),
            "damit" to listOf("weiter","wir","das","es"),
            "wenn" to listOf("wir","das","du","ich"),
            "dass" to listOf("das","wir","es","ich"),
            "noch" to listOf("etwas","mehr","eine","mal"),
            "etwas" to listOf("ergänzen","ändern","verbessern","genauer"),
            "mehr" to listOf("details","intelligenz","kontext","möglichkeiten"),
            "kontext" to listOf("kennen","berücksichtigen","nutzen","verstehen"),
            "vorschläge" to listOf("sollen","können","werden","passen"),
            "tastatur" to listOf("soll","kann","lernt","erkennt"),
            "prediction" to listOf("soll","kann","lernt","nutzt")
        ),
        "en" to mapOf(
            "i" to listOf("would","want","think","can","need"),
            "would" to listOf("like","be","prefer","also"),
            "like" to listOf("to","this","that","more"),
            "to" to listOf("add","continue","make","see"),
            "we" to listOf("can","should","need","could"),
            "can" to listOf("do","make","also","use"),
            "this" to listOf("is","would","can","looks"),
            "is" to listOf("a","very","really","still"),
            "a" to listOf("good","great","better","new"),
            "please" to listOf("continue","add","check","make"),
            "continue" to listOf("with","this","and","from"),
            "with" to listOf("that","this","the","more"),
            "that" to listOf("would","is","we","it"),
            "context" to listOf("aware","matters","helps","improves"),
            "suggestions" to listOf("should","can","will","need"),
            "keyboard" to listOf("should","can","learns","predicts"),
            "prediction" to listOf("should","can","learns","uses")
        ),
        "it" to mapOf(
            "io" to listOf("voglio","penso","posso","vorrei"),
            "voglio" to listOf("continuare","aggiungere","anche","più"),
            "noi" to listOf("possiamo","dovremmo","vogliamo"),
            "possiamo" to listOf("farlo","continuare","anche","ora"),
            "contesto" to listOf("conoscere","usare","capire","considerare")
        ),
        "fr" to mapOf(
            "je" to listOf("veux","pense","peux","voudrais"),
            "veux" to listOf("continuer","ajouter","aussi","plus"),
            "nous" to listOf("pouvons","devrions","voulons"),
            "pouvons" to listOf("continuer","faire","aussi","maintenant"),
            "contexte" to listOf("connaître","utiliser","comprendre","considérer")
        ),
        "es" to mapOf(
            "yo" to listOf("quiero","pienso","puedo","quisiera"),
            "quiero" to listOf("seguir","añadir","también","más"),
            "nosotros" to listOf("podemos","deberíamos","queremos"),
            "podemos" to listOf("seguir","hacerlo","también","ahora"),
            "contexto" to listOf("conocer","usar","entender","considerar")
        )
    )

    private val topicAssociations = mapOf(
        "tastatur" to listOf("vorschläge","eingabe","swipe","sprache","kontext"),
        "keyboard" to listOf("suggestions","input","swipe","language","context"),
        "prediction" to listOf("kontext","vorschläge","lernen","context","suggestions","learning"),
        "vorschläge" to listOf("kontext","besser","intelligent","passen"),
        "suggestions" to listOf("context","better","smart","relevant"),
        "kontext" to listOf("kennen","nutzen","berücksichtigen","verstehen"),
        "context" to listOf("aware","use","understand","relevant"),
        "email" to listOf("antwort","grüße","danke","reply","regards","thanks"),
        "code" to listOf("return","class","function","fun","val","var"),
        "android" to listOf("app","keyboard","tastatur","input","ime"),
        "modell" to listOf("lokal","prediction","sprache","inferenz"),
        "model" to listOf("local","prediction","language","inference")
    )

    private val modeBoosts = mapOf(
        PredictionInputMode.MESSAGE to setOf(
            "danke","gerne","super","später","heute",
            "thanks","great","later","today","please"
        ),
        PredictionInputMode.EMAIL to setOf(
            "bitte","vielen","freundlichen","danke","anbei",
            "please","regards","thank","attached","best"
        ),
        PredictionInputMode.SEARCH to setOf(
            "beste","vergleich","download","android","app",
            "best","review","download","android","app"
        ),
        PredictionInputMode.CODE to setOf(
            "if","else","return","class","fun","val","var","null","true","false"
        ),
        PredictionInputMode.GENERAL to emptySet()
    )

    override fun predict(
        context: PredictionContext,
        maxSuggestions: Int
    ): List<PredictionSuggestion> {
        val partial = currentToken(context.beforeCursor)
        if (partial.isNotBlank()) return emptyList()

        val snapshot = context.surrounding
        val contextSource = snapshot.currentSentenceBefore
            .ifBlank { context.beforeCursor }

        val history = extractWords(contextSource).takeLast(5)
        if (history.isEmpty()) return emptyList()

        val languages = CodeSwitchLanguageResolver.resolve(
            contextText = snapshot.currentParagraph.ifBlank {
                context.beforeCursor
            },
            detectedLanguages = context.languageHints,
            currentToken = "",
            maxLanes = 3
        ).map { it.tag }
        val depth = context.maxSemanticTokens.coerceIn(2, 6)
        val afterWords = extractWords(snapshot.currentSentenceAfter).take(4)
        val topics = snapshot.topicTerms.map { it.lowercase() }.toSet()

        var beams = listOf(
            Beam(
                history = history,
                generated = emptyList(),
                score = 0.0
            )
        )

        val finished = mutableListOf<Beam>()

        repeat(depth) {
            val expanded = mutableListOf<Beam>()

            for (beam in beams) {
                val candidates = candidatesFor(
                    history = beam.history,
                    languages = languages,
                    mode = context.inputMode,
                    topics = topics
                )

                candidates.take(12).forEachIndexed { index, candidate ->
                    val nextHistory = (beam.history + candidate).takeLast(5)
                    val personal = memory.boost(beam.history, candidate)
                    val rankPrior = 1.0 / (index + 1.0)
                    val personalPrior = 1.0 + personal / 65.0

                    val modePrior = if (
                        candidate.lowercase() in modeBoosts[context.inputMode].orEmpty()
                    ) 1.45 else 1.0

                    val topicPrior = topicPrior(candidate, topics)
                    val questionPrior = if (
                        snapshot.isQuestion &&
                        candidate.lowercase() in setOf(
                            "weil","dann","wenn","kann","können",
                            "because","then","if","can","could"
                        )
                    ) 1.18 else 1.0

                    val nextScore = beam.score +
                        ln(
                            (
                                rankPrior *
                                    personalPrior *
                                    modePrior *
                                    topicPrior *
                                    questionPrior
                                ).coerceAtLeast(0.0001)
                        )

                    val next = Beam(
                        history = nextHistory,
                        generated = beam.generated + candidate,
                        score = nextScore
                    )

                    expanded += next

                    if (next.generated.size >= 2) {
                        finished += next
                    }
                }
            }

            beams = expanded
                .sortedByDescending { beam ->
                    normalizedScore(
                        beam = beam,
                        afterWords = afterWords,
                        languages = languages
                    )
                }
                .take(24)

            if (beams.isEmpty()) return@repeat
        }

        return finished
            .filter { it.generated.size >= 2 }
            .filterNot { duplicatesAfterCursor(it.generated, afterWords) }
            .sortedByDescending { beam ->
                normalizedScore(
                    beam = beam,
                    afterWords = afterWords,
                    languages = languages
                )
            }
            .distinctBy { it.generated.joinToString(" ").lowercase() }
            .take(maxSuggestions)
            .mapIndexed { index, beam ->
                val phrase = beam.generated.joinToString(" ")
                PredictionSuggestion(
                    display = "→ $phrase",
                    commitText = phrase,
                    kind = PredictionKind.SENTENCE,
                    confidence = (0.92f - index * 0.07f).coerceAtLeast(0.50f)
                )
            }
    }

    private fun candidatesFor(
        history: List<String>,
        languages: List<String>,
        mode: PredictionInputMode,
        topics: Set<String>
    ): List<String> {
        val scored = mutableMapOf<String, Int>()

        memory.learnedFollowers(history, limit = 12)
            .forEach { (word, score) ->
                scored[word] = maxOf(scored[word] ?: 0, 1100 + score)
            }

        val last = history.lastOrNull()?.lowercase().orEmpty()

        languages.forEach { language ->
            graph[language]?.get(last).orEmpty().forEachIndexed { index, word ->
                var score = 760 - index * 35

                if (word.lowercase() in modeBoosts[mode].orEmpty()) {
                    score += 130
                }

                score += memory.boost(history, word)
                scored[word] = maxOf(scored[word] ?: 0, score)
            }
        }

        topics.forEach { topic ->
            topicAssociations[topic].orEmpty().forEachIndexed { index, word ->
                val score = 570 - index * 30 + memory.boost(history, word)
                scored[word] = maxOf(scored[word] ?: 0, score)
            }
        }

        return scored.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(14)
    }

    private fun normalizedScore(
        beam: Beam,
        afterWords: List<String>,
        languages: List<String>
    ): Double {
        val base = beam.score / beam.generated.size.coerceAtLeast(1)
        val bridge = bridgeScore(beam.generated.lastOrNull(), afterWords.firstOrNull(), languages)
        return base + lengthPreference(beam.generated.size) + bridge
    }

    private fun bridgeScore(
        generatedLast: String?,
        afterFirst: String?,
        languages: List<String>
    ): Double {
        if (generatedLast.isNullOrBlank() || afterFirst.isNullOrBlank()) return 0.0
        if (generatedLast.equals(afterFirst, ignoreCase = true)) return -0.8

        val connects = languages.any { language ->
            graph[language]
                ?.get(generatedLast.lowercase())
                .orEmpty()
                .any { it.equals(afterFirst, ignoreCase = true) }
        }

        return if (connects) 0.40 else 0.0
    }

    private fun duplicatesAfterCursor(
        generated: List<String>,
        afterWords: List<String>
    ): Boolean {
        if (generated.isEmpty() || afterWords.isEmpty()) return false

        val max = minOf(generated.size, afterWords.size, 3)
        for (length in max downTo 1) {
            val tail = generated.takeLast(length).map { it.lowercase() }
            val head = afterWords.take(length).map { it.lowercase() }
            if (tail == head) return true
        }

        return false
    }

    private fun topicPrior(
        candidate: String,
        topics: Set<String>
    ): Double {
        val normalized = candidate.lowercase()
        if (normalized in topics) return 1.12

        val associated = topics.any { topic ->
            topicAssociations[topic]
                .orEmpty()
                .any { it.equals(normalized, ignoreCase = true) }
        }

        return if (associated) 1.28 else 1.0
    }

    private fun lengthPreference(length: Int): Double =
        when (length) {
            2 -> 0.05
            3 -> 0.12
            4 -> 0.20
            5 -> 0.22
            6 -> 0.16
            else -> 0.08
        }

    private fun preferredLanguages(hints: List<String>): List<String> {
        val available = graph.keys

        val normalized = hints
            .map { it.substringBefore('-').lowercase() }
            .filter { it in available }
            .distinct()

        return (normalized + listOf("de","en","it","fr","es")).distinct()
    }

    private fun currentToken(text: String): String {
        if (text.isEmpty() || text.last().isWhitespace()) return ""

        return text.takeLastWhile {
            it.isLetterOrDigit() || it == '\'' || it == '-'
        }
    }

    private fun extractWords(text: String): List<String> =
        Regex("[\\p{L}\\p{N}'-]+")
            .findAll(text.takeLast(900))
            .map { it.value.lowercase() }
            .toList()
}
