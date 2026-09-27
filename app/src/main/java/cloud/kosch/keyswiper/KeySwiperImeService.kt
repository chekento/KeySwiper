package cloud.kosch.keyswiper

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.DeleteGesture
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.HandwritingGesture
import cloud.kosch.keyswiper.clipboard.ClipboardController
import cloud.kosch.keyswiper.handwriting.DigitalInkEngine
import cloud.kosch.keyswiper.handwriting.HandwritingCommitFormatter
import cloud.kosch.keyswiper.handwriting.ScratchDeleteGestureClassifier
import cloud.kosch.keyswiper.handwriting.StylusScreenPoint
import cloud.kosch.keyswiper.handwriting.SystemHandwritingInkView
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
import cloud.kosch.keyswiper.stylus.StylusAction
import cloud.kosch.keyswiper.stylus.StylusActionStore
import cloud.kosch.keyswiper.stylus.StylusClickInterpreter
import cloud.kosch.keyswiper.stylus.StylusTrigger
import cloud.kosch.keyswiper.ui.HandwritingPadView
import cloud.kosch.keyswiper.ui.KeyboardRootView
import cloud.kosch.keyswiper.voice.VoiceInputController
import java.time.Duration
import java.util.Locale

class KeySwiperImeService : InputMethodService() {
    private val swipeDecoder = SwipeDecoder()
    private val translationEngine = TranslationEngine()
    private val digitalInkEngine = DigitalInkEngine()
    private val surroundingContextReader = SurroundingContextReader()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stylusClickInterpreter = StylusClickInterpreter()

    private lateinit var swipeLearningStore: SwipeLearningStore
    private lateinit var motorProfileStore: MotorProfileStore
    private lateinit var predictionLearningStore: PredictionLearningStore
    private lateinit var userVocabularyStore: UserVocabularyStore
    private lateinit var predictionEngine: HybridPredictionEngine
    private lateinit var neuralModelManager: NeuralModelManager
    private lateinit var neuralPredictionBackend: LiteRtLmPredictionBackend
    private lateinit var clipboardController: ClipboardController
    private lateinit var voiceController: VoiceInputController
    private lateinit var stylusActionStore: StylusActionStore

    private var root: KeyboardRootView? = null
    private var sensitiveField = false
    private var languageHints: List<String> = emptyList()
    private var predictionInputMode = PredictionInputMode.GENERAL
    private var predictionGeneration = 0L
    private var currentPredictions: List<PredictionSuggestion> = emptyList()
    private var clipboardQuery: String = ""

    private var lastSwipeWord: String? = null
    private var lastSwipeCandidates: List<String> = emptyList()
    private var lastSwipeSignature: String? = null
    private var lastSwipeContextWord: String = ""
    private var lastSwipeTrace: SwipeTrace? = null

