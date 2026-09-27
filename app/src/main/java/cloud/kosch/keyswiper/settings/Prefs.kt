package cloud.kosch.keyswiper.settings

import android.content.Context
import cloud.kosch.keyswiper.ui.KeyboardLayoutProfiles
import java.util.Locale

object Prefs {
    private const val FILE = "keyswiper_prefs"
    private const val TARGET_LANGUAGE = "target_language"
    private const val TARGET_HISTORY = "target_language_history"
    private const val HANDWRITING_LANGUAGE = "handwriting_language"
    private const val SEMANTIC_DEPTH = "semantic_prediction_depth"
    private const val KEYBOARD_LAYOUT = "keyboard_layout"

    fun targetLanguage(context: Context): String =
        context.getSharedPreferences(
            FILE,
            Context.MODE_PRIVATE
        )
            .getString(
                TARGET_LANGUAGE,
                Locale.getDefault().language
            )
            ?.ifBlank {
                Locale.getDefault().language
            }
            ?: Locale.getDefault().language

    fun setTargetLanguage(
        context: Context,
        value: String
    ) {
        val normalized =
            value
                .trim()
                .substringBefore('-')
                .lowercase()
                .ifBlank {
                    Locale.getDefault().language
                }

        val prefs =
            context.getSharedPreferences(
                FILE,
                Context.MODE_PRIVATE
            )

        val history =
            translationTargetHistory(context)
                .filterNot {
                    it.equals(
                        normalized,
                        ignoreCase = true
                    )
                }
                .toMutableList()
                .apply {
                    add(
                        0,
                        normalized
                    )
                }
                .take(8)

        prefs.edit()
            .putString(
                TARGET_LANGUAGE,
                normalized
            )
            .putString(
                TARGET_HISTORY,
                history.joinToString(",")
            )
            .apply()
    }

    fun translationTargetHistory(
        context: Context
    ): List<String> {
        val raw =
            context
                .getSharedPreferences(
                    FILE,
                    Context.MODE_PRIVATE
                )
                .getString(
                    TARGET_HISTORY,
                    ""
                )
                .orEmpty()

        val stored =
            raw.split(',')
                .map { it.trim() }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        val defaults =
            listOf(
                targetLanguage(context),
                "de",
                "en",
                "it",
                "fr",
                "es"
            )

        return (
            stored + defaults
            )
            .distinct()
            .take(8)
    }

    fun handwritingLanguage(
        context: Context
    ): String =
        context
            .getSharedPreferences(
                FILE,
                Context.MODE_PRIVATE
            )
            .getString(
                HANDWRITING_LANGUAGE,
                Locale.getDefault()
                    .toLanguageTag()
            )
            ?.ifBlank {
                Locale.getDefault()
                    .toLanguageTag()
            }
            ?: Locale.getDefault()
                .toLanguageTag()

    fun setHandwritingLanguage(
        context: Context,
        value: String
    ) {
        context
            .getSharedPreferences(
                FILE,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                HANDWRITING_LANGUAGE,
                value.trim()
            )
            .apply()
    }

    fun semanticPredictionDepth(
        context: Context
    ): Int =
        context
            .getSharedPreferences(
                FILE,
                Context.MODE_PRIVATE
            )
            .getInt(
                SEMANTIC_DEPTH,
                5
            )
            .coerceIn(
                2,
                6
            )

    fun setSemanticPredictionDepth(
        context: Context,
        value: Int
    ) {
        context
            .getSharedPreferences(
                FILE,
                Context.MODE_PRIVATE
            )
            .edit()
            .putInt(
                SEMANTIC_DEPTH,
                value.coerceIn(
                    2,
                    6
                )
            )
            .apply()
    }

    fun keyboardLayoutId(
        context: Context
    ): String {
        val fallback =
            KeyboardLayoutProfiles
                .forLanguage(
                    Locale.getDefault().language
                )
                .id

        return context
            .getSharedPreferences(
                FILE,
                Context.MODE_PRIVATE
            )
            .getString(
                KEYBOARD_LAYOUT,
                fallback
            )
            ?.takeIf {
                KeyboardLayoutProfiles
                    .all
                    .any { profile ->
                        profile.id == it
                    }
            }
            ?: fallback
    }

    fun setKeyboardLayoutId(
        context: Context,
        value: String
    ) {
        val profile =
            KeyboardLayoutProfiles
                .byId(value)

        context
            .getSharedPreferences(
                FILE,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEYBOARD_LAYOUT,
                profile.id
            )
            .apply()
    }
}
