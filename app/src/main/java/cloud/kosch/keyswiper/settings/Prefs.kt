package cloud.kosch.keyswiper.settings

import android.content.Context
import java.util.Locale

object Prefs {
    private const val FILE = "keyswiper_prefs"
    private const val TARGET_LANGUAGE = "target_language"
    private const val HANDWRITING_LANGUAGE = "handwriting_language"

    fun targetLanguage(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(TARGET_LANGUAGE, Locale.getDefault().language)
            ?.ifBlank { Locale.getDefault().language }
            ?: Locale.getDefault().language

    fun setTargetLanguage(context: Context, value: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(TARGET_LANGUAGE, value.trim()).apply()
    }

    fun handwritingLanguage(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(HANDWRITING_LANGUAGE, Locale.getDefault().toLanguageTag())
            ?.ifBlank { Locale.getDefault().toLanguageTag() }
            ?: Locale.getDefault().toLanguageTag()

    fun setHandwritingLanguage(context: Context, value: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(HANDWRITING_LANGUAGE, value.trim()).apply()
    }
}
