package cloud.kosch.keyswiper.prediction

/**
 * Contract for future neural prediction backends such as a downloaded LiteRT-LM model.
 *
 * The keyboard never depends on this interface being available: instant and beam-search
 * providers remain the zero-network fallback.
 */
interface NeuralPredictionBackend {
    val id: String
    fun isReady(): Boolean
    fun predict(
        context: PredictionContext,
        maxSuggestions: Int,
        callback: (List<PredictionSuggestion>) -> Unit
    )
}
