package cloud.kosch.keyswiper.voice

import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import java.util.Locale

enum class VoicePhase { IDLE, STARTING, LISTENING, PROCESSING }

object VoiceRecognitionOptions {
    fun languageTag(tag: String): String {
        val requested = Locale.forLanguageTag(tag)
        if (requested.country.isNotBlank()) return requested.toLanguageTag()
        val device = Locale.getDefault()
        if (device.language == requested.language && device.country.isNotBlank()) return device.toLanguageTag()
        return when (requested.language) {
            "de" -> "de-DE"; "en" -> "en-US"; "fr" -> "fr-FR"; "it" -> "it-IT"; "es" -> "es-ES"
            else -> requested.toLanguageTag()
        }
    }
    fun intent(tag: String, local: Boolean, biasing: List<String>, format: Boolean,
        continuous: Boolean = false): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag(tag))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, local)
            if (continuous) {
                // OEM recognizers may ignore these hints. The controller re-arms on each result.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_BIASING_DEVICE_CONTEXT, false)
                if (format) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
                    putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true)
                }
                // Personal words are supplied only to the explicitly local backend.
                if (local && biasing.isNotEmpty()) putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS,
                    ArrayList(biasing.distinct().take(30)))
            }
        }
}
