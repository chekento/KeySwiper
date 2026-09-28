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
        val threshold =
            when (pointerKind) {
                PointerKind.TOUCH -> 12f
                PointerKind.STYLUS -> 30f
            }

        return displacementDp >= threshold
    }

    fun shouldCommitSwipe(
        sample: GestureIntentSample
    ): Boolean =
        when (sample.pointerKind) {
            PointerKind.TOUCH ->
                sample.displacementDp >= 12f &&
                    sample.pathLengthDp >= 18f &&
                    sample.distinctKeys >= 2

            PointerKind.STYLUS ->
                sample.displacementDp >= 30f &&
                    sample.pathLengthDp >= 48f &&
                    sample.distinctKeys >= 2 &&
                    sample.durationMs >= 40L
        }
}
