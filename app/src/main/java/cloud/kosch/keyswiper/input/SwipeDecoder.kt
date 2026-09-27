package cloud.kosch.keyswiper.input

import kotlin.math.abs

class SwipeDecoder {

    private data class Lexeme(
        val word: String,
        val languages: Set<String>
    )

    private fun lexeme(word: String, vararg languages: String) =
        Lexeme(word, languages.map { it.lowercase() }.toSet())

    private val lexicon = listOf(
        // English
        lexeme("the", "en"), lexeme("this", "en"), lexeme("that", "en"),
        lexeme("there", "en"), lexeme("three", "en"), lexeme("their", "en"),
        lexeme("hello", "en"), lexeme("world", "en"), lexeme("keyboard", "en"),
        lexeme("swipe", "en"), lexeme("android", "en"), lexeme("smart", "en"),
        lexeme("phone", "en"), lexeme("text", "en"), lexeme("message", "en"),
        lexeme("please", "en"), lexeme("thanks", "en"), lexeme("thank", "en"),
        lexeme("good", "en"), lexeme("great", "en"), lexeme("today", "en"),
        lexeme("tomorrow", "en"), lexeme("later", "en"), lexeme("yes", "en"),
        lexeme("no", "en", "es", "it"), lexeme("love", "en"),
        lexeme("work", "en"), lexeme("with", "en"), lexeme("from", "en"),
        lexeme("you", "en"), lexeme("we", "en"), lexeme("can", "en"),
        lexeme("want", "en"), lexeme("need", "en"), lexeme("have", "en"),
        lexeme("very", "en"), lexeme("more", "en"), lexeme("later", "en"),

        // German
        lexeme("und", "de"), lexeme("der", "de"), lexeme("die", "de"),
        lexeme("das", "de"), lexeme("ein", "de"), lexeme("eine", "de"),
        lexeme("einer", "de"), lexeme("ist", "de"), lexeme("sind", "de"),
        lexeme("ich", "de"), lexeme("du", "de"), lexeme("wir", "de"),
        lexeme("ihr", "de"), lexeme("bitte", "de"), lexeme("danke", "de"),
        lexeme("hallo", "de"), lexeme("heute", "de"), lexeme("morgen", "de"),
        lexeme("später", "de"), lexeme("sehr", "de"), lexeme("gut", "de"),
        lexeme("super", "de"), lexeme("tastatur", "de"), lexeme("nachricht", "de"),
        lexeme("schreiben", "de"), lexeme("liebe", "de"), lexeme("arbeit", "de"),
        lexeme("mit", "de"), lexeme("von", "de"), lexeme("für", "de"),
        lexeme("auf", "de"), lexeme("nicht", "de"), lexeme("auch", "de"),
        lexeme("kann", "de"), lexeme("können", "de"), lexeme("möchte", "de"),
        lexeme("mehr", "de"), lexeme("jetzt", "de"), lexeme("weiter", "de"),

        // Italian
        lexeme("ciao", "it"), lexeme("grazie", "it"), lexeme("prego", "it"),
        lexeme("buono", "it"), lexeme("bene", "it"), lexeme("oggi", "it"),
        lexeme("domani", "it"), lexeme("dopo", "it"), lexeme("amore", "it"),
        lexeme("lavoro", "it"), lexeme("messaggio", "it"), lexeme("scrivere", "it"),
        lexeme("tastiera", "it"), lexeme("telefono", "it"), lexeme("con", "it"),
        lexeme("per", "it"), lexeme("sono", "it"), lexeme("siamo", "it"),
        lexeme("voglio", "it"), lexeme("posso", "it"), lexeme("molto", "it"),

        // French
        lexeme("bonjour", "fr"), lexeme("merci", "fr"), lexeme("salut", "fr"),
        lexeme("bien", "fr"), lexeme("aujourd'hui", "fr"), lexeme("demain", "fr"),
        lexeme("plus", "fr"), lexeme("amour", "fr"), lexeme("travail", "fr"),
        lexeme("message", "fr", "en"), lexeme("écrire", "fr"),
        lexeme("clavier", "fr"), lexeme("téléphone", "fr"), lexeme("avec", "fr"),
        lexeme("pour", "fr"), lexeme("nous", "fr"), lexeme("vous", "fr"),
        lexeme("je", "fr"), lexeme("peux", "fr"), lexeme("veux", "fr"),

        // Spanish
        lexeme("hola", "es"), lexeme("gracias", "es"), lexeme("bien", "es", "fr"),
        lexeme("hoy", "es"), lexeme("mañana", "es"), lexeme("luego", "es"),
        lexeme("amor", "es"), lexeme("trabajo", "es"), lexeme("mensaje", "es"),
        lexeme("escribir", "es"), lexeme("teclado", "es"), lexeme("teléfono", "es"),
        lexeme("con", "es", "it"), lexeme("para", "es"), lexeme("somos", "es"),
        lexeme("quiero", "es"), lexeme("puedo", "es"), lexeme("mucho", "es"),
        lexeme("ahora", "es"), lexeme("más", "es")
    )

