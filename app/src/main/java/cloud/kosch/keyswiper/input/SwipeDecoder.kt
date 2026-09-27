package cloud.kosch.keyswiper.input

class SwipeDecoder {
    private val lexicon = setOf(
        "the", "this", "that", "there", "their", "hello", "world", "keyboard", "swipe",
        "android", "smart", "phone", "text", "message", "please", "thanks", "thank", "good",
        "great", "today", "tomorrow", "later", "yes", "no", "love", "work", "with", "from",
        "und", "der", "die", "das", "ein", "eine", "einer", "ist", "sind", "ich", "du",
        "wir", "ihr", "bitte", "danke", "hallo", "heute", "morgen", "später", "sehr",
        "gut", "super", "tastatur", "nachricht", "schreiben", "liebe", "arbeit", "mit",
        "von", "für", "auf", "nicht", "auch", "kann", "können", "möchte"
    )

    fun decode(trace: List<Char>, context: String = ""): List<String> {
        val compact = trace.map { it.lowercaseChar() }.fold(mutableListOf<Char>()) { acc, c ->
            if (acc.lastOrNull() != c) acc.add(c)
            acc
        }
        if (compact.isEmpty()) return emptyList()
        if (compact.size == 1) return listOf(compact.first().toString())

        val first = compact.first()
        val last = compact.last()
        val previousWord = context.trim().split(Regex("\\s+")).lastOrNull().orEmpty().lowercase()

        val candidates = lexicon.asSequence()
            .filter { it.firstOrNull() == first && it.lastOrNull() == last }
            .map { word ->
                val coverage = orderedCoverage(compact, word)
                val distance = levenshtein(compact.joinToString(""), word)
                val contextBoost = bigramBoost(previousWord, word)
                Triple(word, distance - coverage - contextBoost, coverage)
            }
            .sortedWith(compareBy<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third })
            .map { it.first }
            .take(5)
            .toMutableList()

        val raw = compact.joinToString("")
        if (candidates.isEmpty()) candidates.add(raw)
        else if (!candidates.contains(raw)) candidates.add(raw)
        return candidates.take(5)
    }

    private fun orderedCoverage(trace: List<Char>, word: String): Int {
        var index = 0
        var score = 0
        for (c in trace) {
            if (index < word.length && c == word[index]) {
                index++
                score += 2
            }
        }
        return if (index == word.length) score + 4 else score
    }

    private fun bigramBoost(previous: String, word: String): Int =
        when (previous to word) {
            "thank" to "you", "danke" to "dir", "guten" to "morgen",
            "gute" to "nacht", "see" to "you" -> 4
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
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
