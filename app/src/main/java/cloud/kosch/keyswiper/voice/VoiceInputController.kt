package cloud.kosch.keyswiper.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceInputController(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    fun isListening(): Boolean = listening

    fun toggle(
        languageTag: String = Locale.getDefault().toLanguageTag(),
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (listening) {
            stop()
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onError("Grant microphone permission in KeySwiper settings.")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("No speech recognition service is available on this device.")
            return
        }

        destroyRecognizer()
        recognizer = try {
            if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (_: Throwable) {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                onPartial("Listening…")
            }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                onError("Voice recognition error: " + error)
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onFinal(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onPartial(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        }
        listening = true
        recognizer?.startListening(intent)
    }

    fun stop() {
        if (listening) {
            recognizer?.stopListening()
            listening = false
        }
    }

    fun destroy() {
        stop()
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }
}