    fun signature(trace: List<Char>): String =
        compactTrace(trace).joinToString("")

    fun decode(
        trace: List<Char>,
        context: String = "",
        preferredLanguages: List<String> = emptyList(),
        personalizationBoost: (signature: String, previousWord: String, candidate: String) -> Int =
            { _, _, _ -> 0 }
    ): List<String> {
        val compact = compactTrace(trace)
        if (compact.isEmpty()) return emptyList()

        val sentenceStart = isSentenceStart(context)
        if (compact.size == 1) {
            val single = compact.first().toString()
            return listOf(applyCase(single, sentenceStart))
        }

        val sig = compact.joinToString("")
        val previousWord = previousWord(context)
        val first = compact.first()
        val last = compact.last()

        val ranked = lexicon.asSequence()
            .filter { abs(it.word.length - sig.length) <= maxOf(5, sig.length / 2 + 1) }
            .map { item ->
                val word = item.word
                val distance = levenshtein(sig, word)
                val coverage = orderedCoverage(compact, word)
                val endpointPenalty =
                    (if (word.firstOrNull() == first) 0 else 6) +
                    (if (word.lastOrNull() == last) 0 else 5)
                val lengthPenalty = abs(word.length - sig.length) * 2
                val languageBoost = languageBoost(item.languages, preferredLanguages)
                val contextBoost = bigramBoost(previousWord, word)
                val learnedBoost = personalizationBoost(sig, previousWord, word).coerceIn(0, 24)

                CandidateScore(
                    word = word,
                    score = distance * 7 +
                        lengthPenalty +
                        endpointPenalty -
                        coverage * 2 -
                        languageBoost * 4 -
                        contextBoost * 5 -
                        learnedBoost * 6
                )
            }
            .sortedBy { it.score }
            .distinctBy { it.word }
            .take(5)
            .map { applyCase(it.word, sentenceStart) }
            .toMutableList()

        val raw = applyCase(sig, sentenceStart)
        if (ranked.isEmpty()) ranked.add(raw)
        else if (ranked.none { it.equals(raw, ignoreCase = true) }) ranked.add(raw)

        return ranked.take(5)
    }

    private data class CandidateScore(val word: String, val score: Int)

    private fun compactTrace(trace: List<Char>): List<Char> =
        trace.map { it.lowercaseChar() }.fold(mutableListOf()) { acc, c ->
            if (acc.lastOrNull() != c) acc.add(c)
            acc
        }

    private fun previousWord(context: String): String =
        context.trim().split(Regex("\\s+")).lastOrNull()
            ?.trim { !it.isLetter() && it != '\'' }
            ?.lowercase()
            .orEmpty()

    private fun isSentenceStart(context: String): Boolean {
        val trimmed = context.trimEnd()
        return trimmed.isEmpty() || trimmed.last() in ".!?\n"
    }

    private fun applyCase(word: String, sentenceStart: Boolean): String =
        if (sentenceStart && word.isNotEmpty()) {
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            word
        }

    private fun languageBoost(
        candidateLanguages: Set<String>,
        preferredLanguages: List<String>
    ): Int {
        val normalized = preferredLanguages.map { it.substringBefore('-').lowercase() }
        val index = normalized.indexOfFirst { it in candidateLanguages }
        return if (index < 0) 0 else (4 - index).coerceAtLeast(1)
    }

    private fun orderedCoverage(trace: List<Char>, word: String): Int {
        var wordIndex = 0
        var score = 0
        for (c in trace) {
            while (wordIndex < word.length && word[wordIndex] != c) {
                wordIndex++
            }
            if (wordIndex < word.length) {
                score++
                wordIndex++
            }
        }
        return score
    }

    private fun bigramBoost(previous: String, word: String): Int =
        when (previous to word) {
            "thank" to "you" -> 5
            "see" to "you" -> 4
            "good" to "morning" -> 5
            "danke" to "dir" -> 5
            "guten" to "morgen" -> 5
            "gute" to "nacht" -> 5
            "bis" to "später" -> 4
            "a" to "domani" -> 4
            "buon" to "giorno" -> 5
            "à" to "demain" -> 4
            "buenos" to "días" -> 5
            else -> 0
        }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
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
