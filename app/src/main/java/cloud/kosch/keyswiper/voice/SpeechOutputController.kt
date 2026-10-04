package cloud.kosch.keyswiper.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

class SpeechOutputController(private val context: Context) {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null
    private var generation = 0
    fun speak(text: String, language: String, status: (String) -> Unit) {
        if (text.isBlank()) { status("Bitte zuerst Text schreiben oder markieren."); return }
        val request = ++generation
        val action = action@{
            if (request != generation) return@action
            val speech = engine ?: return@action
            val support = speech.setLanguage(Locale.forLanguageTag(language))
            if (support < TextToSpeech.LANG_AVAILABLE) {
                status("Für diese Sprache fehlt eine Android-Sprachausgabe. Bitte in Android installieren.")
            } else {
                val result = speech.speak(text.take(TextToSpeech.getMaxSpeechInputLength()), TextToSpeech.QUEUE_FLUSH, null, "keyswiper-$request")
                status(if (result == TextToSpeech.SUCCESS) "Text wird vorgelesen …" else "Sprachausgabe konnte nicht gestartet werden.")
            }
        }
        if (ready) { action(); return }
        pending = action
        status("Sprachausgabe wird vorbereitet …")
        if (engine == null) engine = TextToSpeech(context) { result ->
            Handler(Looper.getMainLooper()).post {
                ready = result == TextToSpeech.SUCCESS
                val queued = pending; pending = null
                if (ready) queued?.invoke() else {
                    engine?.shutdown(); engine = null
                    if (request == generation) status("Keine Sprachausgabe verfügbar. Bitte Android-Sprachausgabe prüfen.")
                }
            }
        }
    }
    fun stop() { generation++; pending = null; engine?.stop() }
    fun destroy() { stop(); engine?.shutdown(); engine = null; ready = false }
}
