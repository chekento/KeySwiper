package cloud.kosch.keyswiper.input

import android.content.Context

class SwipeLearningStore(context: Context) {

    private val preferences =
        context.getSharedPreferences("keyswiper_swipe_learning", Context.MODE_PRIVATE)

    fun boost(signature: String, previousWord: String, candidate: String): Int {
        val exact = preferences.getInt(key(signature, previousWord, candidate), 0)
        val global = preferences.getInt(key(signature, "*", candidate), 0)
        return (exact * 3 + global).coerceAtMost(24)
    }

    fun record(signature: String, previousWord: String, candidate: String) {
        if (signature.isBlank() || candidate.isBlank()) return

        val normalizedCandidate = candidate.lowercase()
        val exactKey = key(signature, previousWord, normalizedCandidate)
        val globalKey = key(signature, "*", normalizedCandidate)

        val exact = (preferences.getInt(exactKey, 0) + 1).coerceAtMost(100)
        val global = (preferences.getInt(globalKey, 0) + 1).coerceAtMost(100)

        preferences.edit()
            .putInt(exactKey, exact)
            .putInt(globalKey, global)
            .apply()
    }

    fun reset() {
        preferences.edit().clear().apply()
    }

    private fun key(signature: String, previousWord: String, candidate: String): String =
        listOf(
            signature.lowercase().take(40),
            previousWord.lowercase().take(40),
            candidate.lowercase().take(40)
        ).joinToString("\u001f")
}
