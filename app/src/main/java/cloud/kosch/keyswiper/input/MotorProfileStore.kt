package cloud.kosch.keyswiper.input

import android.content.Context

class MotorProfileStore(context: Context) {

    private val preferences =
        context.getSharedPreferences("keyswiper_motor_profile", Context.MODE_PRIVATE)

    fun offsetFor(character: Char): KeyOffset {
        val c = character.lowercaseChar()
        return KeyOffset(
            dx = preferences.getFloat("dx_$c", 0f),
            dy = preferences.getFloat("dy_$c", 0f)
        )
    }

    fun learn(trace: SwipeTrace, correctedWord: String) {
        val observations = SwipeGeometryScorer.estimateLetterOffsets(trace, correctedWord)
        if (observations.isEmpty()) return

        val editor = preferences.edit()

        observations.forEach { (character, observed) ->
            val current = offsetFor(character)
            val samples = preferences.getInt("n_$character", 0)
            val alpha = when {
                samples < 4 -> 0.35f
                samples < 20 -> 0.18f
                else -> 0.08f
            }

            val dx = (current.dx * (1f - alpha) + observed.dx * alpha)
                .coerceIn(-0.08f, 0.08f)
            val dy = (current.dy * (1f - alpha) + observed.dy * alpha)
                .coerceIn(-0.08f, 0.08f)

            editor
                .putFloat("dx_$character", dx)
                .putFloat("dy_$character", dy)
                .putInt("n_$character", (samples + 1).coerceAtMost(10_000))
        }

        editor.apply()
    }

    fun reset() {
        preferences.edit().clear().apply()
    }
}
