package cloud.kosch.keyswiper

import android.annotation.TargetApi
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Region
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.DeleteGesture
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.HandwritingGesture
import android.view.inputmethod.JoinOrSplitGesture
import android.view.inputmethod.RemoveSpaceGesture
import android.view.inputmethod.SelectGesture
import cloud.kosch.keyswiper.clipboard.ClipboardController
import cloud.kosch.keyswiper.handwriting.DigitalInkEngine
import cloud.kosch.keyswiper.handwriting.ExtendedHandwritingGesture
import cloud.kosch.keyswiper.handwriting.ExtendedHandwritingGestureClassifier
import cloud.kosch.keyswiper.handwriting.HandwritingCommitFormatter
import cloud.kosch.keyswiper.handwriting.ScratchDeleteGestureClassifier
import cloud.kosch.keyswiper.handwriting.StylusScreenPoint
import cloud.kosch.keyswiper.handwriting.SystemHandwritingInkView
import cloud.kosch.keyswiper.handwriting.StylusStrokeBoundary
import cloud.kosch.keyswiper.handwriting.StylusWritingArea
import cloud.kosch.keyswiper.input.EditTimeline
import cloud.kosch.keyswiper.input.KeyOffset
import cloud.kosch.keyswiper.input.MotorProfileStore
import cloud.kosch.keyswiper.input.SwipeDecoder
import cloud.kosch.keyswiper.input.SwipeLearningStore
import cloud.kosch.keyswiper.input.SwipeTrace
import cloud.kosch.keyswiper.input.TextBoundaryUtils
import cloud.kosch.keyswiper.language.CodeSwitchLanguageResolver
import cloud.kosch.keyswiper.language.TranslationEngine
import cloud.kosch.keyswiper.language.UserVocabularyStore
import cloud.kosch.keyswiper.prediction.ContextPredictionEngine
import cloud.kosch.keyswiper.prediction.HybridPredictionEngine
import cloud.kosch.keyswiper.prediction.LocalBeamSemanticProvider
import cloud.kosch.keyswiper.prediction.NeuralBackendFactory
import cloud.kosch.keyswiper.prediction.NeuralModelManager
import cloud.kosch.keyswiper.prediction.NeuralPredictionBackend
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
import cloud.kosch.keyswiper.ui.KeyboardEditorMode
import cloud.kosch.keyswiper.ui.KeyboardEditorModeResolver
import cloud.kosch.keyswiper.ui.KeyboardLayoutProfiles
import cloud.kosch.keyswiper.ui.KeyboardRootView
import cloud.kosch.keyswiper.ui.KeyboardThemes
import cloud.kosch.keyswiper.voice.VoiceEditCommand
import cloud.kosch.keyswiper.voice.VoiceEditCommandParser
import cloud.kosch.keyswiper.voice.VoiceInputController
import java.time.Duration
import java.util.Locale

class KeySwiperImeService : InputMethodService() {
    private val swipeDecoder = SwipeDecoder()
    private val surroundingContextReader = SurroundingContextReader()

    private val translationEngineDelegate =
        lazy(LazyThreadSafetyMode.NONE) {
            TranslationEngine()
        }
    private val translationEngine: TranslationEngine
        get() = translationEngineDelegate.value

    private val digitalInkEngineDelegate =
        lazy(LazyThreadSafetyMode.NONE) {
            DigitalInkEngine()
        }
    private val digitalInkEngine: DigitalInkEngine
        get() = digitalInkEngineDelegate.value
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stylusClickInterpreter = StylusClickInterpreter()
    private val editTimeline = EditTimeline()

    private lateinit var swipeLearningStore: SwipeLearningStore
    private lateinit var motorProfileStore: MotorProfileStore
    private lateinit var predictionLearningStore: PredictionLearningStore
    private lateinit var userVocabularyStore: UserVocabularyStore
    private lateinit var predictionEngine: HybridPredictionEngine
    private lateinit var neuralModelManager: NeuralModelManager
    private var neuralPredictionBackend: NeuralPredictionBackend? = null

    private val clipboardControllerDelegate =
        lazy(LazyThreadSafetyMode.NONE) {
            ClipboardController(this).also {
                it.setDefaultExpiryMinutes(
                    Prefs.clipboardExpiryMinutes(this)
                )
                it.start()
            }
        }
    private val clipboardController: ClipboardController
        get() = clipboardControllerDelegate.value

    private val voiceControllerDelegate =
        lazy(LazyThreadSafetyMode.NONE) {
            VoiceInputController(this)
        }
    private val voiceController: VoiceInputController
        get() = voiceControllerDelegate.value

    private lateinit var stylusActionStore: StylusActionStore

    private var root: KeyboardRootView? = null
    private var sensitiveField = false
    private var languageHints: List<String> = emptyList()
    private var languageDetectionGeneration = 0L
    private var predictionInputMode = PredictionInputMode.GENERAL
    private var predictionGeneration = 0L
    private var currentPredictions: List<PredictionSuggestion> = emptyList()
    private var clipboardQuery: String = ""

