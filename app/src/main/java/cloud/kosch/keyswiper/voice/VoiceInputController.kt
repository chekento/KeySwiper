package cloud.kosch.keyswiper.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Continuous dictation joins multiple short platform recognizer requests into one session.
 * Command recognition intentionally remains a single request.
 *
 * This controller never stores audio. All callbacks are invalidated when the editor changes.
 */
class VoiceInputController(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0L
    private var active = false
    private var processing = false
    private var manualStop = false
    private var roundFinished = false
    private var speechInProgress = false
    private var continuousSession = false
    private var startedAt = 0L
    private var latestSpeechAt = 0L
    private var transcript = VoiceTranscriptBuffer()
    private var onFinalText: (String) -> Unit = {}
    private var reportError: (String) -> Unit = {}
    private var stateChanged: (String) -> Unit = {}
    private var phaseChanged: (VoicePhase) -> Unit = {}
    private var levelChanged: (Float) -> Unit = {}

    private val finalTimeout = Runnable {
        if (active && processing) {
            if (!transcript.isEmpty) finishSession() else fail("Spracherkennung antwortet nicht. Bitte erneut versuchen.")
        }
    }
    private val pauseTimeout = Runnable {
        if (active && continuousSession && !manualStop && !speechInProgress) {
            if (processing) {
                manualStop = true  // Wait for the current finalized segment.
            } else {
                finishSession()
            }
        }
    }
    private val maxSessionTimeout = Runnable { if (active) stop() }

    fun isListening(): Boolean = active

    fun toggle(languageTag: String = Locale.getDefault().toLanguageTag(),
        onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit,
        onState: (String) -> Unit = {}, localOnly: Boolean = true, biasing: List<String> = emptyList(),
        format: Boolean = true, onPhase: (VoicePhase) -> Unit = {}, onLevel: (Float) -> Unit = {},
        continuous: Boolean = false) {
        if (active) { stop(); return }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("Bitte den Mikrofonzugriff erlauben."); return
        }
        cancel()
        stateChanged = onState; reportError = onError; phaseChanged = onPhase; levelChanged = onLevel
        onFinalText = onFinal
        transcript = VoiceTranscriptBuffer()
        continuousSession = continuous
        startedAt = SystemClock.elapsedRealtime()
        latestSpeechAt = startedAt
        active = true
        val request = generation
        var fallbackUsed = false
        val onDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (localOnly && !onDevice) {
            fail("Lokale Spracherkennung fehlt. Bitte ein Offline-Sprachpaket installieren.")
            return
        }
        if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(context)) {
            fail("Kein Spracherkennungsdienst installiert. Bitte einen Android-Sprachdienst aktivieren.")
            return
        }

        fun stillCurrent() = active && generation == request

        fun awaitFinalResult() {
            if (!stillCurrent() || processing) return
            processing = true
            phaseChanged(VoicePhase.PROCESSING)
            levelChanged(0f)
            stateChanged("Sprache wird umgewandelt …")
            handler.removeCallbacks(finalTimeout)
            handler.postDelayed(finalTimeout, FINAL_TIMEOUT_MS)
        }

        // Recursion is asynchronous and each SpeechRecognizer is used for one utterance.
        lateinit var startRound: (Boolean) -> Unit
        startRound = start@{ local: Boolean ->
            if (!stillCurrent() || manualStop) return@start
            if (SystemClock.elapsedRealtime() - startedAt >= MAX_SESSION_MS || transcript.isFull) {
                finishSession(); return@start
            }
            handler.removeCallbacks(finalTimeout)
            roundFinished = false
            speechInProgress = false
            processing = false
            phaseChanged(VoicePhase.STARTING)
            stateChanged(if (transcript.isEmpty) "Mikrofon wird gestartet …"
                else "Weiter sprechen · zum Beenden tippen")
            try {
                recognizer?.destroy()
                val speech = if (local && Build.VERSION.SDK_INT >= 31)
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                else SpeechRecognizer.createSpeechRecognizer(context)
                recognizer = speech
                speech.setRecognitionListener(object : RecognitionListener {
                    private fun valid() = stillCurrent() && recognizer === speech

                    override fun onReadyForSpeech(params: Bundle?) {
                        if (valid()) {
                            phaseChanged(VoicePhase.LISTENING)
                            stateChanged(if (local) "Lokale Aufnahme · bitte weiter sprechen …"
                                else "Systemdienst · bitte weiter sprechen …")
                        }
                    }
                    override fun onBeginningOfSpeech() {
                        if (!valid()) return
                        speechInProgress = true
                        latestSpeechAt = SystemClock.elapsedRealtime()
                        handler.removeCallbacks(pauseTimeout)
                        phaseChanged(VoicePhase.LISTENING)
                    }
                    override fun onRmsChanged(rmsdB: Float) {
                        if (valid() && !processing)
                            levelChanged(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                    }
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() {
                        if (!valid()) return
                        speechInProgress = false
                        latestSpeechAt = SystemClock.elapsedRealtime()
                        awaitFinalResult()
                    }
                    override fun onPartialResults(results: Bundle?) {
                        if (!valid()) return
                        val partial = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull { it.isNotBlank() }.orEmpty()
                        if (partial.isNotBlank()) {
                            latestSpeechAt = SystemClock.elapsedRealtime()
                            handler.removeCallbacks(pauseTimeout)
                            onPartial(transcript.preview(partial))
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        if (!valid()) return
                        handler.removeCallbacks(finalTimeout)
                        roundFinished = true
                        processing = false
                        speechInProgress = false
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull { it.isNotBlank() }.orEmpty()
                        if (text.isNotBlank()) {
                            transcript.add(text)
                            latestSpeechAt = SystemClock.elapsedRealtime()
                            onPartial(transcript.text)
                        }
                        if (!continuousSession || manualStop || transcript.isFull ||
                            SystemClock.elapsedRealtime() - startedAt >= MAX_SESSION_MS) {
                            finishSession()
                        } else {
                            phaseChanged(VoicePhase.LISTENING)
                            stateChanged("Weiter sprechen · Aufnahme bleibt aktiv")
                            schedulePause(request)
                            handler.postDelayed({ if (valid() && !manualStop) startRound(local) },
                                RESTART_DELAY_MS)
                        }
                    }
                    override fun onError(error: Int) {
                        if (!valid()) return
                        handler.removeCallbacks(finalTimeout)
                        roundFinished = true
                        processing = false
                        speechInProgress = false
                        if (!localOnly && local && !fallbackUsed && transcript.isEmpty &&
                            error in setOf(4, 5, 11, 12, 13) && SpeechRecognizer.isRecognitionAvailable(context)) {
                            fallbackUsed = true
                            stateChanged("Android-Spracherkennung wird verwendet …")
                            handler.postDelayed({ if (stillCurrent() && !manualStop) startRound(false) },
                                RESTART_DELAY_MS)
                            return
                        }
                        val idleError = error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == SpeechRecognizer.ERROR_NO_MATCH
                        if (continuousSession && !manualStop && idleError &&
                            SystemClock.elapsedRealtime() - startedAt < MAX_SESSION_MS &&
                            (!transcript.isEmpty ||
                                SystemClock.elapsedRealtime() - startedAt < INITIAL_WAIT_MS)) {
                            if (!transcript.isEmpty &&
                                SystemClock.elapsedRealtime() - latestSpeechAt >= PAUSE_MS) {
                                finishSession()
                            } else {
                                if (!transcript.isEmpty) schedulePause(request)
                                handler.postDelayed({ if (stillCurrent() && !manualStop) startRound(local) },
                                    RESTART_DELAY_MS)
                            }
                            return
                        }
                        if (!transcript.isEmpty) finishSession()
                        else fail(message(error))
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                speech.startListening(VoiceRecognitionOptions.intent(languageTag, local, biasing, format,
                    continuous = continuousSession))
            } catch (_: RuntimeException) {
                if (!localOnly && local && !fallbackUsed && transcript.isEmpty &&
                    SpeechRecognizer.isRecognitionAvailable(context)) {
                    fallbackUsed = true
                    handler.postDelayed({ if (stillCurrent() && !manualStop) startRound(false) }, RESTART_DELAY_MS)
                } else if (!transcript.isEmpty) finishSession()
                else fail("Mikrofon konnte nicht gestartet werden. Bitte Android-Spracherkennung prüfen.")
            }
        }
        startRound(onDevice)
        handler.postDelayed(maxSessionTimeout, MAX_SESSION_MS)
    }

    private fun schedulePause(request: Long) {
        handler.removeCallbacks(pauseTimeout)
        if (active && continuousSession && request == generation && !transcript.isEmpty)
            handler.postDelayed(pauseTimeout, PAUSE_MS)
    }

    /** Manual finish; if audio is still being recognized, wait for the final segment. */
    fun stop() {
        if (!active) return
        manualStop = true
        handler.removeCallbacks(pauseTimeout)
        if (roundFinished) { finishSession(); return }
        if (processing) { stateChanged("Letzten Satz abschließen …"); return }
        processing = true
        phaseChanged(VoicePhase.PROCESSING)
        levelChanged(0f)
        stateChanged("Letzten Satz umwandeln …")
        handler.removeCallbacks(finalTimeout)
        handler.postDelayed(finalTimeout, FINAL_TIMEOUT_MS)
        runCatching { recognizer?.stopListening() }
    }

    private fun finishSession() {
        if (!active) return
        val result = transcript.text
        cancel()
        stateChanged("Bereit")
        if (result.isNotBlank()) onFinalText(result)
        else reportError("Keine Sprache erkannt. Bitte erneut versuchen.")
    }

    private fun fail(reason: String) {
        cancel()
        stateChanged("Bereit")
        reportError(reason)
    }

    /** Invalidate late callbacks when focus changes or the dictation panel is closed. */
    fun cancel() {
        generation++
        handler.removeCallbacksAndMessages(null)
        active = false; processing = false; manualStop = false
        speechInProgress = false; roundFinished = false; continuousSession = false
        phaseChanged(VoicePhase.IDLE)
        levelChanged(0f)
        runCatching { recognizer?.cancel(); recognizer?.destroy() }
        recognizer = null
    }
    fun destroy() = cancel()

    companion object {
        private const val RESTART_DELAY_MS = 400L
        private const val PAUSE_MS = 9_000L
        private const val INITIAL_WAIT_MS = 20_000L
        private const val MAX_SESSION_MS = 300_000L
        private const val FINAL_TIMEOUT_MS = 15_000L

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
