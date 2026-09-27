package cloud.kosch.keyswiper.prediction

import kotlin.math.ln

class LocalBeamSemanticProvider(
    private val memory: PredictionMemory
) : PredictionProvider {

    override val id: String = "local-beam-v1"

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
            "mehr" to listOf("details","intelligenz","kontext","möglichkeiten")
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
            "that" to listOf("would","is","we","it")
        ),
        "it" to mapOf(
            "io" to listOf("voglio","penso","posso","vorrei"),
            "voglio" to listOf("continuare","aggiungere","anche","più"),
            "noi" to listOf("possiamo","dovremmo","vogliamo"),
            "possiamo" to listOf("farlo","continuare","anche","ora")
        ),
        "fr" to mapOf(
            "je" to listOf("veux","pense","peux","voudrais"),
            "veux" to listOf("continuer","ajouter","aussi","plus"),
            "nous" to listOf("pouvons","devrions","voulons"),
            "pouvons" to listOf("continuer","faire","aussi","maintenant")
        ),
        "es" to mapOf(
            "yo" to listOf("quiero","pienso","puedo","quisiera"),
            "quiero" to listOf("seguir","añadir","también","más"),
            "nosotros" to listOf("podemos","deberíamos","queremos"),
            "podemos" to listOf("seguir","hacerlo","también","ahora")
        )
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

        val history = extractWords(context.beforeCursor).takeLast(4)
        if (history.isEmpty()) return emptyList()

        val languages = preferredLanguages(context.languageHints)
        val depth = context.maxSemanticTokens.coerceIn(2, 6)

        var beams = listOf(
            Beam(
                history = history,
                generated = emptyList(),
                score = 0.0
            )
        )

        val finished = mutableListOf<Beam>()

        repeat(depth) { step ->
            val expanded = mutableListOf<Beam>()

            for (beam in beams) {
                val candidates = candidatesFor(
                    beam.history,
                    languages,
                    context.inputMode
                )

                candidates.take(10).forEachIndexed { index, candidate ->
                    val nextHistory = (beam.history + candidate).takeLast(4)
                    val personal = memory.boost(beam.history, candidate)
                    val rankPrior = 1.0 / (index + 1.0)
                    val personalPrior = 1.0 + personal / 70.0
                    val modePrior = if (
                        candidate.lowercase() in modeBoosts[context.inputMode].orEmpty()
                    ) 1.45 else 1.0

                    val nextScore = beam.score +
                        ln((rankPrior * personalPrior * modePrior).coerceAtLeast(0.0001))

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
                .sortedByDescending { it.score }
                .take(18)

            if (beams.isEmpty()) return@repeat
        }

        return finished
            .filter { it.generated.size >= 2 }
            .sortedByDescending { (it.score / it.generated.size.coerceAtLeast(1)) + lengthPreference(it.generated.size) }
            .distinctBy { it.generated.joinToString(" ").lowercase() }
            .take(maxSuggestions)
            .mapIndexed { index, beam ->
                val phrase = beam.generated.joinToString(" ")
                PredictionSuggestion(
                    display = "→ $phrase",
                    commitText = phrase,
                    kind = PredictionKind.SENTENCE,
                    confidence = (0.90f - index * 0.07f).coerceAtLeast(0.48f)
                )
            }
    }

    private fun candidatesFor(
        history: List<String>,
        languages: List<String>,
        mode: PredictionInputMode
    ): List<String> {
        val scored = mutableMapOf<String, Int>()

        memory.learnedFollowers(history, limit = 10)
            .forEach { (word, score) ->
                scored[word] = maxOf(scored[word] ?: 0, 1000 + score)
            }

        val last = history.lastOrNull()?.lowercase().orEmpty()

        languages.forEach { language ->
            graph[language]?.get(last).orEmpty().forEachIndexed { index, word ->
                var score = 700 - index * 35
                if (word.lowercase() in modeBoosts[mode].orEmpty()) score += 120
                score += memory.boost(history, word)
                scored[word] = maxOf(scored[word] ?: 0, score)
            }
        }

        return scored.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(12)
    }

    private fun lengthPreference(length: Int): Double =
        when (length) {
            2 -> 0.05
            3 -> 0.12
            4 -> 0.18
            5 -> 0.14
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
            .findAll(text.takeLast(700))
            .map { it.value.lowercase() }
            .toList()
}
