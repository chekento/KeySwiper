package cloud.kosch.keyswiper.settings

import android.content.Context
import cloud.kosch.keyswiper.ui.KeyboardLayoutProfiles
import cloud.kosch.keyswiper.ui.KeyboardThemes
import cloud.kosch.keyswiper.ui.OneHandMode
import java.util.Locale

object Prefs {
    private const val FILE = "keyswiper_prefs"
    private const val TARGET_LANGUAGE = "target_language"
    private const val TARGET_HISTORY = "target_language_history"
    private const val HANDWRITING_LANGUAGE = "handwriting_language"
    private const val AUTO_CORRECT = "auto_correct"
    private const val SEMANTIC_DEPTH = "semantic_prediction_depth"
    private const val KEYBOARD_LAYOUT = "keyboard_layout"
    private const val CLIPBOARD_EXPIRY_MINUTES = "clipboard_expiry_minutes"
    private const val ONE_HAND_MODE = "one_hand_mode"
    private const val KEYBOARD_THEME = "keyboard_theme"

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

    fun autoCorrectEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(AUTO_CORRECT, true)

    fun setAutoCorrectEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(AUTO_CORRECT, enabled).apply()
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

    fun clipboardExpiryMinutes(
        context: Context
    ): Long =
        context
            .getSharedPreferences(
                FILE,
                Context.MODE_PRIVATE
            )
            .getLong(
                CLIPBOARD_EXPIRY_MINUTES,
                60L
            )
            .coerceIn(
                10L,
                1440L
            )

    fun setClipboardExpiryMinutes(
        context: Context,
        minutes: Long
    ) {
        context
            .getSharedPreferences(
                FILE,
                Context.MODE_PRIVATE
            )
            .edit()
            .putLong(
                CLIPBOARD_EXPIRY_MINUTES,
                minutes.coerceIn(
                    10L,
                    1440L
                )
            )
            .apply()
    }
    fun oneHandMode(
        context: Context
    ): OneHandMode {
        val stored =
            context
                .getSharedPreferences(
                    FILE,
                    Context.MODE_PRIVATE
                )
                .getString(
                    ONE_HAND_MODE,
                    OneHandMode.OFF.name
                )

        return OneHandMode.entries
            .firstOrNull {
                it.name == stored
            }
            ?: OneHandMode.OFF
    }

    fun setOneHandMode(
        context: Context,
        mode: OneHandMode
    ) {
        context
            .getSharedPreferences(
                FILE,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                ONE_HAND_MODE,
                mode.name
            )
            .apply()
    }
    fun keyboardThemeId(
        context: Context
    ): String =
        context
            .getSharedPreferences(
                FILE,
                Context.MODE_PRIVATE
            )
            .getString(
                KEYBOARD_THEME,
                KeyboardThemes
                    .matrixCyber
                    .id
            )
            ?.takeIf { stored ->
                KeyboardThemes
                    .all
                    .any {
                        it.id ==
                            stored
                    }
            }
            ?: KeyboardThemes
                .matrixCyber
                .id

    fun setKeyboardThemeId(
        context: Context,
        value: String
    ) {
        val theme =
            KeyboardThemes
                .byId(
                    value
                )

        context
            .getSharedPreferences(
                FILE,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEYBOARD_THEME,
                theme.id
            )
            .apply()
    }
}

