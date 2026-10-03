package cloud.kosch.keyswiper.input

enum class PointerKind {
    TOUCH,
    STYLUS
}

data class GestureIntentSample(
    val pointerKind: PointerKind,
    val displacementDp: Float,
    val pathLengthDp: Float,
    val distinctKeys: Int,
    val durationMs: Long
)

object SwipeIntentClassifier {
    fun shouldStartDrag(
        pointerKind: PointerKind,
        displacementDp: Float
    ): Boolean {
        // A small pen-specific dead zone keeps tip jitter from starting a word.
        // Once deliberate movement begins, both tools use the same decoder.
        val threshold = if (pointerKind == PointerKind.STYLUS) 16f else 12f
        return displacementDp >= threshold
    }

    fun shouldCommitSwipe(
        sample: GestureIntentSample
    ): Boolean =
        when (sample.pointerKind) {
            PointerKind.TOUCH ->
                sample.pathLengthDp >= 18f &&
                    sample.distinctKeys >= 2

            PointerKind.STYLUS ->
                sample.pathLengthDp >= 24f &&
                    sample.distinctKeys >= 2
        }
}