    private val selectionRefreshRunnable =
        Runnable {
            if (!sensitiveField) {
                val before =
                    textBeforeCursor()
                refreshLanguageHints(
                    before
                )
                refreshPredictionBar()
                updateAutoShift()
            }
        }

    private var lastSwipeWord: String? = null
    private var lastSwipeCandidates: List<String> = emptyList()
    private var lastSwipeSignature: String? = null
    private var lastSwipeContextWord: String = ""
    private var lastSwipeTrace: SwipeTrace? = null

    private var systemHandwritingView: SystemHandwritingInkView? = null
    private val handwritingStrokeBoundary = StylusStrokeBoundary()
    private var handwritingKeyboardTarget: KeyboardRootView? = null
    private var handwritingStrokeBounds: RectF? = null
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
        stylusActionStore = StylusActionStore(this)
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
        handwritingStrokeBounds = null
        handwritingStrokeBoundary.reset()
        handwritingKeyboardTarget = null
        updateSystemHandwritingRegion()

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
        handwritingStrokeBounds = null
        handwritingStrokeBoundary.reset()
        handwritingKeyboardTarget = null
        updateSystemHandwritingRegion()
        setStylusHandwritingSessionTimeout(
            Duration.ofSeconds(6)
        )

        prepareSystemHandwritingModel()
        return true
    }

    override fun onStylusHandwritingMotionEvent(motionEvent: MotionEvent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            motionEvent.pointerCount == 0
        ) return

        val action = motionEvent.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            mainHandler.removeCallbacks(finishConnectionlessHandwritingRunnable)
            handwritingGesturePoints.clear()
            val keyboard = root?.takeIf { it.isShown }
            val location = IntArray(2)
            keyboard?.getLocationOnScreen(location)
            handwritingKeyboardTarget = keyboard?.takeIf {
                motionEvent.rawX >= location[0] &&
                    motionEvent.rawX < location[0] + it.width &&
                    motionEvent.rawY >= location[1] &&
                    motionEvent.rawY < location[1] + it.height
            }
            handwritingStrokeBoundary.begin(
                motionEvent.rawX, motionEvent.rawY, screenWritingArea()
            )
        }

        // Android 16 passes touches outside our handwriting region through itself.
        // Older versions can still deliver keyboard touches to this callback. Keep
        // the whole sequence on the original keyboard control, in local coordinates.
        handwritingKeyboardTarget?.let { keyboard ->
            val location = IntArray(2)
            keyboard.getLocationOnScreen(location)
            val local = MotionEvent.obtain(motionEvent)
            try {
                local.setLocation(
                    motionEvent.rawX - location[0], motionEvent.rawY - location[1]
                )
                keyboard.dispatchTouchEvent(local)
            } finally {
                local.recycle()
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                handwritingKeyboardTarget = null
                finishStylusHandwriting()
            }
            return
        }

        var inside = handwritingStrokeBoundary.continueAt(motionEvent.rawX, motionEvent.rawY)
        val rawOffsetX = motionEvent.rawX - motionEvent.x
        val rawOffsetY = motionEvent.rawY - motionEvent.y
        for (index in 0 until motionEvent.historySize) {
            inside = handwritingStrokeBoundary.continueAt(
                motionEvent.getHistoricalX(index) + rawOffsetX,
                motionEvent.getHistoricalY(index) + rawOffsetY
            ) && inside
        }
        if (!inside || action == MotionEvent.ACTION_CANCEL) {
            val cancel = MotionEvent.obtain(motionEvent)
            try {
                cancel.action = MotionEvent.ACTION_CANCEL
                systemHandwritingView?.consumeStylusEvent(cancel)
            } finally {
                cancel.recycle()
            }
            handwritingGesturePoints.clear()
            if (action == MotionEvent.ACTION_DOWN) finishStylusHandwriting()
            return
        }

        if (action == MotionEvent.ACTION_DOWN) {
            val primary = (motionEvent.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
            val secondary = (motionEvent.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0
            if (primary || secondary) {
                handwritingStrokeBoundary.reset()
                if (primary) handlePrimaryStylusPress()
                if (secondary) executeStylusAction(
                    stylusActionStore.actionFor(StylusTrigger.SECONDARY_SINGLE)
                )
                return
            }
        }

        trackHandwritingGesture(motionEvent)
        val inkView = systemHandwritingView ?: return
        val location = IntArray(2)
        inkView.getLocationOnScreen(location)
        val local = MotionEvent.obtain(motionEvent)
        try {
            local.setLocation(
                motionEvent.rawX - location[0], motionEvent.rawY - location[1]
            )
            inkView.consumeStylusEvent(local)
        } finally {
            local.recycle()
        }
        val x = motionEvent.rawX
        val y = motionEvent.rawY
        val strokeBounds = handwritingStrokeBounds
        if (strokeBounds == null) handwritingStrokeBounds = RectF(x, y, x + 1f, y + 1f)
        else strokeBounds.union(x, y)
        for (index in 0 until motionEvent.historySize) {
            handwritingStrokeBounds?.union(
                motionEvent.getHistoricalX(index) + rawOffsetX,
                motionEvent.getHistoricalY(index) + rawOffsetY
            )
        }

        if (action == MotionEvent.ACTION_UP) {
            if (tryPerformHandwritingEditGesture()) {
                mainHandler.removeCallbacks(systemHandwritingRecognitionRunnable)
            }
            updateSystemHandwritingRegion()
            handwritingStrokeBoundary.reset()
        }
    }

    private fun screenWritingArea(): StylusWritingArea {
        val metrics = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getSystemService(WindowManager::class.java)?.maximumWindowMetrics
        } else null
        val bars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            metrics?.windowInsets?.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        } else null
        val width = metrics?.bounds?.width() ?: resources.displayMetrics.widthPixels
        val height = metrics?.bounds?.height() ?: resources.displayMetrics.heightPixels
        val location = IntArray(2)
        val keyboard = root?.takeIf { it.isShown && it.height > 0 }
        keyboard?.getLocationOnScreen(location)
        val bottom = if (keyboard != null) location[1] else height - (bars?.bottom ?: 0)
        return StylusWritingArea(
            (bars?.left ?: 0).toFloat(),
            (bars?.top ?: 0).toFloat(),
            (width - (bars?.right ?: 0)).toFloat(),
            bottom.toFloat()
        )
    }

    private fun updateSystemHandwritingRegion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA ||
            systemHandwritingView == null
        ) return
        val area = screenWritingArea()
        val bounds = RectF(area.left, area.top, area.right, area.bottom)
        handwritingStrokeBounds?.let { ink ->
            val margin = resources.displayMetrics.density * 64f
            val nearby = RectF(ink).apply { inset(-margin, -margin) }
            if (!bounds.intersect(nearby)) bounds.setEmpty()
        }
        setStylusHandwritingRegion(Region(
            bounds.left.toInt(), bounds.top.toInt(),
            bounds.right.toInt(), bounds.bottom.toInt()
        ))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && systemHandwritingView != null) {
            // A new orientation/display invalidates both the latched stroke bounds
            // and the handwriting window's coordinate transform.
            finishStylusHandwriting()
        }
        super.onConfigurationChanged(newConfig)
    }

    override fun onFinishStylusHandwriting() {
        handwritingStrokeBoundary.reset()
        handwritingKeyboardTarget = null
        handwritingStrokeBounds = null
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
            styleImeSystemBars()
            root = view
            view.callbacks = callbacks
            view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateSystemHandwritingRegion()
            }

            view.setOnApplyWindowInsetsListener {
                    _,
                    insets ->
                val bottom =
                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.R
                    ) {
                        insets.getInsets(
                            WindowInsets.Type.navigationBars()
                        ).bottom
                    } else {
                        @Suppress("DEPRECATION")
                        insets.systemWindowInsetBottom
                    }

                view.setSystemBottomInset(bottom)
                insets
            }
            view.requestApplyInsets()

            refreshPrivacyState()
            refreshPredictionBar()
            refreshEditHistoryState()
        }

    override fun onWindowShown() {
        super.onWindowShown()
        styleImeSystemBars()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sensitiveField = SecurityPolicy.isSensitive(attribute)
        predictionInputMode = PredictionContextClassifier.classify(attribute)
        languageHints = emptyList()
        languageDetectionGeneration++
        predictionGeneration++
        editTimeline.clear()
        clearSwipeState()
        refreshEditHistoryState()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        styleImeSystemBars()
        sensitiveField = SecurityPolicy.isSensitive(info)
        predictionInputMode =
            PredictionContextClassifier.classify(
                info
            )

        root?.setEditorMode(
            KeyboardEditorModeResolver
                .fromInputType(
                    info?.inputType ?: 0
                )
        )
        root?.showKeyboard()
        clipboardQuery = ""

        refreshPrivacyState()

        if (sensitiveField) {
            predictionGeneration++
            currentPredictions =
                emptyList()
            root?.clearSuggestions()
            root?.setAutoShift(false)
        } else {
            val snapshot =
                currentContextSnapshot()
            refreshLanguageHints(
                snapshot.beforeCursor
            )
            refreshPredictionBar()
            updateAutoShift()
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )

        mainHandler.removeCallbacks(
            selectionRefreshRunnable
        )

        if (!sensitiveField) {
            mainHandler.postDelayed(
                selectionRefreshRunnable,
                90L
            )
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
        if (voiceControllerDelegate.isInitialized()) {
            voiceControllerDelegate.value.stop()
        }
        languageHints = emptyList()
        languageDetectionGeneration++
        predictionInputMode = PredictionInputMode.GENERAL
        editTimeline.clear()
        clearSwipeState()
        refreshEditHistoryState()
        root?.clearSuggestions()
        root?.setStatus(null)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        predictionGeneration++
        stylusClickInterpreter.clear()
        mainHandler.removeCallbacksAndMessages(null)
        NeuralBackendFactory.close(
            neuralPredictionBackend
        )
        neuralPredictionBackend = null

        if (clipboardControllerDelegate.isInitialized()) {
            clipboardControllerDelegate.value.stop()
        }
        if (voiceControllerDelegate.isInitialized()) {
            voiceControllerDelegate.value.destroy()
        }
        if (translationEngineDelegate.isInitialized()) {
            translationEngineDelegate.value.close()
        }
        if (digitalInkEngineDelegate.isInitialized()) {
            digitalInkEngineDelegate.value.close()
        }

        super.onDestroy()
    }

    private val callbacks = object : KeyboardRootView.Callbacks {
        override fun onCharacter(value: Char) {
            currentInputConnection?.commitText(value.toString(), 1)
            clearSwipeState()
            refreshPredictionBar()
            updateAutoShift()
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
                preferredLanguages = if (before.isBlank()) {
                    listOf(KeyboardLayoutProfiles.byId(trace.layoutId).languageTag)
                } else activeLanguageLanes.map { it.tag },
                additionalWords = if (sensitiveField) emptyList() else {
                    userVocabularyStore.frequentWords(activeLanguageLanes, 256).map { it.first }
                },
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
            val connection =
                currentInputConnection
                    ?: return

            val selected =
                connection
                    .getSelectedText(0)
                    ?.toString()
                    .orEmpty()

            when {
                selected.isNotEmpty() -> {
                    connection.commitText(
                        "",
                        1
                    )
                    clearSwipeState()
                }

                lastSwipeWord != null -> {
                    val swipeWord =
                        lastSwipeWord
                            ?: return

                    connection
                        .deleteSurroundingText(
                            swipeWord.length + 1,
                            0
                        )
                    clearSwipeState()
                }

                else -> {
                    val before =
                        connection
                            .getTextBeforeCursor(
                                64,
                                0
                            )
                            ?.toString()
                            .orEmpty()

                    val count =
                        TextBoundaryUtils
                            .lastGraphemeUtf16Length(
                                before
                            )

                    if (count > 0) {
                        connection
                            .deleteSurroundingText(
                                count,
                                0
                            )
                    }
                }
            }

            refreshPredictionBar()
            updateAutoShift()
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
            updateAutoShift()
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
            updateAutoShift()
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

            var replacedToken: String? =
                null

            if (suggestion.replacesCurrentToken) {
                val token =
                    currentToken(before)
                val length =
                    token.length

                if (length > 0) {
                    replacedToken =
                        token
                    connection
                        .deleteSurroundingText(
                            length,
                            0
                        )
                }
            } else if (
                before.isNotEmpty() &&
                !before.last().isWhitespace() &&
                !before.last().isISOControl()
            ) {
                connection.commitText(" ", 1)
            }

            val committedText =
                suggestion.commitText +
                    " "

            connection.commitText(
                committedText,
                1
            )

            if (
                !sensitiveField &&
                replacedToken != null
            ) {
                editTimeline.record(
                    deletedText =
                        replacedToken,
                    insertedText =
                        committedText,
                    source =
                        "Prediction"
                )
                refreshEditHistoryState()
            }

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
            updateAutoShift()
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
                root?.setStatus(
                    "Voice input is disabled in sensitive fields."
                )
                return
            }

            voiceController.toggle(
                languageTag =
                    languageHints.firstOrNull()
                        ?: Locale.getDefault()
                            .toLanguageTag(),
                onPartial = {
                    root?.setStatus(it)
                },
                onFinal = {
                    currentInputConnection
                        ?.commitText(
                            it + " ",
                            1
                        )
                    root?.setStatus(null)

                    val before =
                        textBeforeCursor()

                    predictionLearningStore
                        .learnTransition(
                            extractWords(before)
                                .takeLast(5)
                        )

                    extractWords(it)
                        .forEach { word ->
                            userVocabularyStore
                                .observeWord(
                                    word,
                                    languageHints
                                )
                        }

                    refreshLanguageHints(
                        before
                    )
                    refreshPredictionBar()
                },
                onError = {
                    root?.setStatus(it)
                }
            )
        }

        override fun onVoiceCommand() {
            if (sensitiveField) {
                root?.setStatus(
                    "Voice editing is disabled in sensitive fields."
                )
                return
            }

            root?.setStatus(
                "Voice command mode…"
            )

            voiceController.toggle(
                languageTag =
                    Locale.getDefault()
                        .toLanguageTag(),
                onPartial = {
                    root?.setStatus(
                        "Command: " + it
                    )
                },
                onFinal = { spoken ->
                    val command =
                        VoiceEditCommandParser
                            .parse(spoken)

                    if (command == null) {
                        root?.setStatus(
                            "Voice command not recognized: " + spoken
                        )
                    } else {
                        executeVoiceEditCommand(
                            command
                        )
                    }
                },
                onError = {
                    root?.setStatus(it)
                }
            )
        }

        override fun onUndoEdit() {
            performTimelineUndo()
        }

        override fun onRedoEdit() {
            performTimelineRedo()
        }

        override fun onEditTimelineRequested() {
            if (sensitiveField) {
                root?.setStatus(
                    "Correction history is disabled in sensitive fields."
                )
                return
            }

            root?.showEditTimeline(
                editTimeline.recent()
            )
        }

        override fun onDeveloperText(
            value: String
        ) {
            currentInputConnection
                ?.commitText(
                    value,
                    1
                )

            clearSwipeState()
            refreshPredictionBar()
            updateAutoShift()
        }

        override fun onDeveloperKeyCode(
            keyCode: Int,
            ctrl: Boolean
        ) {
            val connection =
                currentInputConnection
                    ?: return

            val now =
                SystemClock.uptimeMillis()

            val metaState =
                if (ctrl) {
                    KeyEvent.META_CTRL_ON
                } else {
                    0
                }

            connection.sendKeyEvent(
                KeyEvent(
                    now,
                    now,
                    KeyEvent.ACTION_DOWN,
                    keyCode,
                    0,
                    metaState
                )
            )

            connection.sendKeyEvent(
                KeyEvent(
                    now,
                    now,
                    KeyEvent.ACTION_UP,
                    keyCode,
                    0,
                    metaState
                )
            )

            clearSwipeState()
            refreshPredictionBar()
            updateAutoShift()
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

    private fun executeVoiceEditCommand(
        command: VoiceEditCommand
    ) {
        if (sensitiveField) {
            root?.setStatus(
                "Voice editing is disabled in sensitive fields."
            )
            return
        }

        when (command) {
            VoiceEditCommand.DeleteLastWord -> {
                val changed =
                    deleteLastWordBeforeCursor()

                root?.setStatus(
                    if (changed) {
                        "Deleted last word."
                    } else {
                        "No word to delete."
                    }
                )
            }

            VoiceEditCommand.DeleteLastSentence -> {
                val changed =
                    deleteLastSentenceBeforeCursor()

                root?.setStatus(
                    if (changed) {
                        "Deleted last sentence."
                    } else {
                        "No sentence to delete."
                    }
                )
            }

            VoiceEditCommand.NewLine -> {
                currentInputConnection
                    ?.commitText(
                        "\n",
                        1
                    )
                root?.setStatus(
                    "Inserted new line."
                )
            }

            VoiceEditCommand.SelectAll -> {
                performEditorContextAction(
                    android.R.id.selectAll,
                    "Selected all text."
                )
            }

            VoiceEditCommand.Copy -> {
                performEditorContextAction(
                    android.R.id.copy,
                    "Copied selection."
                )
            }

            VoiceEditCommand.Cut -> {
                performEditorContextAction(
                    android.R.id.cut,
                    "Cut selection."
                )
            }

            VoiceEditCommand.Paste -> {
                performEditorContextAction(
                    android.R.id.paste,
                    "Pasted clipboard."
                )
            }

            VoiceEditCommand.UndoLastSwipe -> {
                if (lastSwipeWord != null) {
                    callbacks.onBackspace()
                    root?.setStatus(
                        "Undid last swipe."
                    )
                } else {
                    root?.setStatus(
                        "No recent swipe word to undo."
                    )
                }
            }

            is VoiceEditCommand.Replace -> {
                val changed =
                    replaceBeforeCursor(
                        oldText =
                            command.oldText,
                        newText =
                            command.newText
                    )

                root?.setStatus(
                    if (changed) {
                        "Replaced '" +
                            command.oldText +
                            "' with '" +
                            command.newText +
                            "'."
                    } else {
                        "Could not find '" +
                            command.oldText +
                            "' before the cursor."
                    }
                )
            }

            is VoiceEditCommand.TranslateSelection -> {
                translateSelectedTextTo(
                    command.targetLanguageTag
                )
                return
            }
        }

        clearSwipeState()
        val before =
            textBeforeCursor()
        refreshLanguageHints(before)
        refreshPredictionBar()
    }

    private fun deleteLastWordBeforeCursor(): Boolean {
        val connection =
            currentInputConnection
                ?: return false

        val before =
            connection
                .getTextBeforeCursor(
                    1600,
                    0
                )
                ?.toString()
                .orEmpty()

        val match =
            Regex(
                """[\p{L}\p{N}'-]+\s*$"""
            )
                .find(before)
                ?: return false

        val count =
            before.length -
                match.range.first

        return connection
            .deleteSurroundingText(
                count,
                0
            )
    }

    private fun deleteLastSentenceBeforeCursor(): Boolean {
        val connection =
            currentInputConnection
                ?: return false

        val before =
            connection
                .getTextBeforeCursor(
                    2000,
                    0
                )
                ?.toString()
                .orEmpty()

        if (before.isBlank()) {
            return false
        }

        val trimmed =
            before.trimEnd()

        val searchFrom =
            (trimmed.length - 2)
                .coerceAtLeast(0)

        val boundary =
            maxOf(
                trimmed.lastIndexOf(
                    '.',
                    startIndex = searchFrom
                ),
                trimmed.lastIndexOf(
                    '!',
                    startIndex = searchFrom
                ),
                trimmed.lastIndexOf(
                    '?',
                    startIndex = searchFrom
                ),
                trimmed.lastIndexOf(
                    '\n',
                    startIndex = searchFrom
                )
            )

        val start =
            (boundary + 1)
                .coerceAtLeast(0)

        val count =
            before.length -
                start

        if (count <= 0) {
            return false
        }

        return connection
            .deleteSurroundingText(
                count,
                0
            )
    }

    private fun replaceBeforeCursor(
        oldText: String,
        newText: String
    ): Boolean {
        val connection =
            currentInputConnection
                ?: return false

        val before =
            connection
                .getTextBeforeCursor(
                    2000,
                    0
                )
                ?.toString()
                .orEmpty()

        val match =
            Regex(
                Regex.escape(
                    oldText
                ),
                RegexOption.IGNORE_CASE
            )
                .findAll(before)
                .lastOrNull()
                ?: return false

        val suffix =
            before.substring(
                match.range.last + 1
            )

        val deleteCount =
            before.length -
                match.range.first

        if (
            !connection
                .deleteSurroundingText(
                    deleteCount,
                    0
                )
        ) {
            return false
        }

        val inserted =
            newText +
                suffix

        connection.commitText(
            inserted,
            1
        )

        if (!sensitiveField) {
            editTimeline.record(
                deletedText =
                    oldText +
                        suffix,
                insertedText =
                    inserted,
                source =
                    "Voice replace"
            )
            refreshEditHistoryState()
        }

        return true
    }

    private fun performEditorContextAction(
        actionId: Int,
        successMessage: String
    ) {
        val handled =
            currentInputConnection
                ?.performContextMenuAction(
                    actionId
                )
                ?: false

        root?.setStatus(
            if (handled) {
                successMessage
            } else {
                "This editor did not accept the voice editing action."
            }
        )
    }

    private fun translateSelectedTextTo(
        targetLanguageTag: String
    ) {
        val selected =
            currentInputConnection
                ?.getSelectedText(0)
                ?.toString()
                .orEmpty()

        if (selected.isBlank()) {
            root?.setStatus(
                "Select text before using the translate voice command."
            )
            return
        }

        Prefs.setTargetLanguage(
            this,
            targetLanguageTag
        )

        root?.setStatus(
            "Voice command: translating → " +
                targetLanguageTag.uppercase() +
                "…"
        )

        translationEngine.translate(
            selected,
            targetLanguageTag
        ) { result ->
            result.onSuccess { translated ->
                currentInputConnection
                    ?.commitText(
                        translated,
                        1
                    )

                root?.showKeyboard()
                root?.setStatus(
                    "Voice command translated selection → " +
                        targetLanguageTag.uppercase() +
                        "."
                )
                refreshLanguageHints(
                    translated
                )
                refreshPredictionBar()
            }.onFailure {
                root?.setStatus(
                    it.message
                        ?: "Voice translation command failed."
                )
            }
        }
    }

    private fun performTimelineUndo() {
        if (sensitiveField) {
            root?.setStatus(
                "Correction history is disabled in sensitive fields."
            )
            return
        }

        val connection =
            currentInputConnection
                ?: return

        val plan =
            editTimeline.planUndo(
                textBeforeCursor()
            )

        if (plan == null) {
            root?.setStatus(
                if (editTimeline.canUndo) {
                    "Undo paused: cursor context changed. Move the cursor back to the corrected text first."
                } else {
                    "Nothing to undo in this input session."
                }
            )
            refreshEditHistoryState()
            return
        }

        val deleted =
            if (
                plan.deleteUtf16Count >
                0
            ) {
                connection
                    .deleteSurroundingText(
                        plan.deleteUtf16Count,
                        0
                    )
            } else {
                true
            }

        if (!deleted) {
            root?.setStatus(
                "The editor rejected the undo operation."
            )
            return
        }

        connection.commitText(
            plan.insertText,
            1
        )

        editTimeline.completeUndo(
            plan.entry.id
        )
        clearSwipeState()
        refreshEditHistoryState()
        refreshPredictionBar()
        updateAutoShift()

        root?.setStatus(
            "Undo: " +
                plan.entry.summary
        )
    }

    private fun performTimelineRedo() {
        if (sensitiveField) {
            root?.setStatus(
                "Correction history is disabled in sensitive fields."
            )
            return
        }

        val connection =
            currentInputConnection
                ?: return

        val plan =
            editTimeline.planRedo(
                textBeforeCursor()
            )

        if (plan == null) {
            root?.setStatus(
                if (editTimeline.canRedo) {
                    "Redo paused: cursor context changed."
                } else {
                    "Nothing to redo."
                }
            )
            refreshEditHistoryState()
            return
        }

        val deleted =
            if (
                plan.deleteUtf16Count >
                0
            ) {
                connection
                    .deleteSurroundingText(
                        plan.deleteUtf16Count,
                        0
                    )
            } else {
                true
            }

        if (!deleted) {
            root?.setStatus(
                "The editor rejected the redo operation."
            )
            return
        }

        connection.commitText(
            plan.insertText,
            1
        )

        editTimeline.completeRedo(
            plan.entry.id
        )
        clearSwipeState()
        refreshEditHistoryState()
        refreshPredictionBar()
        updateAutoShift()

        root?.setStatus(
            "Redo: " +
                plan.entry.summary
        )
    }

    private fun refreshEditHistoryState() {
        root?.setEditHistoryState(
            canUndo =
                !sensitiveField &&
                    editTimeline.canUndo,
            canRedo =
                !sensitiveField &&
                    editTimeline.canRedo
        )
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

        val deletedText =
            previous +
                " "
        val insertedText =
            value +
                " "

        connection.deleteSurroundingText(
            deletedText.length,
            0
        )
        connection.commitText(
            insertedText,
            1
        )

        if (
            !sensitiveField &&
            previous != value
        ) {
            editTimeline.record(
                deletedText =
                    deletedText,
                insertedText =
                    insertedText,
                source =
                    if (learn) {
                        "Swipe correction"
                    } else {
                        "Swipe candidate"
                    }
            )
            refreshEditHistoryState()
        }

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
            currentPredictions =
                emptyList()
            root?.clearSuggestions()
            return
        }

        if (
            !KeyboardEditorModeResolver
                .allowsWordPrediction(
                    currentInputEditorInfo
                        ?.inputType
                        ?: 0
                )
        ) {
            currentPredictions =
                emptyList()
            root?.clearSuggestions()
            return
        }

        val snapshot = currentContextSnapshot()
        val before = snapshot.beforeCursor

        val layoutLanguage =
            KeyboardLayoutProfiles.byId(
                Prefs.keyboardLayoutId(this)
            ).languageTag
        val inputLanguage =
            CodeSwitchLanguageResolver.primaryInputLanguage(
                contextText = before,
                detectedLanguages = languageHints,
                currentToken = currentToken(before),
                fallbackLanguage = layoutLanguage
            )

        val context = PredictionContext(
            beforeCursor = before,
            languageHints =
                (
                    listOfNotNull(inputLanguage) +
                        languageHints
                ).distinct(),
            inputLanguageTag = inputLanguage,
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
            neuralModelManager.activeModelPath() != null &&
            currentToken(before).isBlank()
        ) {
            val backend =
                neuralPredictionBackend
                    ?: NeuralBackendFactory
                        .create(neuralModelManager)
                        .also {
                            neuralPredictionBackend = it
                        }

            backend.predict(
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
                            maxSuggestions = 6,
                            inputLanguageTag =
                                context.inputLanguageTag
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

    @TargetApi(34)
    private fun tryPerformHandwritingEditGesture(): Boolean {
        if (
            Build.VERSION.SDK_INT <
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            connectionlessHandwriting ||
            sensitiveField
        ) {
            handwritingGesturePoints.clear()
            return false
        }

        val points =
            handwritingGesturePoints
                .toList()

        handwritingGesturePoints.clear()

        val scratch =
            ScratchDeleteGestureClassifier
                .classify(
                    points = points,
                    density =
                        resources
                            .displayMetrics
                            .density
                )

        if (scratch != null) {
            val supported =
                currentInputEditorInfo
                    ?.supportedHandwritingGestures
                    ?.contains(
                        DeleteGesture::class.java
                    ) == true

            if (!supported) {
                return false
            }

            val gesture =
                DeleteGesture.Builder()
                    .setDeletionArea(
                        scratch.bounds
                    )
                    .setGranularity(
                        HandwritingGesture
                            .GRANULARITY_WORD
                    )
                    .build()

            return performHandwritingEditGesture(
                gesture,
                successMessage =
                    "Scratch-out gesture deleted text."
            )
        }

        val extended =
            ExtendedHandwritingGestureClassifier
                .classify(
                    points = points,
                    density =
                        resources
                            .displayMetrics
                            .density
                )
                ?: return false

        val supported =
            currentInputEditorInfo
                ?.supportedHandwritingGestures
                .orEmpty()

        val gesture: HandwritingGesture =
            when (extended) {
                is ExtendedHandwritingGesture.SelectArea -> {
                    if (
                        !supported.contains(
                            SelectGesture::class.java
                        )
                    ) {
                        return false
                    }

                    SelectGesture.Builder()
                        .setSelectionArea(
                            extended.bounds
                        )
                        .setGranularity(
                            HandwritingGesture
                                .GRANULARITY_WORD
                        )
                        .build()
                }

                is ExtendedHandwritingGesture.JoinOrSplit -> {
                    if (
                        !supported.contains(
                            JoinOrSplitGesture::class.java
                        )
                    ) {
                        return false
                    }

                    JoinOrSplitGesture.Builder()
                        .setJoinOrSplitPoint(
                            extended.point
                        )
                        .build()
                }

                is ExtendedHandwritingGesture.RemoveSpace -> {
                    if (
                        !supported.contains(
                            RemoveSpaceGesture::class.java
                        )
                    ) {
                        return false
                    }

                    RemoveSpaceGesture.Builder()
                        .setPoints(
                            extended.start,
                            extended.end
                        )
                        .build()
                }
            }

        val message =
            when (extended) {
                is ExtendedHandwritingGesture.SelectArea ->
                    "Circle gesture selected text."
                is ExtendedHandwritingGesture.JoinOrSplit ->
                    "Vertical gesture joined or split text."
                is ExtendedHandwritingGesture.RemoveSpace ->
                    "Horizontal gesture removed whitespace."
            }

        return performHandwritingEditGesture(
            gesture,
            message
        )
    }

    @TargetApi(34)
    private fun performHandwritingEditGesture(
        gesture: HandwritingGesture,
        successMessage: String
    ): Boolean {
        val connection =
            currentInputConnection
                ?: return false

        systemHandwritingView
            ?.discardLastStroke()

        connection.performHandwritingGesture(
            gesture,
            mainExecutor
        ) { result ->
            root?.setStatus(
                when (result) {
                    android.view.inputmethod
                        .InputConnection
                        .HANDWRITING_GESTURE_RESULT_SUCCESS ->
                        successMessage

                    android.view.inputmethod
                        .InputConnection
                        .HANDWRITING_GESTURE_RESULT_UNSUPPORTED ->
                        "This editor does not support that handwriting gesture."

                    else ->
                        "Handwriting gesture was not applied."
                }
            )

            if (
                result ==
                android.view.inputmethod
                    .InputConnection
                    .HANDWRITING_GESTURE_RESULT_SUCCESS
            ) {
                refreshPredictionBar()
                updateAutoShift()
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

    private fun styleImeSystemBars() {
        val theme =
            KeyboardThemes.byId(
                Prefs.keyboardThemeId(
                    this
                )
            )

        val imeWindow =
            window
                ?.window
                ?: return

        imeWindow.navigationBarColor =
            theme.background

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.P
        ) {
            imeWindow.navigationBarDividerColor =
                theme.background
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.R
        ) {
            imeWindow.insetsController
                ?.setSystemBarsAppearance(
                    0,
                    android.view.WindowInsetsController
                        .APPEARANCE_LIGHT_NAVIGATION_BARS
                )
        }

        @Suppress("DEPRECATION")
        run {
            imeWindow.decorView.systemUiVisibility =
                imeWindow.decorView.systemUiVisibility and
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                        .inv()
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            imeWindow.isNavigationBarContrastEnforced =
                false
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

        val requestGeneration =
            ++languageDetectionGeneration
        val sample =
            text.takeLast(700)

        translationEngine.identifyLikelyLanguages(sample) { detected ->
            if (
                requestGeneration !=
                languageDetectionGeneration
            ) {
                return@identifyLikelyLanguages
            }

            if (detected.isNotEmpty()) {
                languageHints =
                    detected
                        .map {
                            it.substringBefore('-')
                                .lowercase()
                        }
                        .distinct()
                refreshPredictionBar()
            }
        }
    }

    private fun textBeforeCursor(): String =
        currentInputConnection
            ?.getTextBeforeCursor(1600, 0)
            ?.toString()
            .orEmpty()

    private fun updateAutoShift() {
        val inputType =
            currentInputEditorInfo
                ?.inputType
                ?: 0

        val mode =
            KeyboardEditorModeResolver
                .fromInputType(
                    inputType
                )

        if (
            mode !=
            KeyboardEditorMode.TEXT
        ) {
            root?.setAutoShift(
                false
            )
            return
        }

        val before =
            textBeforeCursor()
        val trimmed =
            before.trimEnd()

        val shouldShift =
            trimmed.isEmpty() ||
                trimmed.lastOrNull() in
                setOf(
                    '.',
                    '!',
                    '?',
                    '\n'
                )

        root?.setAutoShift(
            shouldShift
        )
    }

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
