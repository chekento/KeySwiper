package cloud.kosch.keyswiper.handwriting

import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea

class DigitalInkEngine {
    private var recognizer: DigitalInkRecognizer? = null
    private var activeLanguage: String? = null

    fun prepare(languageTag: String, callback: (Result<Unit>) -> Unit) {
        if (recognizer != null && activeLanguage == languageTag) {
            callback(Result.success(Unit))
            return
        }

        val identifier = findIdentifier(languageTag)
            ?: run {
                callback(Result.failure(UnsupportedOperationException(
                    "No Digital Ink model found for " + languageTag
                )))
                return
            }

        val model = DigitalInkRecognitionModel.builder(identifier).build()
        RemoteModelManager.getInstance()
            .download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener {
                recognizer?.close()
                recognizer = DigitalInkRecognition.getClient(
                    DigitalInkRecognizerOptions.builder(model).build()
                )
                activeLanguage = languageTag
                callback(Result.success(Unit))
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun isReadyFor(languageTag: String): Boolean =
        recognizer != null && activeLanguage == languageTag

    fun recognize(
        ink: Ink,
        preContext: String,
        width: Float,
        height: Float,
        callback: (Result<List<String>>) -> Unit
    ) {
        val current = recognizer
            ?: run {
                callback(Result.failure(IllegalStateException("Handwriting model is not ready yet.")))
                return
            }

        val context = RecognitionContext.builder()
            .setPreContext(preContext.takeLast(20))
            .setWritingArea(WritingArea(width.coerceAtLeast(1f), height.coerceAtLeast(1f)))
            .build()

        current.recognize(ink, context)
            .addOnSuccessListener { result ->
                val candidates = result.candidates.map { it.text }.take(5)
                if (candidates.isEmpty()) {
                    callback(Result.failure(IllegalStateException("No handwriting candidate recognized.")))
                } else {
                    callback(Result.success(candidates))
                }
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }

    private fun findIdentifier(tag: String): DigitalInkRecognitionModelIdentifier? {
        val attempts = listOf(tag, tag.substringBefore('-'), "en-US")
        for (candidate in attempts.distinct()) {
            try {
                val result = DigitalInkRecognitionModelIdentifier.fromLanguageTag(candidate)
                if (result != null) return result
            } catch (_: MlKitException) {
                // Try next fallback.
            }
        }
        return null
    }
}
