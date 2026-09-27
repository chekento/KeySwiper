package cloud.kosch.keyswiper.prediction

import android.content.Context

class PredictionLearningStore(context: Context) : PredictionMemory {

    private val preferences =
        context.getSharedPreferences("keyswiper_prediction_learning", Context.MODE_PRIVATE)

    fun learnTransition(words: List<String>) {
        val normalized = words
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .takeLast(5)

        if (normalized.size < 2) return

        val next = normalized.last()
        val previous = normalized[normalized.lastIndex - 1]
        increment("b|$previous|$next")

        if (normalized.size >= 3) {
            val previous2 = normalized[normalized.lastIndex - 2]
            increment("t|$previous2|$previous|$next")
        }

        if (normalized.size >= 4) {
            val previous3 = normalized[normalized.lastIndex - 3]
            val previous2 = normalized[normalized.lastIndex - 2]
            increment("q|$previous3|$previous2|$previous|$next")
        }

        increment("u|$next")
        pruneIfNeeded()
    }

    fun learnChosenSuggestion(contextWords: List<String>, suggestion: String) {
        val chosenWords = suggestion
            .split(Regex("\\s+"))
            .map { normalize(it) }
            .filter { it.isNotBlank() }

        if (chosenWords.isEmpty()) return

        var history = contextWords
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .takeLast(4)

        for (word in chosenWords) {
            learnTransition(history + word)
            history = (history + word).takeLast(4)
            increment("chosen|$word", 2)
        }
    }

    override fun boost(contextWords: List<String>, candidate: String): Int {
        val word = normalize(candidate.substringBefore(' '))
        if (word.isBlank()) return 0

        val normalized = contextWords.map { normalize(it) }.filter { it.isNotBlank() }
        val previous = normalized.lastOrNull().orEmpty()
        val previous2 = normalized.dropLast(1).lastOrNull().orEmpty()
        val previous3 = normalized.dropLast(2).lastOrNull().orEmpty()

        var score = preferences.getInt("u|$word", 0).coerceAtMost(12)
        score += preferences.getInt("chosen|$word", 0).coerceAtMost(18)

        if (previous.isNotBlank()) {
            score += preferences.getInt("b|$previous|$word", 0).coerceAtMost(30) * 2
        }

        if (previous2.isNotBlank() && previous.isNotBlank()) {
            score += preferences.getInt(
                "t|$previous2|$previous|$word",
                0
            ).coerceAtMost(30) * 4
        }

        if (previous3.isNotBlank() && previous2.isNotBlank() && previous.isNotBlank()) {
            score += preferences.getInt(
                "q|$previous3|$previous2|$previous|$word",
                0
            ).coerceAtMost(30) * 7
        }

        return score.coerceAtMost(260)
    }

    override fun learnedFollowers(
        contextWords: List<String>,
        limit: Int
    ): List<Pair<String, Int>> {
        val normalized = contextWords.map { normalize(it) }.filter { it.isNotBlank() }
        val previous = normalized.lastOrNull().orEmpty()
        val previous2 = normalized.dropLast(1).lastOrNull().orEmpty()
        val previous3 = normalized.dropLast(2).lastOrNull().orEmpty()

        if (previous.isBlank()) return emptyList()

        val results = mutableMapOf<String, Int>()

        preferences.all.forEach { (key, value) ->
            val count = value as? Int ?: return@forEach

            when {
                previous3.isNotBlank() &&
                    key.startsWith("q|$previous3|$previous2|$previous|") -> {
                    val candidate = key.substringAfterLast('|')
                    results[candidate] = maxOf(results[candidate] ?: 0, count * 9)
                }

                previous2.isNotBlank() &&
                    key.startsWith("t|$previous2|$previous|") -> {
                    val candidate = key.substringAfterLast('|')
                    results[candidate] = maxOf(results[candidate] ?: 0, count * 5)
                }

                key.startsWith("b|$previous|") -> {
                    val candidate = key.substringAfterLast('|')
                    results[candidate] = maxOf(results[candidate] ?: 0, count * 3)
                }
            }
        }

        return results.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }

    fun reset() {
        preferences.edit().clear().apply()
    }

    private fun increment(key: String, amount: Int = 1) {
        val next = (preferences.getInt(key, 0) + amount).coerceAtMost(10_000)
        preferences.edit().putInt(key, next).apply()
    }

    private fun pruneIfNeeded() {
        if (preferences.all.size <= 4000) return

        val removable = preferences.all
            .mapNotNull { (key, value) -> (value as? Int)?.let { key to it } }
            .sortedBy { it.second }
            .take(600)

        val editor = preferences.edit()
        removable.forEach { editor.remove(it.first) }
        editor.apply()
    }

    private fun normalize(value: String): String =
        value.lowercase().trim { !it.isLetterOrDigit() && it != '\'' && it != '-' }
}
