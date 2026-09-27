package cloud.kosch.keyswiper

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import cloud.kosch.keyswiper.clipboard.ClipboardController
import cloud.kosch.keyswiper.handwriting.DigitalInkEngine
import cloud.kosch.keyswiper.input.KeyOffset
import cloud.kosch.keyswiper.input.MotorProfileStore
import cloud.kosch.keyswiper.input.SwipeDecoder
import cloud.kosch.keyswiper.input.SwipeLearningStore
import cloud.kosch.keyswiper.input.SwipeTrace
import cloud.kosch.keyswiper.language.CodeSwitchLanguageResolver
import cloud.kosch.keyswiper.language.TranslationEngine
import cloud.kosch.keyswiper.language.UserVocabularyStore
import cloud.kosch.keyswiper.prediction.ContextPredictionEngine
import cloud.kosch.keyswiper.prediction.HybridPredictionEngine
import cloud.kosch.keyswiper.prediction.LiteRtLmPredictionBackend
import cloud.kosch.keyswiper.prediction.LocalBeamSemanticProvider
import cloud.kosch.keyswiper.prediction.NeuralModelManager
import cloud.kosch.keyswiper.prediction.PredictionContext
import cloud.kosch.keyswiper.prediction.PredictionContextClassifier
import cloud.kosch.keyswiper.prediction.PredictionInputMode
import cloud.kosch.keyswiper.prediction.PredictionLearningStore
import cloud.kosch.keyswiper.prediction.PredictionSuggestion
import cloud.kosch.keyswiper.prediction.SurroundingContextReader
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
    private val surroundingContextReader = SurroundingContextReader()

    private lateinit var swipeLearningStore: SwipeLearningStore
    private lateinit var motorProfileStore: MotorProfileStore
    private lateinit var predictionLearningStore: PredictionLearningStore
    private lateinit var userVocabularyStore: UserVocabularyStore
    private lateinit var predictionEngine: HybridPredictionEngine
    private lateinit var neuralModelManager: NeuralModelManager
    private lateinit var neuralPredictionBackend: LiteRtLmPredictionBackend
    private lateinit var clipboardController: ClipboardController
    private lateinit var voiceController: VoiceInputController

    private var root: KeyboardRootView? = null
    private var sensitiveField = false
    private var languageHints: List<String> = emptyList()
    private var predictionInputMode = PredictionInputMode.GENERAL
    private var predictionGeneration = 0L

    private var lastSwipeWord: String? = null
    private var lastSwipeCandidates: List<String> = emptyList()
    private var lastSwipeSignature: String? = null
    private var lastSwipeContextWord: String = ""
    private var lastSwipeTrace: SwipeTrace? = null

    override fun onCreate() {
        super.onCreate()

        swipeLearningStore = SwipeLearningStore(this)
        motorProfileStore = MotorProfileStore(this)
        predictionLearningStore = PredictionLearningStore(this)
        userVocabularyStore = UserVocabularyStore(this)

        val instantPrediction = ContextPredictionEngine(
            predictionLearningStore,
            userVocabularyStore
        )
        val semanticPrediction = LocalBeamSemanticProvider(predictionLearningStore)
        predictionEngine = HybridPredictionEngine(
            instant = instantPrediction,
            semantic = semanticPrediction
        )

        neuralModelManager = NeuralModelManager(this)
        neuralPredictionBackend = LiteRtLmPredictionBackend(neuralModelManager)

        clipboardController = ClipboardController(this)
        voiceController = VoiceInputController(this)
        clipboardController.start()
    }

    override fun onCreateInputView(): View =
        KeyboardRootView(this).also { view ->
            root = view
            view.callbacks = callbacks
            refreshPrivacyState()
            refreshPredictionBar()
        }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sensitiveField = SecurityPolicy.isSensitive(attribute)
        predictionInputMode = PredictionContextClassifier.classify(attribute)
        languageHints = emptyList()
        predictionGeneration++
        clearSwipeState()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        sensitiveField = SecurityPolicy.isSensitive(info)
        predictionInputMode = PredictionContextClassifier.classify(info)
        refreshPrivacyState()

        if (sensitiveField) {
            predictionGeneration++
            root?.clearSuggestions()
        } else {
            val snapshot = currentContextSnapshot()
            refreshLanguageHints(snapshot.beforeCursor)
            refreshPredictionBar()
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        predictionGeneration++
        voiceController.stop()
        languageHints = emptyList()
        predictionInputMode = PredictionInputMode.GENERAL
        clearSwipeState()
        root?.clearSuggestions()
        root?.setStatus(null)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        predictionGeneration++
        neuralPredictionBackend.close()
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
            refreshPredictionBar()
        }

        override fun onSwipe(trace: SwipeTrace) {
            val before = textBeforeCursor()
            val signature = swipeDecoder.signature(trace)
            val previousWord = previousWord(before)

            val activeLanguageLanes = CodeSwitchLanguageResolver.resolve(
                contextText = before,
                detectedLanguages = languageHints,
                currentToken = "",
                maxLanes = 3
            )

            val values = swipeDecoder.decode(
                trace = trace,
                context = before,
                preferredLanguages = activeLanguageLanes.map { it.tag },
                personalizationBoost = { sig, previous, candidate ->
                    if (sensitiveField) 0
                    else swipeLearningStore.boost(sig, previous, candidate)
                },
                motorOffset = { character ->
                    if (sensitiveField) KeyOffset()
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

            predictionGeneration++
            root?.setSwipeCandidates(values)
            refreshLanguageHints((before + " " + word).takeLast(1000))
        }

        override fun onBackspace() {
            val swipeWord = lastSwipeWord

            if (swipeWord != null) {
                currentInputConnection?.deleteSurroundingText(
                    swipeWord.length + 1,
                    0
                )
                clearSwipeState()
            } else {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }

            refreshPredictionBar()
        }

        override fun onSpace() {
            currentInputConnection?.commitText(" ", 1)
            clearSwipeState()

            val before = textBeforeCursor()
            if (!sensitiveField) {
                val words = extractWords(before)
                predictionLearningStore.learnTransition(
                    words.takeLast(5)
                )
                words.lastOrNull()?.let { committedWord ->
                    userVocabularyStore.observeWord(
                        committedWord,
                        languageHints
                    )
                }
            }

            refreshLanguageHints(before)
            refreshPredictionBar()
        }

        override fun onEnter() {
            currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            )
            currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
            )

            clearSwipeState()
            refreshPredictionBar()
        }

        override fun onCandidate(value: String) {
            replaceLastSwipe(value)
        }

        override fun onPrediction(suggestion: PredictionSuggestion) {
            if (sensitiveField) return

            val connection = currentInputConnection ?: return
            val snapshot = currentContextSnapshot()
            val before = snapshot.beforeCursor
            val contextWords = extractWords(before).takeLast(5)

            if (suggestion.replacesCurrentToken) {
                val length = currentToken(before).length
                if (length > 0) {
                    connection.deleteSurroundingText(length, 0)
                }
            } else if (
                before.isNotEmpty() &&
                !before.last().isWhitespace() &&
                !before.last().isISOControl()
            ) {
                connection.commitText(" ", 1)
            }

            connection.commitText(suggestion.commitText + " ", 1)

            predictionLearningStore.learnChosenSuggestion(
                contextWords,
                suggestion.commitText
            )
            extractWords(suggestion.commitText).forEach { chosenWord ->
                userVocabularyStore.observeWord(
                    chosenWord,
                    languageHints
                )
            }

            clearSwipeState()

            val updated = textBeforeCursor()
            refreshLanguageHints(updated)
            refreshPredictionBar()
        }

        override fun onTranslate() {
            if (sensitiveField) {
                root?.setStatus("Translation is disabled in sensitive fields.")
                return
            }

            val selected = currentInputConnection
                ?.getSelectedText(0)
                ?.toString()
                .orEmpty()

            if (selected.isBlank()) {
                root?.setStatus("Select text first, then tap 🌐 to translate.")
                return
            }

            val target = Prefs.targetLanguage(this@KeySwiperImeService)
            root?.setStatus("Translating → $target…")

            translationEngine.translate(selected, target) { result ->
                result.onSuccess { translated ->
                    currentInputConnection?.commitText(translated, 1)
                    root?.setStatus("Translated locally with Google ML Kit.")
                    refreshLanguageHints(translated)
                    refreshPredictionBar()
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

                    val before = textBeforeCursor()
                    predictionLearningStore.learnTransition(
                        extractWords(before).takeLast(5)
                    )
                    refreshLanguageHints(before)
                    refreshPredictionBar()
                },
                onError = { root?.setStatus(it) }
            )
        }

        override fun onClipboard() {
            if (sensitiveField) {
                root?.setStatus(
                    "Clipboard history is hidden in sensitive fields."
                )
                return
            }

            root?.showClipboardPanel(clipboardController.items())
        }

        override fun onEmoji(value: String) {
            currentInputConnection?.commitText(value, 1)
            clearSwipeState()
            refreshPredictionBar()
        }

        override fun onHandwritingRequested() {
            if (sensitiveField) {
                root?.setStatus(
                    "Handwriting recognition is disabled in sensitive fields."
                )
                return
            }

            val language = Prefs.handwritingLanguage(this@KeySwiperImeService)
            root?.setStatus("Preparing handwriting model $language…")

            root?.showHandwritingPanel {
                digitalInkEngine.prepare(language) { result ->
                    result.onSuccess {
                        root?.setStatus("Handwriting ready: $language")
                    }.onFailure {
                        root?.setStatus(
                            it.message ?: "Handwriting model failed to load."
                        )
                    }
                }
            }
        }

        override fun onHandwritingRecognize(view: HandwritingPadView) {
            if (sensitiveField) return

            val before = currentInputConnection
                ?.getTextBeforeCursor(160, 0)
                ?.toString()
                .orEmpty()

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
                    root?.setStatus(null)
                    view.clearInk()

                    val updated = textBeforeCursor()
                    predictionLearningStore.learnTransition(
                        extractWords(updated).takeLast(5)
                    )
                    refreshLanguageHints(updated)
                    refreshPredictionBar()
                }.onFailure {
                    root?.setStatus(
                        it.message ?: "Handwriting recognition failed."
                    )
                }
            }
        }

        override fun onSettings() {
            startActivity(
                Intent(
                    this@KeySwiperImeService,
                    SettingsActivity::class.java
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        override fun onStylusPrimary() {
            if (sensitiveField) {
                root?.setStatus("S Pen action blocked in sensitive field.")
            } else {
                onVoice()
            }
        }

        override fun onStylusSecondary() {
            val word = lastSwipeWord

            if (word != null && lastSwipeCandidates.size > 1) {
                val index = lastSwipeCandidates
                    .indexOf(word)
                    .coerceAtLeast(0)

                val next = lastSwipeCandidates[
                    (index + 1) % lastSwipeCandidates.size
                ]

                replaceLastSwipe(next)
            } else {
                root?.setStatus(
                    "Secondary stylus action: no alternate candidate."
                )
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
            swipeLearningStore.record(
                signature,
                lastSwipeContextWord,
                value
            )

            if (trace != null) {
                motorProfileStore.learn(trace, value)
            }

            predictionLearningStore.learnTransition(
                extractWords(textBeforeCursor()).takeLast(5)
            )
            userVocabularyStore.rememberWord(
                value,
                languageHints.firstOrNull()
            )
        }

        lastSwipeWord = value
        root?.setStatus(
            "Adaptive swipe learned this correction locally."
        )

        refreshPredictionBar()
    }

    private fun refreshPredictionBar() {
        predictionGeneration++
        val requestGeneration = predictionGeneration

        if (sensitiveField) {
            root?.clearSuggestions()
            return
        }

        val snapshot = currentContextSnapshot()
        val before = snapshot.beforeCursor

        val context = PredictionContext(
            beforeCursor = before,
            languageHints = languageHints,
            inputMode = predictionInputMode,
            maxSemanticTokens = Prefs.semanticPredictionDepth(this),
            surrounding = snapshot
        )

        val base = predictionEngine.predict(
            context = context,
            maxSuggestions = 6
        )

        root?.setPredictions(base)

        if (
            neuralPredictionBackend.isReady() &&
            currentToken(before).isBlank()
        ) {
            neuralPredictionBackend.predict(
                context = context,
                maxSuggestions = 2
            ) { neural ->
                root?.post {
                    if (
                        requestGeneration == predictionGeneration &&
                        !sensitiveField &&
                        neural.isNotEmpty()
                    ) {
                        root?.setPredictions(
                            predictionEngine.mergeNeural(
                                base = base,
                                neural = neural,
                                maxSuggestions = 6
                            )
                        )
                    }
                }
            }
        }
    }

    private fun currentContextSnapshot() =
        surroundingContextReader.read(
            connection = currentInputConnection,
            editorInfo = currentInputEditorInfo,
            sensitive = sensitiveField
        )

    private fun refreshLanguageHints(text: String) {
        if (sensitiveField || text.isBlank()) return

        translationEngine.identifyLikelyLanguages(text) { detected ->
            if (detected.isNotEmpty()) {
                languageHints = detected
                refreshPredictionBar()
            }
        }
    }

    private fun textBeforeCursor(): String =
        currentInputConnection
            ?.getTextBeforeCursor(1600, 0)
            ?.toString()
            .orEmpty()

    private fun currentToken(text: String): String =
        text.takeLastWhile {
            it.isLetterOrDigit() || it == '\'' || it == '-'
        }

    private fun extractWords(text: String): List<String> =
        Regex("[\\p{L}\\p{N}'-]+")
            .findAll(text.takeLast(1200))
            .map { it.value.lowercase() }
            .toList()

    private fun previousWord(context: String): String =
        context.trim()
            .split(Regex("\\s+"))
            .lastOrNull()
            ?.trim { !it.isLetter() && it != '\'' }
            ?.lowercase()
            .orEmpty()

    private fun clearSwipeState() {
        lastSwipeWord = null
        lastSwipeCandidates = emptyList()
        lastSwipeSignature = null
        lastSwipeContextWord = ""
        lastSwipeTrace = null
    }

    private fun refreshPrivacyState() {
        if (sensitiveField) {
            root?.setStatus(
                "Private field: learning, surrounding context, neural prediction, clipboard, voice and transformations are off."
            )
        } else {
            root?.setStatus(null)
        }
    }
}
