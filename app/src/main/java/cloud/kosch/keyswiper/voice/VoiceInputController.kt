package cloud.kosch.keyswiper.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceInputController(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var processing = false
    private var generation = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var stateChanged: (String) -> Unit = {}
    private var reportError: (String) -> Unit = {}
    private val finalTimeout = Runnable {
        if (active && processing) {
            cancel()
            stateChanged("Bereit")
            reportError("Der Spracherkennungsdienst antwortet nicht. Bitte erneut versuchen.")
        }
    }
    private fun awaitFinalResult() {
        processing = true
        stateChanged("Sprache wird umgewandelt …")
        handler.removeCallbacks(finalTimeout)
        handler.postDelayed(finalTimeout, 15_000)
    }
    fun isListening(): Boolean = active

    fun toggle(languageTag: String = Locale.getDefault().toLanguageTag(),
        onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit,
        onState: (String) -> Unit = {}) {
        if (active) { stop(); return }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("Bitte den Mikrofonzugriff erlauben."); return
        }
        cancel()
        stateChanged = onState
        reportError = onError
        val request = generation
        var fallbackUsed = false
        val onDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Kein Spracherkennungsdienst installiert. Bitte einen Dienst in den Android-Spracheinstellungen aktivieren."); return
        }
        fun start(local: Boolean) {
            if (request != generation) return
            active = true; processing = false
            stateChanged("Mikrofon wird gestartet …")
            try {
                recognizer?.destroy()
                val speech = if (local && Build.VERSION.SDK_INT >= 31)
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
                recognizer = speech
                speech.setRecognitionListener(object : RecognitionListener {
                    private fun valid() = request == generation && recognizer === speech && active
                    override fun onReadyForSpeech(params: Bundle?) { if (valid()) stateChanged("Ich höre zu …") }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() { if (valid()) awaitFinalResult() }
                    override fun onError(error: Int) {
                        if (!valid()) return
                        handler.removeCallbacks(finalTimeout)
                        if (local && !fallbackUsed && error in setOf(4, 5, 11, 12, 13) && SpeechRecognizer.isRecognitionAvailable(context)) {
                            fallbackUsed = true
                            stateChanged("System-Spracherkennung wird verwendet …")
                            handler.post { start(false) }
                            return
                        }
                        active = false; processing = false
                        stateChanged("Bereit")
                        onError(message(error))
                    }
                    override fun onResults(results: Bundle?) {
                        if (!valid()) return
                        handler.removeCallbacks(finalTimeout)
                        active = false; processing = false
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull { it.isNotBlank() }.orEmpty()
                        stateChanged("Bereit")
                        if (text.isNotBlank()) onFinal(text) else onError("Keine Sprache erkannt. Bitte erneut versuchen.")
                    }
                    override fun onPartialResults(results: Bundle?) {
                        if (!valid()) return
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onPartial)
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                })
            } catch (_: RuntimeException) {
                if (local && !fallbackUsed && SpeechRecognizer.isRecognitionAvailable(context)) {
                    fallbackUsed = true; handler.post { start(false) }
                } else {
                    active = false; processing = false
                    stateChanged("Bereit")
                    onError("Mikrofon konnte nicht gestartet werden. Bitte Berechtigung und Android-Spracherkennung prüfen.")
                }
            }
        }
        start(onDevice)
    }

    /** Stop recording, but keep the request alive until its final result arrives. */
    fun stop() {
        if (active && !processing) {
            awaitFinalResult()
            runCatching { recognizer?.stopListening() }
        }
    }
    /** Cancel invalidates callbacks, so old dictation cannot enter another editor. */
    fun cancel() {
        generation++
        handler.removeCallbacksAndMessages(null)
        active = false; processing = false
        runCatching { recognizer?.cancel(); recognizer?.destroy() }
        recognizer = null
    }
    fun destroy() = cancel()

    companion object {
        fun message(error: Int): String = when (error) {
            1, 2 -> "Netzwerk nicht erreichbar. Offline-Sprachpaket prüfen oder erneut versuchen."
            3 -> "Mikrofon ist nicht verfügbar oder wird bereits verwendet."
            6, 7 -> "Keine Sprache erkannt. Bitte erneut auf das Mikrofon tippen."
            8 -> "Spracherkennung ist noch beschäftigt. Bitte kurz warten."
            9 -> "Mikrofonzugriff fehlt. Bitte in den App-Berechtigungen erlauben."
            12, 13 -> "Das Sprachpaket fehlt. Bitte die gewählte Sprache in Android installieren."
            else -> "Spracherkennung konnte nicht abgeschlossen werden. Bitte erneut versuchen."
        }
    }
}
