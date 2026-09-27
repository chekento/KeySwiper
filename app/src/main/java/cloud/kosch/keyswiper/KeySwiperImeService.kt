package cloud.kosch.keyswiper

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import cloud.kosch.keyswiper.clipboard.ClipboardController
import cloud.kosch.keyswiper.handwriting.DigitalInkEngine
import cloud.kosch.keyswiper.input.MotorProfileStore
import cloud.kosch.keyswiper.input.SwipeDecoder
import cloud.kosch.keyswiper.input.SwipeLearningStore
import cloud.kosch.keyswiper.input.SwipeTrace
import cloud.kosch.keyswiper.language.TranslationEngine
import cloud.kosch.keyswiper.security.SecurityPolicy
import cloud.kosch.keyswiper.settings.Prefs
import cloud.kosch.keyswiper.settings.SettingsActivity
import cloud.kosch.keyswiper.ui.HandwritingPadView
import cloud.kosch.keyswiper.ui.KeyboardRootView
import cloud.kosch.keyswiper.voice.VoiceInputController
import java.util.Locale

class KeySwiperImeService : InputMethodService() {
    private val swipeDecoder = SwipeDecoder()
    private val translationEngine = TranslationEngine()
    private val digitalInkEngine = DigitalInkEngine()

    private lateinit var swipeLearningStore: SwipeLearningStore
    private lateinit var motorProfileStore: MotorProfileStore
    private lateinit var clipboardController: ClipboardController
    private lateinit var voiceController: VoiceInputController
    private var root: KeyboardRootView? = null

    private var sensitiveField = false
    private var languageHints: List<String> = emptyList()
    private var lastSwipeWord: String? = null
    private var lastSwipeCandidates: List<String> = emptyList()
    private var lastSwipeSignature: String? = null
    private var lastSwipeContextWord: String = ""
    private var lastSwipeTrace: SwipeTrace? = null

    override fun onCreate() {
        super.onCreate()
        swipeLearningStore = SwipeLearningStore(this)
        motorProfileStore = MotorProfileStore(this)
        clipboardController = ClipboardController(this)
        voiceController = VoiceInputController(this)
        clipboardController.start()
    }

    override fun onCreateInputView(): View =
        KeyboardRootView(this).also { view ->
            root = view
            view.callbacks = callbacks
            refreshPrivacyState()
        }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sensitiveField = SecurityPolicy.isSensitive(attribute)
        languageHints = emptyList()
        clearSwipeState()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        sensitiveField = SecurityPolicy.isSensitive(info)
        refreshPrivacyState()
        if (!sensitiveField) {
            val before = currentInputConnection?.getTextBeforeCursor(300, 0)?.toString().orEmpty()
            refreshLanguageHints(before)
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        voiceController.stop()
        languageHints = emptyList()
        root?.setCandidates(emptyList())
        root?.setStatus(null)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        clipboardController.stop()
        voiceController.destroy()
        translationEngine.close()
        digitalInkEngine.close()
        super.onDestroy()
    }

