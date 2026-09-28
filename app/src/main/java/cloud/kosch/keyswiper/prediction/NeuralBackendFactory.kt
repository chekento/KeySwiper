package cloud.kosch.keyswiper.prediction

object NeuralBackendFactory {
    fun create(
        modelManager: NeuralModelManager
    ): NeuralPredictionBackend =
        LiteRtLmPredictionBackend(
            modelManager
        )

    fun close(
        backend: NeuralPredictionBackend?
    ) {
        (backend as? AutoCloseable)
            ?.close()
    }
}