    private var systemHandwritingView: SystemHandwritingInkView? = null
    private var systemHandwritingModelReady = false
    private var systemHandwritingRecognitionInFlight = false
    private var systemHandwritingGeneration = 0L
    private val systemHandwritingRecognitionRunnable = Runnable {
        recognizeSystemHandwritingBatch()
    }
    private var connectionlessHandwriting = false
    private val connectionlessRecognizedText = StringBuilder()
    private val handwritingGesturePoints = mutableListOf<StylusScreenPoint>()
    private val finishConnectionlessHandwritingRunnable = Runnable {
        finishConnectionlessHandwritingIfReady()
    }

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
        clipboardController.setDefaultExpiryMinutes(
            Prefs.clipboardExpiryMinutes(this)
        )
        voiceController = VoiceInputController(this)
        stylusActionStore = StylusActionStore(this)
        clipboardController.start()
    }

    override fun onPrepareStylusHandwriting() {
        super.onPrepareStylusHandwriting()

        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            (sensitiveField && !connectionlessHandwriting)
        ) {
            return
        }

        prepareSystemHandwritingModel()
    }

    override fun onStartStylusHandwriting(): Boolean {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            (sensitiveField && !connectionlessHandwriting)
        ) {
            return false
        }

        val handwritingWindow = getStylusHandwritingWindow()
            ?: return false

        systemHandwritingGeneration++
        systemHandwritingRecognitionInFlight = false
        connectionlessHandwriting = false
        connectionlessRecognizedText.setLength(0)
        handwritingGesturePoints.clear()

        val view = SystemHandwritingInkView(this).also {
            it.onStrokeFinished = {
                scheduleSystemHandwritingRecognition()
            }
        }

        systemHandwritingView = view
        handwritingWindow.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )
        handwritingWindow.setContentView(view)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setStylusHandwritingSessionTimeout(
                Duration.ofSeconds(7)
            )
        }

        prepareSystemHandwritingModel()
        return true
    }


    override fun onStartConnectionlessStylusHandwriting(
        inputType: Int,
        cursorAnchorInfo: CursorAnchorInfo?
    ): Boolean {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            SecurityPolicy.isSensitiveInputType(inputType)
        ) {
            return false
        }

        val handwritingWindow = getStylusHandwritingWindow()
            ?: return false

        systemHandwritingGeneration++
        systemHandwritingRecognitionInFlight = false
        connectionlessHandwriting = true
        connectionlessRecognizedText.setLength(0)
        handwritingGesturePoints.clear()

        val view = SystemHandwritingInkView(this).also {
            it.onStrokeFinished = {
                scheduleSystemHandwritingRecognition()
            }
        }

        systemHandwritingView = view
        handwritingWindow.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )
        handwritingWindow.setContentView(view)
        setStylusHandwritingSessionTimeout(
            Duration.ofSeconds(6)
        )

        prepareSystemHandwritingModel()
        return true
    }

    override fun onStylusHandwritingMotionEvent(
        motionEvent: MotionEvent
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        if (
            motionEvent.actionMasked == MotionEvent.ACTION_DOWN &&
            motionEvent.pointerCount > 0
        ) {
            mainHandler.removeCallbacks(
                finishConnectionlessHandwritingRunnable
            )
            handwritingGesturePoints.clear()

            if (
                (motionEvent.buttonState and
                    MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
            ) {
                handlePrimaryStylusPress()
            }

            if (
                (motionEvent.buttonState and
                    MotionEvent.BUTTON_STYLUS_SECONDARY) != 0
            ) {
                executeStylusAction(
                    stylusActionStore.actionFor(
                        StylusTrigger.SECONDARY_SINGLE
                    )
                )
            }
        }

        trackHandwritingGesture(motionEvent)

        systemHandwritingView
            ?.consumeStylusEvent(motionEvent)

        if (motionEvent.actionMasked == MotionEvent.ACTION_UP) {
            if (tryPerformScratchDeleteGesture()) {
                mainHandler.removeCallbacks(
                    systemHandwritingRecognitionRunnable
                )
            }
        }
    }

    override fun onFinishStylusHandwriting() {
        systemHandwritingGeneration++
        mainHandler.removeCallbacks(
            systemHandwritingRecognitionRunnable
        )
        systemHandwritingRecognitionInFlight = false
        connectionlessHandwriting = false
        connectionlessRecognizedText.setLength(0)
        handwritingGesturePoints.clear()
        mainHandler.removeCallbacks(
            finishConnectionlessHandwritingRunnable
        )
        systemHandwritingView?.clearInk()
        systemHandwritingView = null
        super.onFinishStylusHandwriting()
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
        systemHandwritingGeneration++
        systemHandwritingModelReady = false
        systemHandwritingRecognitionInFlight = false
        connectionlessHandwriting = false
        connectionlessRecognizedText.setLength(0)
        handwritingGesturePoints.clear()
        systemHandwritingView?.clearInk()
        systemHandwritingView = null
        stylusClickInterpreter.clear()
        mainHandler.removeCallbacksAndMessages(null)
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
        stylusClickInterpreter.clear()
        mainHandler.removeCallbacksAndMessages(null)
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
            currentPredictions = emptyList()
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
            val action =
                currentInputEditorInfo
                    ?.imeOptions
                    ?.and(
                        EditorInfo.IME_MASK_ACTION
                    )
                    ?: EditorInfo.IME_ACTION_NONE

            val handled =
                if (
                    action != EditorInfo.IME_ACTION_NONE &&
                    action != EditorInfo.IME_ACTION_UNSPECIFIED
                ) {
                    currentInputConnection
                        ?.performEditorAction(
                            action
                        )
                        ?: false
                } else {
                    false
                }

            if (!handled) {
                currentInputConnection
                    ?.sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_ENTER
                        )
                    )
                currentInputConnection
                    ?.sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_UP,
                            KeyEvent.KEYCODE_ENTER
                        )
                    )
            }

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

        override fun onTranslationPanelRequested() {
            if (sensitiveField) {
                root?.setStatus(
                    "Translation is disabled in sensitive fields."
                )
                return
            }

            root?.showTranslationPanel(
                currentTarget =
                    Prefs.targetLanguage(
                        this@KeySwiperImeService
                    ),
                recentTargets =
                    Prefs.translationTargetHistory(
                        this@KeySwiperImeService
                    ),
                detectedLanguages =
                    languageHints
            )
        }

        override fun onTranslationTargetSelected(
            languageTag: String
        ) {
            if (sensitiveField) return

            Prefs.setTargetLanguage(
                this@KeySwiperImeService,
                languageTag
            )

            val target =
                Prefs.targetLanguage(
                    this@KeySwiperImeService
                )

            root?.showTranslationPanel(
                currentTarget = target,
                recentTargets =
                    Prefs.translationTargetHistory(
                        this@KeySwiperImeService
                    ),
                detectedLanguages =
                    languageHints
            )

            root?.setStatus(
                "Translation target: ${target.uppercase()}"
            )
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
                onTranslationPanelRequested()
                root?.setStatus(
                    "Select text, choose a target language, then translate."
                )
                return
            }

            val target = Prefs.targetLanguage(this@KeySwiperImeService)
            root?.setStatus("Translating → $target…")

            translationEngine.translate(selected, target) { result ->
                result.onSuccess { translated ->
                    currentInputConnection?.commitText(translated, 1)
                    root?.showKeyboard()
                    root?.setStatus(
                        "Translated locally → ${target.uppercase()}."
                    )
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

            clipboardQuery = ""
            showClipboardPanel()
        }

        override fun onClipboardInsert(
            id: String
        ) {
            if (sensitiveField) return

            val value =
                clipboardController.textFor(id)
                    ?: return

            currentInputConnection
                ?.commitText(
                    value,
                    1
                )

            clearSwipeState()
            refreshLanguageHints(
                textBeforeCursor()
            )
            refreshPredictionBar()
        }

        override fun onClipboardSearch(
            query: String
        ) {
            if (sensitiveField) return

            clipboardQuery =
                query.trim()

            showClipboardPanel()
        }

        override fun onClipboardTogglePin(
            id: String
        ) {
            if (sensitiveField) return

            clipboardController
                .togglePin(id)

            showClipboardPanel()
        }

        override fun onClipboardDelete(
            id: String
        ) {
            if (sensitiveField) return

            clipboardController
                .delete(id)

            showClipboardPanel()
        }

        override fun onClipboardClearUnpinned() {
            if (sensitiveField) return

            clipboardController
                .clearUnpinned()

            showClipboardPanel()
        }

        override fun onClipboardExpiryChanged(
            minutes: Long
        ) {
            if (sensitiveField) return

            Prefs.setClipboardExpiryMinutes(
                this@KeySwiperImeService,
                minutes
            )
            clipboardController
                .setDefaultExpiryMinutes(
                    minutes
                )

            showClipboardPanel()
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
            handlePrimaryStylusPress()
        }

        override fun onStylusSecondary() {
            executeStylusAction(
                stylusActionStore.actionFor(
                    StylusTrigger.SECONDARY_SINGLE
                )
            )
        }
    }

    private fun handlePrimaryStylusPress() {
        val now = SystemClock.uptimeMillis()
        val isDouble = stylusClickInterpreter.registerPrimaryPress(now)

        if (isDouble) {
            executeStylusAction(
                stylusActionStore.actionFor(
                    StylusTrigger.PRIMARY_DOUBLE
                )
            )
            return
        }

        mainHandler.postDelayed(
            {
                if (
                    stylusClickInterpreter.consumePendingSingle(now)
                ) {
                    executeStylusAction(
                        stylusActionStore.actionFor(
                            StylusTrigger.PRIMARY_SINGLE
                        )
                    )
                }
            },
            280L
        )
    }

    private fun executeStylusAction(
        action: StylusAction
    ) {
        if (
            sensitiveField &&
            action != StylusAction.SETTINGS &&
            action != StylusAction.NONE
        ) {
            root?.setStatus(
                "Stylus content action blocked in sensitive field."
            )
            return
        }

        when (action) {
            StylusAction.VOICE_TOGGLE ->
                callbacks.onVoice()

            StylusAction.ACCEPT_TOP_PREDICTION -> {
                val top = currentPredictions.firstOrNull()
                if (top != null) {
                    callbacks.onPrediction(top)
                } else {
                    root?.setStatus(
                        "No prediction is currently available."
                    )
                }
            }

            StylusAction.NEXT_CANDIDATE ->
                cycleSwipeCandidate(1)

            StylusAction.PREVIOUS_CANDIDATE ->
                cycleSwipeCandidate(-1)

            StylusAction.TRANSLATE_SELECTION ->
                callbacks.onTranslate()

            StylusAction.CLIPBOARD ->
                callbacks.onClipboard()

            StylusAction.EMOJI ->
                root?.showEmojiPanel()

            StylusAction.HANDWRITING ->
                callbacks.onHandwritingRequested()

            StylusAction.UNDO_LAST_SWIPE -> {
                if (lastSwipeWord != null) {
                    callbacks.onBackspace()
                } else {
                    root?.setStatus(
                        "No recent swipe word to undo."
                    )
                }
            }

            StylusAction.SETTINGS ->
                callbacks.onSettings()

            StylusAction.NONE ->
                Unit
        }
    }

    private fun cycleSwipeCandidate(
        delta: Int
    ) {
        val current = lastSwipeWord

        if (
            current == null ||
            lastSwipeCandidates.size <= 1
        ) {
            root?.setStatus(
                "No alternate swipe candidate."
            )
            return
        }

        val index = lastSwipeCandidates
            .indexOf(current)
            .coerceAtLeast(0)

        val size = lastSwipeCandidates.size
        val nextIndex = (
            (index + delta) % size + size
            ) % size

        replaceLastSwipe(
            value = lastSwipeCandidates[nextIndex],
            learn = false
        )
    }

    private fun replaceLastSwipe(
        value: String,
        learn: Boolean = true
    ) {
        val previous = lastSwipeWord ?: return
        val connection = currentInputConnection ?: return
        val signature = lastSwipeSignature
        val trace = lastSwipeTrace

        connection.deleteSurroundingText(previous.length + 1, 0)
        connection.commitText(value + " ", 1)

        if (!sensitiveField && signature != null && learn) {
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
            if (learn) {
                "Adaptive swipe learned this correction locally."
            } else {
                "Swipe candidate changed with stylus."
            }
        )

        if (learn) {
            refreshPredictionBar()
        } else {
            currentPredictions = emptyList()
            root?.setSwipeCandidates(lastSwipeCandidates)
        }
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

        currentPredictions = base
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
                        val merged = predictionEngine.mergeNeural(
                            base = base,
                            neural = neural,
                            maxSuggestions = 6
                        )
                        currentPredictions = merged
                        root?.setPredictions(merged)
                    }
                }
            }
        }
    }

    private fun prepareSystemHandwritingModel() {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            (sensitiveField && !connectionlessHandwriting)
        ) {
            systemHandwritingModelReady = false
            return
        }

        val language =
            Prefs.handwritingLanguage(this)

        if (digitalInkEngine.isReadyFor(language)) {
            systemHandwritingModelReady = true

            if (systemHandwritingView?.hasInk() == true) {
                scheduleSystemHandwritingRecognition()
            }
            return
        }

        digitalInkEngine.prepare(language) { result ->
            mainHandler.post {
                systemHandwritingModelReady =
                    result.isSuccess

                result.onSuccess {
                    if (
                        systemHandwritingView
                            ?.hasInk() == true
                    ) {
                        scheduleSystemHandwritingRecognition()
                    }
                }.onFailure {
                    root?.setStatus(
                        it.message
                            ?: "Stylus handwriting model failed to load."
                    )
                }
            }
        }
    }

    private fun scheduleSystemHandwritingRecognition() {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            (sensitiveField && !connectionlessHandwriting)
        ) {
            return
        }

        mainHandler.removeCallbacks(
            systemHandwritingRecognitionRunnable
        )
        mainHandler.postDelayed(
            systemHandwritingRecognitionRunnable,
            650L
        )
    }

    private fun recognizeSystemHandwritingBatch() {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            (sensitiveField && !connectionlessHandwriting) ||
            systemHandwritingRecognitionInFlight
        ) {
            return
        }

        val view =
            systemHandwritingView ?: return

        if (!view.hasInk()) {
            return
        }

        if (!systemHandwritingModelReady) {
            prepareSystemHandwritingModel()
            mainHandler.postDelayed(
                systemHandwritingRecognitionRunnable,
                250L
            )
            return
        }

        val generation = systemHandwritingGeneration
        val ink = view.drainInk()
        val width = view.width
            .toFloat()
            .coerceAtLeast(1f)
        val height = view.height
            .toFloat()
            .coerceAtLeast(1f)

        val before = if (connectionlessHandwriting) {
            ""
        } else {
            currentInputConnection
                ?.getTextBeforeCursor(200, 0)
                ?.toString()
                .orEmpty()
        }

        systemHandwritingRecognitionInFlight = true

        digitalInkEngine.recognize(
            ink = ink,
            preContext = before,
            width = width,
            height = height
        ) { result ->
            mainHandler.post {
                if (
                    generation != systemHandwritingGeneration
                ) {
                    systemHandwritingRecognitionInFlight = false
                    return@post
                }

                systemHandwritingRecognitionInFlight = false

                result.onSuccess { values ->
                    val best =
                        values.firstOrNull().orEmpty()

                    val committed =
                        HandwritingCommitFormatter
                            .formatRecognition(best)

                    if (committed.isNotBlank()) {
                        if (connectionlessHandwriting) {
                            if (connectionlessRecognizedText.isNotEmpty()) {
                                connectionlessRecognizedText.append(' ')
                            }
                            connectionlessRecognizedText.append(
                                best.trim()
                            )

                            extractWords(best).forEach {
                                userVocabularyStore.observeWord(
                                    it,
                                    languageHints
                                )
                            }

                            mainHandler.removeCallbacks(
                                finishConnectionlessHandwritingRunnable
                            )
                            mainHandler.postDelayed(
                                finishConnectionlessHandwritingRunnable,
                                950L
                            )
                        } else {
                            currentInputConnection
                                ?.commitText(
                                    committed,
                                    1
                                )

                            if (!sensitiveField) {
                                val words =
                                    extractWords(
                                        textBeforeCursor()
                                    )

                                predictionLearningStore
                                    .learnTransition(
                                        words.takeLast(5)
                                    )

                                extractWords(best)
                                    .forEach {
                                        userVocabularyStore
                                            .observeWord(
                                                it,
                                                languageHints
                                            )
                                    }
                            }

                            refreshLanguageHints(
                                textBeforeCursor()
                            )
                            refreshPredictionBar()
                        }
                    }

                    root?.setStatus(
                        if (connectionlessHandwriting) {
                            "Connectionless handwriting recognized locally."
                        } else {
                            "Stylus handwriting recognized locally."
                        }
                    )
                }.onFailure {
                    root?.setStatus(
                        it.message
                            ?: "Stylus handwriting recognition failed."
                    )
                }

                if (
                    systemHandwritingView
                        ?.hasInk() == true
                ) {
                    scheduleSystemHandwritingRecognition()
                }
            }
        }
    }


    private fun trackHandwritingGesture(
        event: MotionEvent
    ) {
        if (
            connectionlessHandwriting ||
            event.pointerCount == 0 ||
            event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS
        ) {
            return
        }

        val offsetX = event.rawX - event.x
        val offsetY = event.rawY - event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handwritingGesturePoints.clear()
                handwritingGesturePoints.add(
                    StylusScreenPoint(
                        event.rawX,
                        event.rawY,
                        event.eventTime
                    )
                )
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.historySize) {
                    handwritingGesturePoints.add(
                        StylusScreenPoint(
                            event.getHistoricalX(i) + offsetX,
                            event.getHistoricalY(i) + offsetY,
                            event.getHistoricalEventTime(i)
                        )
                    )
                }

                handwritingGesturePoints.add(
                    StylusScreenPoint(
                        event.rawX,
                        event.rawY,
                        event.eventTime
                    )
                )
            }

            MotionEvent.ACTION_UP -> {
                handwritingGesturePoints.add(
                    StylusScreenPoint(
                        event.rawX,
                        event.rawY,
                        event.eventTime
                    )
                )
            }

            MotionEvent.ACTION_CANCEL -> {
                handwritingGesturePoints.clear()
            }
        }
    }

    private fun tryPerformScratchDeleteGesture(): Boolean {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            connectionlessHandwriting ||
            sensitiveField
        ) {
            handwritingGesturePoints.clear()
            return false
        }

        val scratch = ScratchDeleteGestureClassifier.classify(
            points = handwritingGesturePoints.toList(),
            density = resources.displayMetrics.density
        )

        handwritingGesturePoints.clear()

        if (scratch == null) {
            return false
        }

        val supportsDelete = currentInputEditorInfo
            ?.supportedHandwritingGestures
            ?.contains(DeleteGesture::class.java) == true

        if (!supportsDelete) {
            return false
        }

        val connection = currentInputConnection ?: return false

        systemHandwritingView?.discardLastStroke()

        val gesture = DeleteGesture.Builder()
            .setDeletionArea(scratch.bounds)
            .setGranularity(
                HandwritingGesture.GRANULARITY_WORD
            )
            .build()

        connection.performHandwritingGesture(
            gesture,
            mainExecutor
        ) { result ->
            root?.setStatus(
                when (result) {
                    android.view.inputmethod.InputConnection
                        .HANDWRITING_GESTURE_RESULT_SUCCESS ->
                        "Scratch-out gesture deleted text."

                    android.view.inputmethod.InputConnection
                        .HANDWRITING_GESTURE_RESULT_UNSUPPORTED ->
                        "This editor does not support scratch-out deletion."

                    else ->
                        "Scratch-out gesture was not applied."
                }
            )

            if (
                result == android.view.inputmethod.InputConnection
                    .HANDWRITING_GESTURE_RESULT_SUCCESS
            ) {
                refreshPredictionBar()
            }
        }

        return true
    }

    private fun finishConnectionlessHandwritingIfReady() {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
            !connectionlessHandwriting ||
            systemHandwritingRecognitionInFlight ||
            systemHandwritingView?.hasInk() == true
        ) {
            return
        }

        val result = connectionlessRecognizedText
            .toString()
            .trim()

        if (result.isBlank()) {
            return
        }

        connectionlessHandwriting = false
        connectionlessRecognizedText.setLength(0)
        finishConnectionlessStylusHandwriting(result)
    }

    private fun showClipboardPanel() {
        if (sensitiveField) {
            root?.clearSuggestions()
            return
        }

        root?.showClipboardPanel(
            items =
                clipboardController.items(
                    clipboardQuery
                ),
            query =
                clipboardQuery,
            expiryMinutes =
                Prefs.clipboardExpiryMinutes(
                    this
                )
        )
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
