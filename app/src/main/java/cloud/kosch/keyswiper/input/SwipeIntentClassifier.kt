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
        // Word swipes belong to finger input on the letter surface. Pen input
        // on keys is always a tap; handwriting has its own view above the keys.
        return pointerKind == PointerKind.TOUCH && displacementDp >= 12f
    }

    fun shouldCommitSwipe(
        sample: GestureIntentSample
    ): Boolean =
        when (sample.pointerKind) {
            PointerKind.TOUCH ->
                sample.pathLengthDp >= 18f &&
                    sample.distinctKeys >= 2

            PointerKind.STYLUS -> false
        }
}