    private val callbacks = object : KeyboardRootView.Callbacks {
        override fun onCharacter(value: Char) {
            currentInputConnection?.commitText(value.toString(), 1)
            clearSwipeState()
        }

        override fun onSwipe(trace: SwipeTrace) {
            val before = currentInputConnection?.getTextBeforeCursor(300, 0)?.toString().orEmpty()
            val signature = swipeDecoder.signature(trace)
            val previousWord = previousWord(before)

            val values = swipeDecoder.decode(
                trace = trace,
                context = before,
                preferredLanguages = languageHints,
                personalizationBoost = { sig, previous, candidate ->
                    if (sensitiveField) 0
                    else swipeLearningStore.boost(sig, previous, candidate)
                },
                motorOffset = { character ->
                    if (sensitiveField) cloud.kosch.keyswiper.input.KeyOffset()
                    else motorProfileStore.offsetFor(character)
                }
            )
            if (values.isEmpty()) return

            val word = values.first()
            currentInputConnection?.commitText(word + " ", 1)

            lastSwipeWord = word
            lastSwipeCandidates = values
            lastSwipeSignature = signature
            lastSwipeContextWord = previousWord
            lastSwipeTrace = trace
            root?.setCandidates(values)

            refreshLanguageHints((before + " " + word).takeLast(400))
        }

        override fun onBackspace() {
            val swipeWord = lastSwipeWord
            if (swipeWord != null) {
                currentInputConnection?.deleteSurroundingText(swipeWord.length + 1, 0)
                clearSwipeState()
            } else {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
        }

        override fun onSpace() {
            currentInputConnection?.commitText(" ", 1)
            clearSwipeState()
            val before = currentInputConnection?.getTextBeforeCursor(300, 0)?.toString().orEmpty()
            refreshLanguageHints(before)
        }

        override fun onEnter() {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            clearSwipeState()
        }

        override fun onCandidate(value: String) = replaceLastSwipe(value)

        override fun onTranslate() {
            if (sensitiveField) {
                root?.setStatus("Translation is disabled in sensitive fields.")
                return
            }

            val selected = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
            if (selected.isBlank()) {
                root?.setStatus("Select text first, then tap 🌐 to translate.")
                return
            }

            val target = Prefs.targetLanguage(this@KeySwiperImeService)
            root?.setStatus("Translating → " + target + "…")
            translationEngine.translate(selected, target) { result ->
                result.onSuccess { translated ->
                    currentInputConnection?.commitText(translated, 1)
                    root?.setStatus("Translated locally with Google ML Kit.")
                    refreshLanguageHints(translated)
                }.onFailure {
                    root?.setStatus(it.message ?: "Translation failed.")
                }
            }
        }

        override fun onVoice() {
            if (sensitiveField) {
                root?.setStatus("Voice input is disabled in sensitive fields.")
                return
            }

            voiceController.toggle(
                languageTag = Locale.getDefault().toLanguageTag(),
                onPartial = { root?.setStatus(it) },
                onFinal = {
                    currentInputConnection?.commitText(it + " ", 1)
                    root?.setStatus(null)
                    val before = currentInputConnection?.getTextBeforeCursor(300, 0)?.toString().orEmpty()
                    refreshLanguageHints(before)
                },
                onError = { root?.setStatus(it) }
            )
        }

        override fun onClipboard() {
            if (sensitiveField) {
                root?.setStatus("Clipboard history is hidden in sensitive fields.")
                return
            }
            root?.showClipboardPanel(clipboardController.items())
        }

        override fun onEmoji(value: String) {
            currentInputConnection?.commitText(value, 1)
            clearSwipeState()
        }

        override fun onHandwritingRequested() {
            if (sensitiveField) {
                root?.setStatus("Handwriting recognition is disabled in sensitive fields.")
                return
            }

            val language = Prefs.handwritingLanguage(this@KeySwiperImeService)
            root?.setStatus("Preparing handwriting model " + language + "…")
            root?.showHandwritingPanel {
                digitalInkEngine.prepare(language) { result ->
                    result.onSuccess {
                        root?.setStatus("Handwriting ready: " + language)
                    }.onFailure {
                        root?.setStatus(it.message ?: "Handwriting model failed to load.")
                    }
                }
            }
        }

        override fun onHandwritingRecognize(view: HandwritingPadView) {
            if (sensitiveField) return
            val before = currentInputConnection?.getTextBeforeCursor(120, 0)?.toString().orEmpty()

            root?.setStatus("Recognizing handwriting…")
            digitalInkEngine.recognize(
                ink = view.snapshotInk(),
                preContext = before,
                width = view.width.toFloat(),
                height = view.height.toFloat()
            ) { result ->
                result.onSuccess { values ->
                    val best = values.first()
                    currentInputConnection?.commitText(best + " ", 1)
                    root?.setCandidates(values)
                    root?.setStatus(null)
                    view.clearInk()
                    refreshLanguageHints((before + " " + best).takeLast(400))
                }.onFailure {
                    root?.setStatus(it.message ?: "Handwriting recognition failed.")
                }
            }
        }

        override fun onSettings() {
            startActivity(Intent(this@KeySwiperImeService, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        override fun onStylusPrimary() {
            if (sensitiveField) root?.setStatus("S Pen action blocked in sensitive field.")
            else onVoice()
        }

        override fun onStylusSecondary() {
            val word = lastSwipeWord
            if (word != null && lastSwipeCandidates.size > 1) {
                val index = lastSwipeCandidates.indexOf(word).coerceAtLeast(0)
                val next = lastSwipeCandidates[(index + 1) % lastSwipeCandidates.size]
                replaceLastSwipe(next)
            } else {
                root?.setStatus("Secondary stylus action: no alternate candidate.")
            }
        }
    }

    private fun replaceLastSwipe(value: String) {
        val previous = lastSwipeWord ?: return
        val connection = currentInputConnection ?: return
        val signature = lastSwipeSignature
        val trace = lastSwipeTrace

        connection.deleteSurroundingText(previous.length + 1, 0)
        connection.commitText(value + " ", 1)

        if (!sensitiveField && signature != null) {
            swipeLearningStore.record(signature, lastSwipeContextWord, value)
            if (trace != null) {
                motorProfileStore.learn(trace, value)
            }
        }

        lastSwipeWord = value
        root?.setCandidates(lastSwipeCandidates)
        root?.setStatus("Adaptive swipe learned this correction locally.")
    }

    private fun refreshLanguageHints(text: String) {
        if (sensitiveField || text.isBlank()) return
        translationEngine.identifyLikelyLanguages(text) { detected ->
            if (detected.isNotEmpty()) {
                languageHints = detected
            }
        }
    }

    private fun previousWord(context: String): String =
        context.trim().split(Regex("\\s+")).lastOrNull()
            ?.trim { !it.isLetter() && it != '\'' }
            ?.lowercase()
            .orEmpty()

    private fun clearSwipeState() {
        lastSwipeWord = null
        lastSwipeCandidates = emptyList()
        lastSwipeSignature = null
        lastSwipeContextWord = ""
        lastSwipeTrace = null
        root?.setCandidates(emptyList())
    }

    private fun refreshPrivacyState() {
        if (sensitiveField) {
            root?.setStatus("Private field: learning, clipboard, voice and transformations are off.")
        } else {
            root?.setStatus(null)
        }
    }
}
