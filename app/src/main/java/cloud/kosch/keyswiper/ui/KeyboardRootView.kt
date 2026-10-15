package cloud.kosch.keyswiper.ui

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import cloud.kosch.keyswiper.clipboard.ClipboardEntry
import cloud.kosch.keyswiper.input.EditTimelineEntry
import cloud.kosch.keyswiper.input.SwipeTrace
import cloud.kosch.keyswiper.input.SwipeDecoder
import cloud.kosch.keyswiper.input.HoldAcceleration
import cloud.kosch.keyswiper.input.DeleteUnit
import cloud.kosch.keyswiper.input.CursorMovement
import cloud.kosch.keyswiper.prediction.PredictionKind
import cloud.kosch.keyswiper.prediction.PredictionSuggestion
import cloud.kosch.keyswiper.settings.Prefs
import cloud.kosch.keyswiper.voice.VoicePhase

class KeyboardRootView(
    context: Context
) : LinearLayout(context) {

    interface Callbacks {
        fun onCharacter(value: Char)
        fun onSwipe(trace: SwipeTrace)
        fun onBackspace()
        fun onBackspaceRepeat(elapsedMs: Long) {}
        fun onBackspaceHoldEnd() {}
        fun onSpace()
        fun onCursorMove(steps: Int) {}
        fun onCursorGesture(active: Boolean) {}
        fun onEnter()
        fun onCandidate(value: String)
        fun onPrediction(
            suggestion: PredictionSuggestion
        )
        fun onTranslationPanelRequested()
        fun onTranslationTargetSelected(
            languageTag: String
        )
        fun onTranslate()
        fun onVoice()
        fun onVoiceCommand()
        fun onVoiceCancel() {}
        fun onSpeak() {}
        fun onStopSpeaking() {}
        fun onUndoEdit()
        fun onRedoEdit()
        fun onEditTimelineRequested()
        fun onDeveloperText(value: String)
        fun onDeveloperKeyCode(
            keyCode: Int,
            ctrl: Boolean = false
        )
        fun onClipboard()
        fun onClipboardInsert(id: String)
        fun onClipboardSearch(query: String)
        fun onClipboardTogglePin(id: String)
        fun onClipboardToggleSaved(id: String) {}
        fun onClipboardCaptureSelection() {}
        fun onClipboardImportSystem() {}
        fun onSensitivePanelChanged(active: Boolean) {}
        fun onClipboardDelete(id: String)
        fun onClipboardDeleteMany(ids: Set<String>) {}
        fun onClipboardUndoDelete() {}
        fun onClipboardCopy(id: String) {}
        fun onClipboardSave(id: String?, value: String) {}
        fun onClipboardClearUnpinned()
        fun onClipboardExpiryChanged(minutes: Long)
        fun onEmoji(value: String)
        fun onHandwritingRequested()
        fun onHandwritingRecognize(
            view: HandwritingPadView
        )
        fun onSettings()
        fun onStylusPrimary()
        fun onStylusSecondary()
    }

    private var clipboardEditor: EditText? = null
    private var clipboardPanel: ClipboardPanelView? = null
    private val clipboardDecoder by lazy { SwipeDecoder() }
    var callbacks: Callbacks? = null
        get() {
            val delegate = field ?: return null
            val panel = clipboardPanel ?: return delegate
            val target = clipboardEditor?.takeIf { it.parent != null } ?: panel.searchEditor
            return object : Callbacks by delegate {
                override fun onCharacter(value: Char) = editClipboard(target, value.toString())
                override fun onDeveloperText(value: String) = editClipboard(target, value)
                override fun onEmoji(value: String) = editClipboard(target, value)
                override fun onBackspace() = deleteClipboard(target, 0)
                override fun onBackspaceRepeat(elapsedMs: Long) = deleteClipboard(target, elapsedMs)
                override fun onBackspaceHoldEnd() = Unit
                override fun onSpace() = editClipboard(target, " ")
                override fun onCursorMove(steps: Int) {
                    target.setSelection(CursorMovement.offset(target.text.toString(), target.selectionStart, target.selectionEnd, steps))
                }
                override fun onCursorGesture(active: Boolean) = Unit
                override fun onEnter() { if (target !== panel.searchEditor) editClipboard(target, "\n") }
                override fun onSwipe(trace: SwipeTrace) {
                    val start = minOf(target.selectionStart, target.selectionEnd).coerceAtLeast(0)
                    clipboardDecoder.decode(trace, target.text.take(start).toString(), listOf(layoutProfile.languageTag))
                        .firstOrNull()?.let { editClipboard(target, "$it ") }
                }
                override fun onCandidate(value: String) = editClipboard(target, "$value ")
                override fun onPrediction(suggestion: PredictionSuggestion) = editClipboard(target, "${suggestion.commitText} ")
            }
        }

    private fun editClipboard(target: EditText, value: String) {
        val start = minOf(target.selectionStart, target.selectionEnd).coerceAtLeast(0)
        val end = maxOf(target.selectionStart, target.selectionEnd).coerceAtLeast(start)
        target.text.replace(start, end, value)
        target.setSelection(start + value.length)
    }

    private fun deleteClipboard(target: EditText, elapsedMs: Long) {
        val start = minOf(target.selectionStart, target.selectionEnd).coerceAtLeast(0)
        val end = maxOf(target.selectionStart, target.selectionEnd).coerceAtLeast(start)
        val amount = if (start == end) HoldAcceleration.deleteLength(target.text.take(start).toString(),
            HoldAcceleration.step(elapsedMs, Prefs.advancedHoldDeleteEnabled(context)).unit) else 0
        target.text.delete(start - amount, end)
        target.setSelection(start - amount)
    }

    private val density =
        resources.displayMetrics.density

    private var theme =
        KeyboardThemes.byId(
            Prefs.keyboardThemeId(
                context
            )
        )

    private fun dp(value: Int) =
        (value * density).toInt()

    private var statusToast: Toast? = null
    private var systemBottomInsetPx = 0

    private lateinit var toolbarView: View
    private lateinit var predictionRowView: LinearLayout
    private val suggestionButtons = mutableListOf<Button>()
    private val moreSuggestionsButton = Button(context)
    private var suggestionModel = SuggestionBarModel.from(emptyList())
    private var suggestionAction: (PredictionSuggestion) -> Unit = {}
    private var suggestionInteractions = 0
    private data class SuggestionUpdate(val values: List<PredictionSuggestion>, val action: (PredictionSuggestion) -> Unit)
    private var pendingSuggestionUpdate: SuggestionUpdate? = null
    private var keyboardContentHeightPx = 1
    private var spaceButton: Button? = null
    private var spaceController: SpacebarCursorController? = null
    private var numericProfile: NumericKeypadProfile? = null
    private var sensitivePanelOpen = false
    private var voicePanelOpen = false
    private var privateInput = false

    private val suggestions =
        LinearLayout(context)

    private val undoButton =
        Button(context)

    private val redoButton =
        Button(context)

    private val handwritingPanelContainer = LinearLayout(context).apply {
        orientation = VERTICAL
        visibility = GONE
    }

    private val content =
        FrameLayout(context)

    private val keyboardPanel =
        LinearLayout(context)

    private val keyboardSurface =
        KeyboardSurface(context)

    private val shiftState = ShiftState()
    private var shiftButton: Button? = null
    private var symbolMode = false
    private var symbolPage = 0
    private val textAlternatives = KeyAlternativesPopup(context) { value ->
        consumeShift(value)
        callbacks?.onDeveloperText(value)
    }
    private val emojiAlternatives = KeyAlternativesPopup(context) { callbacks?.onEmoji(it) }
    private val languageAlternatives = KeyAlternativesPopup(context) { tag ->
        KeyboardLayoutProfiles.all.firstOrNull { it.languageTag.equals(tag, true) }?.let { next ->
            Prefs.setKeyboardLayoutId(context, next.id)
            layoutProfile = next
            shiftState.reset()
            symbolMode = false
            rebuildKeyboardPanel()
            setStatus("Layout: ${next.label}")
        }
    }
    private var oneHandMode =
        Prefs.oneHandMode(context)
    private var editorMode =
        KeyboardEditorMode.TEXT
    private var layoutProfile =
        KeyboardLayoutProfiles.byId(
            Prefs.keyboardLayoutId(context)
        )

    init {
        orientation = VERTICAL
        filterTouchesWhenObscured = true
        setBackgroundColor(
            theme.background
        )

        addView(handwritingPanelContainer, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ))
        toolbarView = buildToolbar()
        addView(
            toolbarView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(30)
            )
        )

        suggestions.apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.surface)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        repeat(3) { index ->
            val button = StableSuggestionButton(context, ::onSuggestionInteraction).apply {
                isAllCaps = false
                textSize = 16f
                minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(4), 0, dp(4), 0)
                isEnabled = false
                setOnClickListener { suggestionModel.words.getOrNull(index)?.let(suggestionAction) }
                setOnLongClickListener {
                    suggestionModel.words.getOrNull(index)?.let {
                        Toast.makeText(context, it.commitText, Toast.LENGTH_LONG).show()
                    }
                    true
                }
            }
            suggestionButtons.add(button)
            suggestions.addView(button, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
        }
        moreSuggestionsButton.apply {
            text = "⌄"
            textSize = 18f
            isAllCaps = false
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            visibility = INVISIBLE
            contentDescription = "Satzvorschläge und weitere Wörter anzeigen"
            setOnClickListener { showSuggestionPanel() }
        }
        suggestions.addView(moreSuggestionsButton, LayoutParams(dp(30), LayoutParams.MATCH_PARENT))

        undoButton.apply {
            text = "↶"
            textSize = 18f
            isAllCaps = false
            setTextColor(
                theme.accent
            )
            background =
                buttonBackground(
                    special = true,
                    subtle = true
                )
            minWidth = 0
            minimumWidth = 0
            isEnabled = false
            contentDescription =
                "Undo last KeySwiper correction"
            setOnClickListener {
                callbacks
                    ?.onUndoEdit()
            }
            setOnLongClickListener {
                callbacks
                    ?.onEditTimelineRequested()
                true
            }
        }

        redoButton.apply {
            text = "↷"
            textSize = 18f
            isAllCaps = false
            setTextColor(
                theme.accent
            )
            background =
                buttonBackground(
                    special = true,
                    subtle = true
                )
            minWidth = 0
            minimumWidth = 0
            isEnabled = false
            contentDescription =
                "Redo last KeySwiper correction"
            setOnClickListener {
                callbacks
                    ?.onRedoEdit()
            }
        }

        predictionRowView =
            LinearLayout(context).apply {
                orientation =
                    HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
                setBackgroundColor(
                    theme.surface
                )
            }

        predictionRowView.addView(
            undoButton,
            LayoutParams(
                dp(44),
                LayoutParams.MATCH_PARENT
            )
        )

        predictionRowView.addView(
            suggestions,
            LayoutParams(
                0,
                LayoutParams.MATCH_PARENT,
                1f
            )
        )

        predictionRowView.addView(
            redoButton,
            LayoutParams(
                dp(44),
                LayoutParams.MATCH_PARENT
            )
        )

        addView(
            predictionRowView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(36)
            )
        )

        keyboardPanel.orientation =
            VERTICAL
        keyboardPanel.setBackgroundColor(
            theme.background
        )

        keyboardSurface.listener =
            object :
                KeyboardSurface.Listener {

                override fun onTap(character: Char) {
                    consumeShift(character.toString())
                    callbacks?.onCharacter(character)
                }

                override fun onText(value: String) {
                    consumeShift(value)
                    callbacks?.onDeveloperText(value)
                }

                override fun onSwipe(
                    trace: SwipeTrace
                ) {
                    callbacks
                        ?.onSwipe(trace)
                }

                override fun onBackspace() {
                    callbacks
                        ?.onBackspace()
                }

                override fun onBackspaceRepeat(elapsedMs: Long) {
                    val unit = if (privateInput || editorMode == KeyboardEditorMode.NUMBER) DeleteUnit.CHARACTER
                        else HoldAcceleration.step(elapsedMs, Prefs.advancedHoldDeleteEnabled(context)).unit
                    showDeletionUnit(unit)
                    callbacks?.onBackspaceRepeat(elapsedMs)
                }
                override fun onBackspaceHoldEnd() { showDeletionUnit(null); callbacks?.onBackspaceHoldEnd() }
                override fun onEnter() { callbacks?.onEnter() }
                override fun onShift() { toggleShift() }

                override fun onStylusPrimaryButton() {
                    callbacks
                        ?.onStylusPrimary()
                }

                override fun onStylusSecondaryButton() {
                    callbacks
                        ?.onStylusSecondary()
                }
            }

        addView(
            content,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        showKeyboard()
        StylusUi.usePointerInput(this)
    }

    private fun buildToolbar(): View {
        val row =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(
                    dp(5),
                    dp(3),
                    dp(5),
                    dp(3)
                )
                setBackgroundColor(
                    theme.background
                )
            }

        fun tool(
            label: String,
            longAction: (() -> Unit)? = null,
            action: () -> Unit
        ): Button =
            Button(context).apply {
                text = label
                textSize = 13f
                isAllCaps = false
                setTextColor(
                    theme.textSecondary
                )
                background =
                    buttonBackground(
                        special = false,
                        subtle = true
                    )
                minWidth = 0
                minimumWidth = 0
                setPadding(
                    dp(4),
                    0,
                    dp(4),
                    0
                )
                setOnClickListener {
                    action()
                }

                if (longAction != null) {
                    setOnLongClickListener {
                        longAction()
                        true
                    }
                }
            }

        listOf(
            tool(
                "⌨",
                longAction = {
                    cycleOneHandMode()
                },
                action = {
                    showKeyboard()
                }
            ),
            tool("🌐") {
                callbacks
                    ?.onTranslationPanelRequested()
            },
            tool(
                "🎙",
                action = {
                    callbacks?.onVoice()
                },
                longAction = {
                    callbacks?.onVoiceCommand()
                }
            ),
            tool("📋") {
                callbacks?.onClipboard()
            },
            tool("✍") {
                callbacks
                    ?.onHandwritingRequested()
            },
            tool("</>") {
                showDeveloperPanel()
            },
            tool("⚙") {
                callbacks?.onSettings()
            }
        ).forEach {
            row.addView(
                it,
                LayoutParams(
                    0,
                    LayoutParams.MATCH_PARENT,
                    1f
                )
            )
        }

        return row
    }

    private fun rebuildKeyboardPanel() {
        spaceController?.cancel()
        spaceController = null
        spaceButton = null
        dismissAlternatives()
        shiftButton = null
        keyboardPanel.removeAllViews()

        layoutProfile =
            KeyboardLayoutProfiles.byId(
                Prefs.keyboardLayoutId(
                    context
                )
            )

        val sizing =
            KeyboardSizing.calculate(
                screenHeightPx =
                    resources.displayMetrics.heightPixels,
                density = density,
                symbolMode = symbolMode,
                bottomInsetPx = systemBottomInsetPx,
                contentVerticalPaddingPx = dp(4)
            )

        setPadding(
            paddingLeft,
            paddingTop,
            paddingRight,
            sizing.bottomInsetPx
        )
        val numeric = editorMode == KeyboardEditorMode.NUMBER
        toolbarView.visibility = if (numeric) GONE else VISIBLE
        predictionRowView.visibility = if (numeric) GONE else VISIBLE
        toolbarView.layoutParams =
            toolbarView.layoutParams.apply {
                height = sizing.toolbarHeightPx
            }
        predictionRowView.layoutParams =
            predictionRowView.layoutParams.apply {
                height = sizing.predictionHeightPx
            }

        if (numeric) keyboardSurface.setNumericLayout(numericProfile ?: NumericKeypadProfile())
        else keyboardSurface.setLayout(profile = layoutProfile, symbols = symbolMode, page = symbolPage)
        keyboardSurface.shifted = shiftState.shifted && !symbolMode
        keyboardSurface.capsLocked = shiftState.locked && !symbolMode

        keyboardContentHeightPx = if (numeric) sizing.toolbarHeightPx + sizing.predictionHeightPx +
            sizing.surfaceHeightPx + sizing.bottomRowHeightPx else sizing.surfaceHeightPx + sizing.bottomRowHeightPx
        keyboardPanel.addView(
            keyboardSurface,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                if (numeric) keyboardContentHeightPx else sizing.surfaceHeightPx
            )
        )

        if (!numeric) keyboardPanel.addView(
            buildBottomRow(),
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                sizing.bottomRowHeightPx
            )
        )
        StylusUi.usePointerInput(keyboardPanel)
    }

    fun setSystemBottomInset(
        valuePx: Int
    ) {
        val normalized = valuePx.coerceAtLeast(0)
        if (normalized == systemBottomInsetPx) return

        systemBottomInsetPx = normalized
        rebuildKeyboardPanel()
    }

    private fun compactKey(label: String, action: () -> Unit): Button = Button(context).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setTextColor(theme.textPrimary)
        background = buttonBackground()
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(3), 0, dp(3), 0)
        setOnClickListener { action() }
    }

    fun applyInputCase(word: String): String = when {
        shiftState.locked -> word.uppercase()
        shiftState.shifted -> word.replaceFirstChar { it.uppercase() }
        else -> word
    }

    /** Must also run after swipe commits and accepting predictions. */
    fun consumeShiftForCommittedText(value: String) {
        shiftState.consume(value)
        updateShiftAppearance()
    }

    private fun consumeShift(value: String) = consumeShiftForCommittedText(value)

    private fun toggleShift() {
        shiftState.tap()
        updateShiftAppearance()
    }

    private fun updateShiftAppearance() {
        val shifted = shiftState.shifted
        val locked = shiftState.locked
        keyboardSurface.shifted = shifted && !symbolMode
        keyboardSurface.capsLocked = locked && !symbolMode
        shiftButton?.apply {
            text = if (locked) "⇪" else "⇧"
            contentDescription = when {
                locked -> "Dauerhafte Großschreibung aktiv; tippen für Kleinbuchstaben"
                shifted -> "Großschreibung aktiv; tippen für Feststelltaste"
                else -> "Kleinbuchstaben aktiv; tippen für Großschreibung"
            }
            setTextColor(if (shifted) theme.accent else theme.textPrimary)
            background = buttonBackground(special = shifted)
            isSelected = shifted
        }
    }

    private fun buildBottomRow(): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(3), dp(4), dp(4))
        }
        fun key(label: String, weight: Float = 1f, action: () -> Unit): Button {
            val button = compactKey(label, action)
            row.addView(button, LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
            return button
        }
        fun character(value: Char) {
            val button = key(value.toString(), 0.75f) { callbacks?.onCharacter(value) }
            textAlternatives.attach(button) { KeyAlternatives.forKey(value, layoutProfile.id, false, true) }
        }
        if (symbolMode) {
            if (editorMode != KeyboardEditorMode.NUMBER) {
                key("ABC", 1.05f) { symbolMode = false; rebuildKeyboardPanel() }
            }
            key(if (symbolPage == 0) "#+=" else "123", 1.05f) {
                symbolPage = 1 - symbolPage
                rebuildKeyboardPanel()
            }.contentDescription = "Weitere Zahlen und Symbole"
        } else {
            key("?123", 1.05f) {
                symbolMode = true
                symbolPage = 0
                rebuildKeyboardPanel()
            }.contentDescription = "Zahlen und Symbole"
        }
        if (editorMode == KeyboardEditorMode.NUMBER) {
            character('-'); character('.')
        } else {
            if (editorMode == KeyboardEditorMode.EMAIL) character('@')
            else if (editorMode == KeyboardEditorMode.URL) character('/')
            else if (symbolMode) character(',')
            val language = key(layoutProfile.languageTag.uppercase(), 0.85f) {}
            language.contentDescription = "Tastatursprache auswählen"
            language.setOnClickListener {
                languageAlternatives.show(language, listOf(layoutProfile.languageTag.uppercase()) +
                    KeyboardLayoutProfiles.all.filter { it.id != layoutProfile.id }.map { it.languageTag.uppercase() })
            }
            key("😀", 0.85f) { showEmojiPanel() }.contentDescription = "Emojis und Varianten"
            val space = key("Leerzeichen", 4.2f) { callbacks?.onSpace() }
            spaceButton = space
            space.textSize = 13f
            space.contentDescription = "Leerzeichen; horizontal wischen zum Bewegen des Cursors"
            spaceController = SpacebarCursorController(space,
                onMove = { callbacks?.onCursorMove(it) },
                onGesture = { active ->
                    space.text = if (active) "↔ Cursor" else "Leerzeichen"
                    callbacks?.onCursorGesture(active)
                }).also { it.attach() }
            if (symbolMode || editorMode != KeyboardEditorMode.TEXT) character('.')
        }
        key("↵", 1f) { callbacks?.onEnter() }.contentDescription = "Eingabe"
        return row
    }

    fun cancelActiveGestures() {
        spaceController?.cancel()
        keyboardSurface.cancelActiveGesture()
        dismissAlternatives()
    }

    private fun dismissAlternatives() {
        textAlternatives.dismiss()
        emojiAlternatives.dismiss()
        languageAlternatives.dismiss()
    }

    override fun onDetachedFromWindow() {
        dismissAlternatives()
        super.onDetachedFromWindow()
    }

    fun setEditorMode(
        mode: KeyboardEditorMode,
        inputType: Int = if (mode == KeyboardEditorMode.NUMBER) android.text.InputType.TYPE_CLASS_NUMBER
            else android.text.InputType.TYPE_CLASS_TEXT
    ) {
        cancelActiveGestures()
        clearSuggestions()
        editorMode = mode
        numericProfile = NumericKeypadProfile.fromInputType(inputType)
        shiftState.beginEditorSession()
        symbolPage = 0
        symbolMode =
            mode ==
                KeyboardEditorMode.NUMBER

        rebuildKeyboardPanel()
    }

    fun setAutoShift(enabled: Boolean) {
        shiftState.setAutomatic(enabled && editorMode == KeyboardEditorMode.TEXT)
        updateShiftAppearance()
    }

    private fun roundedDrawable(
        color: Int,
        radiusDp: Float,
        strokeColor: Int? = null,
        strokeDp: Int = 1
    ): GradientDrawable =
        GradientDrawable().apply {
            shape =
                GradientDrawable
                    .RECTANGLE
            cornerRadius =
                radiusDp *
                    density
            setColor(
                color
            )

            if (
                strokeColor != null
            ) {
                setStroke(
                    dp(strokeDp),
                    strokeColor
                )
            }
        }

    private fun buttonBackground(
        special: Boolean = false,
        subtle: Boolean = false
    ): StateListDrawable {
        val normalColor =
            when {
                subtle ->
                    theme.surfaceRaised
                special ->
                    theme.keySpecial
                else ->
                    theme.key
            }

        return StateListDrawable().apply {
            addState(
                intArrayOf(
                    android.R.attr
                        .state_pressed
                ),
                roundedDrawable(
                    theme.keyPressed,
                    theme.keyCornerDp,
                    theme.accentSoft
                )
            )
            addState(
                intArrayOf(),
                roundedDrawable(
                    normalColor,
                    theme.keyCornerDp,
                    if (subtle) {
                        theme.border
                    } else {
                        theme.border
                    }
                )
            )
        }
    }

    fun setStatus(
        message: String?
    ) {
        statusToast?.cancel()
        statusToast = null

        if (!message.isNullOrBlank()) {
            statusToast =
                Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_SHORT
                ).also {
                    it.show()
                }
        }
    }

    fun setPredictions(values: List<PredictionSuggestion>) {
        renderSuggestions(values) { callbacks?.onPrediction(it) }
    }

    private fun showDeletionUnit(unit: DeleteUnit?) {
        spaceButton?.text = when (unit) {
            DeleteUnit.CHARACTER -> "Löschen · Zeichen"
            DeleteUnit.WORD -> "Löschen · Wort"
            DeleteUnit.WORDS -> "Löschen · 3 Wörter"
            DeleteUnit.SENTENCE -> "Löschen · Satz"
            DeleteUnit.PARAGRAPH -> "Löschen · Absatz"
            null -> "Leerzeichen"
        }
    }

    private fun onSuggestionInteraction(active: Boolean) {
        suggestionInteractions = (suggestionInteractions + if (active) 1 else -1).coerceAtLeast(0)
        if (suggestionInteractions == 0) {
            val pending = pendingSuggestionUpdate
            pendingSuggestionUpdate = null
            pending?.let { renderSuggestions(it.values, it.action) }
        }
    }

    private fun renderSuggestions(values: List<PredictionSuggestion>, onChoose: (PredictionSuggestion) -> Unit) {
        if (privateInput || clipboardPanel != null || editorMode == KeyboardEditorMode.NUMBER) { clearSuggestions(); return }
        if (suggestionInteractions > 0) {
            pendingSuggestionUpdate = SuggestionUpdate(values.toList(), onChoose)
            return
        }
        suggestionModel = SuggestionBarModel.from(values)
        suggestionAction = onChoose
        suggestionButtons.forEachIndexed { index, button ->
            val suggestion = suggestionModel.words.getOrNull(index)
            val correction = suggestion?.kind == PredictionKind.CORRECTION
            button.text = suggestion?.commitText.orEmpty()
            button.isEnabled = suggestion != null
            button.alpha = if (suggestion == null) 0f else 1f
            button.setTextColor(if (correction) theme.accent else theme.textPrimary)
            button.typeface = android.graphics.Typeface.create("sans-serif",
                if (index == 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            button.background = roundedDrawable(theme.surfaceRaised, theme.keyCornerDp,
                if (correction) theme.accent else theme.border)
            button.contentDescription = when (suggestion?.kind) {
                PredictionKind.CORRECTION -> "Korrektur: ${suggestion.commitText}"
                PredictionKind.KEEP_TYPED -> "Schreibweise behalten: ${suggestion.commitText}"
                null -> ""
                else -> "Wortvorschlag: ${suggestion.commitText}"
            }
        }
        moreSuggestionsButton.visibility = if (suggestionModel.hasMore) VISIBLE else INVISIBLE
        moreSuggestionsButton.isEnabled = suggestionModel.hasMore
        moreSuggestionsButton.setTextColor(theme.accent)
        moreSuggestionsButton.background = buttonBackground(subtle = true)
        StylusUi.usePointerInput(suggestions)
    }

    private fun showSuggestionPanel() {
        val model = suggestionModel
        val choose = suggestionAction
        if (!model.hasMore) return
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        val list = LinearLayout(context).apply { orientation = VERTICAL }
        fun section(title: String, values: List<PredictionSuggestion>) {
            if (values.isEmpty()) return
            list.addView(TextView(context).apply {
                text = title; textSize = 14f
                setPadding(dp(6), dp(6), dp(6), dp(4))
            })
            values.forEach { suggestion ->
                list.addView(compactKey(suggestion.commitText) {
                    showKeyboard()
                    choose(suggestion)
                }.apply {
                    textSize = 15f
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    contentDescription = "$title: ${suggestion.commitText}"
                    setOnLongClickListener {
                        Toast.makeText(context, suggestion.commitText, Toast.LENGTH_LONG).show(); true
                    }
                }, LayoutParams(LayoutParams.MATCH_PARENT, dp(64)).apply { bottomMargin = dp(3) })
            }
        }
        section("Satzvorschläge", model.phrases)
        section("Weitere Wörter", model.moreWords)
        val scroll = ScrollView(context).apply { addView(list) }
        panel.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        panel.addView(compactKey("ABC · Zur Tastatur") { showKeyboard() },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(36)))
        swapContent(panel, keyboardContentHeightPx)
    }

    fun setSwipeCandidates(values: List<String>) {
        renderSuggestions(values.mapIndexed { index, value ->
            PredictionSuggestion(value, value, PredictionKind.SWIPE_CORRECTION, confidence = (0.96f - index * 0.10f).coerceAtLeast(0.45f))
        }) { callbacks?.onCandidate(it.commitText) }
    }

    fun setPrivateInput(active: Boolean) {
        privateInput = active
        keyboardSurface.swipeEnabled = !active
        if (active) { keyboardSurface.cancelActiveGesture(); clearSuggestions() }
    }

    fun clearSuggestions() {
        pendingSuggestionUpdate = null
        suggestionModel = SuggestionBarModel.from(emptyList())
        suggestionAction = {}
        suggestionButtons.forEach { it.text = ""; it.isEnabled = false; it.alpha = 0f }
        moreSuggestionsButton.visibility = INVISIBLE
        moreSuggestionsButton.isEnabled = false
    }

    fun showKeyboard() {
        if (voicePanelOpen) { voicePanelOpen = false; callbacks?.onVoiceCancel() }
        setSensitivePanel(false)
        clipboardEditor = null
        clipboardPanel = null
        dismissAlternatives()
        handwritingPanelContainer.removeAllViews()
        handwritingPanelContainer.visibility = GONE
        theme =
            KeyboardThemes.byId(
                Prefs.keyboardThemeId(
                    context
                )
            )
        setBackgroundColor(
            theme.background
        )
        applyChromeTheme()
        oneHandMode =
            Prefs.oneHandMode(
                context
            )
        symbolMode =
            editorMode ==
                KeyboardEditorMode.NUMBER
        shiftState.setAutomatic(false)
        rebuildKeyboardPanel()
        attachMainPanel(
            keyboardPanel
        )
    }

    fun setEditHistoryState(
        canUndo: Boolean,
        canRedo: Boolean
    ) {
        undoButton.visibility = if (canUndo) VISIBLE else INVISIBLE
        redoButton.visibility = if (canRedo) VISIBLE else INVISIBLE
        undoButton.isEnabled =
            canUndo
        redoButton.isEnabled =
            canRedo
        undoButton.alpha =
            if (canUndo) 1f else 0.38f
        redoButton.alpha =
            if (canRedo) 1f else 0.38f
    }

    fun showEditTimeline(
        entries: List<EditTimelineEntry>
    ) {
        val panel =
            LinearLayout(context).apply {
                orientation =
                    VERTICAL
                setPadding(
                    dp(8),
                    dp(8),
                    dp(8),
                    dp(8)
                )
            }

        panel.addView(
            TextView(context).apply {
                text =
                    "Autocorrect timeline · newest first"
                textSize = 16f
                setTextColor(
                    Color.WHITE
                )
                setPadding(
                    dp(4),
                    dp(3),
                    dp(4),
                    dp(8)
                )
            }
        )

        if (entries.isEmpty()) {
            panel.addView(
                TextView(context).apply {
                    text =
                        "No KeySwiper correction has been recorded in this input session."
                    setTextColor(
                        Color.WHITE
                    )
                    setPadding(
                        dp(4),
                        dp(12),
                        dp(4),
                        dp(12)
                    )
                }
            )
        } else {
            entries
                .take(12)
                .forEach { entry ->
                    panel.addView(
                        TextView(context).apply {
                            text =
                                "• " +
                                    entry.source +
                                    ": " +
                                    entry.summary
                            setTextColor(
                                Color.WHITE
                            )
                            textSize = 13f
                            setPadding(
                                dp(4),
                                dp(5),
                                dp(4),
                                dp(5)
                            )
                        }
                    )
                }
        }

        val actions =
            LinearLayout(context).apply {
                orientation =
                    HORIZONTAL
            }

        actions.addView(
            Button(context).apply {
                text = "↶ Undo"
                isAllCaps = false
                isEnabled =
                    undoButton.isEnabled
                setOnClickListener {
                    callbacks
                        ?.onUndoEdit()
                }
            },
            LayoutParams(
                0,
                dp(48),
                1f
            )
        )

        actions.addView(
            Button(context).apply {
                text = "↷ Redo"
                isAllCaps = false
                isEnabled =
                    redoButton.isEnabled
                setOnClickListener {
                    callbacks
                        ?.onRedoEdit()
                }
            },
            LayoutParams(
                0,
                dp(48),
                1f
            )
        )

        actions.addView(
            Button(context).apply {
                text = "Keyboard"
                isAllCaps = false
                setOnClickListener {
                    showKeyboard()
                }
            },
            LayoutParams(
                0,
                dp(48),
                1f
            )
        )

        panel.addView(actions)
        swapContent(panel)
    }

    fun showDeveloperPanel() {
        val panel =
            LinearLayout(context).apply {
                orientation =
                    VERTICAL
                setPadding(
                    dp(3),
                    dp(3),
                    dp(3),
                    dp(3)
                )
            }

        fun addRow(
            keys: List<Pair<String, () -> Unit>>
        ) {
            val row =
                LinearLayout(context).apply {
                    orientation =
                        HORIZONTAL
                }

            keys.forEach {
                    (label, action) ->
                row.addView(
                    Button(context).apply {
                        text = label
                        isAllCaps = false
                        textSize =
                            if (
                                label.length > 4
                            ) {
                                11f
                            } else {
                                14f
                            }
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(
                            dp(1),
                            0,
                            dp(1),
                            0
                        )
                        setOnClickListener {
                            action()
                        }
                    },
                    LayoutParams(
                        0,
                        dp(45),
                        1f
                    )
                )
            }

            panel.addView(row)
        }

        fun textKey(
            label: String,
            value: String = label
        ): Pair<String, () -> Unit> =
            label to {
                callbacks
                    ?.onDeveloperText(
                        value
                    )
            }

        fun keyCode(
            label: String,
            code: Int,
            ctrl: Boolean = false
        ): Pair<String, () -> Unit> =
            label to {
                callbacks
                    ?.onDeveloperKeyCode(
                        code,
                        ctrl
                    )
            }

        addRow(
            listOf(
                keyCode(
                    "ESC",
                    android.view.KeyEvent
                        .KEYCODE_ESCAPE
                ),
                keyCode(
                    "TAB",
                    android.view.KeyEvent
                        .KEYCODE_TAB
                ),
                textKey("{"),
                textKey("}"),
                textKey("["),
                textKey("]"),
                textKey("("),
                textKey(")")
            )
        )

        addRow(
            listOf(
                textKey("<"),
                textKey(">"),
                textKey("/"),
                textKey("\\"),
                textKey("|"),
                textKey("~"),
                textKey("`"),
                textKey("\""),
                textKey("'")
            )
        )

        addRow(
            listOf(
                textKey("="),
                textKey("+"),
                textKey("-"),
                textKey("_"),
                textKey(":"),
                textKey(";"),
                textKey("@"),
                textKey("#"),
                textKey("$")
            )
        )

        addRow(
            listOf(
                keyCode(
                    "←",
                    android.view.KeyEvent
                        .KEYCODE_DPAD_LEFT
                ),
                keyCode(
                    "↑",
                    android.view.KeyEvent
                        .KEYCODE_DPAD_UP
                ),
                keyCode(
                    "↓",
                    android.view.KeyEvent
                        .KEYCODE_DPAD_DOWN
                ),
                keyCode(
                    "→",
                    android.view.KeyEvent
                        .KEYCODE_DPAD_RIGHT
                ),
                keyCode(
                    "HOME",
                    android.view.KeyEvent
                        .KEYCODE_MOVE_HOME
                ),
                keyCode(
                    "END",
                    android.view.KeyEvent
                        .KEYCODE_MOVE_END
                )
            )
        )

        addRow(
            listOf(
                keyCode(
                    "Ctrl+A",
                    android.view.KeyEvent
                        .KEYCODE_A,
                    true
                ),
                keyCode(
                    "Ctrl+C",
                    android.view.KeyEvent
                        .KEYCODE_C,
                    true
                ),
                keyCode(
                    "Ctrl+V",
                    android.view.KeyEvent
                        .KEYCODE_V,
                    true
                ),
                keyCode(
                    "Ctrl+X",
                    android.view.KeyEvent
                        .KEYCODE_X,
                    true
                ),
                keyCode(
                    "Ctrl+Z",
                    android.view.KeyEvent
                        .KEYCODE_Z,
                    true
                ),
                keyCode(
                    "Ctrl+Y",
                    android.view.KeyEvent
                        .KEYCODE_Y,
                    true
                )
            )
        )

        val bottom =
            LinearLayout(context).apply {
                orientation =
                    HORIZONTAL
            }

        bottom.addView(
            Button(context).apply {
                text = "ABC"
                isAllCaps = false
                setOnClickListener {
                    showKeyboard()
                }
            },
            LayoutParams(
                0,
                dp(48),
                1f
            )
        )

        bottom.addView(
            Button(context).apply {
                text =
                    "↔ " +
                        oneHandMode.label
                isAllCaps = false
                setOnClickListener {
                    cycleOneHandMode()
                    showDeveloperPanel()
                }
            },
            LayoutParams(
                0,
                dp(48),
                2f
            )
        )

        panel.addView(bottom)

        oneHandMode =
            Prefs.oneHandMode(
                context
            )

        themeAuxiliaryTree(
            panel
        )
        attachMainPanel(panel)
    }

    private fun cycleOneHandMode() {
        oneHandMode =
            Prefs.oneHandMode(
                context
            ).next()

        Prefs.setOneHandMode(
            context,
            oneHandMode
        )

        setStatus(
            "One-hand mode: " +
                oneHandMode.label
        )
        showKeyboard()
    }

    private fun applyChromeTheme() {
        toolbarView.setBackgroundColor(theme.background)
        predictionRowView.setBackgroundColor(theme.surface)
        suggestions.setBackgroundColor(theme.surface)
        keyboardPanel.setBackgroundColor(theme.background)

        themeAuxiliaryTree(toolbarView)

        undoButton.setTextColor(theme.accent)
        redoButton.setTextColor(theme.accent)
        undoButton.background =
            buttonBackground(
                special = true,
                subtle = true
            )
        redoButton.background =
            buttonBackground(
                special = true,
                subtle = true
            )
    }

    private fun attachMainPanel(
        view: View
    ) {
        content.removeAllViews()
        detachFromParent(view)

        oneHandMode =
            Prefs.oneHandMode(
                context
            )

        val targetWidth =
            if (
                oneHandMode ==
                OneHandMode.OFF
            ) {
                LayoutParams.MATCH_PARENT
            } else {
                (
                    resources
                        .displayMetrics
                        .widthPixels *
                        0.84f
                    ).toInt()
            }

        val params =
            FrameLayout.LayoutParams(
                targetWidth,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity =
                    when (oneHandMode) {
                        OneHandMode.LEFT ->
                            Gravity.START
                        OneHandMode.RIGHT ->
                            Gravity.END
                        OneHandMode.OFF ->
                            Gravity.CENTER_HORIZONTAL
                    }
            }

        view.background =
            roundedDrawable(
                theme.background,
                theme.panelCornerDp,
                theme.border
            )

        content.setPadding(
            dp(2),
            dp(2),
            dp(2),
            dp(2)
        )

        content.addView(
            view,
            params
        )
    }

    fun showTranslationPanel(
        currentTarget: String,
        recentTargets: List<String>,
        detectedLanguages: List<String>
    ) {
        val panel =
            LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(
                    dp(7),
                    dp(6),
                    dp(7),
                    dp(7)
                )
            }

        panel.addView(
            TextView(context).apply {
                text =
                    buildString {
                        append(
                            "Translate → "
                        )
                        append(
                            currentTarget
                                .uppercase()
                        )

                        if (
                            detectedLanguages
                                .isNotEmpty()
                        ) {
                            append(
                                "   · source: "
                            )
                            append(
                                detectedLanguages
                                    .take(3)
                                    .joinToString(
                                        "/"
                                    ) {
                                        it.uppercase()
                                    }
                            )
                        }
                    }

                setTextColor(
                    Color.WHITE
                )
                textSize = 14f
                setPadding(
                    dp(6),
                    dp(3),
                    dp(6),
                    dp(4)
                )
            }
        )

        val targets =
            (
                recentTargets +
                    listOf(
                        "de",
                        "en",
                        "it",
                        "fr",
                        "es",
                        "pt",
                        "nl",
                        "pl",
                        "tr",
                        "uk",
                        "ar",
                        "hi",
                        "ja",
                        "ko",
                        "zh"
                    )
                )
                .map {
                    it.substringBefore('-')
                        .lowercase()
                }
                .distinct()
                .take(15)

        val scroll =
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled =
                    false
            }

        val targetRow =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
            }

        targets.forEach { tag ->
            targetRow.addView(
                Button(context).apply {
                    text =
                        if (
                            tag.equals(
                                currentTarget,
                                ignoreCase = true
                            )
                        ) {
                            "✓ ${tag.uppercase()}"
                        } else {
                            tag.uppercase()
                        }

                    isAllCaps = false
                    minWidth = dp(64)
                    setOnClickListener {
                        callbacks
                            ?.onTranslationTargetSelected(
                                tag
                            )
                    }
                },
                LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    dp(48)
                )
            )
        }

        scroll.addView(targetRow)
        panel.addView(
            scroll,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        val actions =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
            }

        actions.addView(
            Button(context).apply {
                text =
                    "Translate selection → ${currentTarget.uppercase()}"
                isAllCaps = false
                setOnClickListener {
                    callbacks?.onTranslate()
                }
            },
            LayoutParams(
                0,
                dp(52),
                2f
            )
        )

        actions.addView(
            Button(context).apply {
                text = "Keyboard"
                isAllCaps = false
                setOnClickListener {
                    showKeyboard()
                }
            },
            LayoutParams(
                0,
                dp(52),
                1f
            )
        )

        panel.addView(actions)
        swapContent(panel)
    }

    private var voiceStatus: TextView? = null
    private var voiceTranscript: TextView? = null
    private var voiceLevel: VoiceLevelView? = null
    private var voiceRecordButton: Button? = null

    fun showVoicePanel(commandMode: Boolean = false) {
        val panelBudget = clipboardHeight()
        val budgetDp = panelBudget / density
        fun portion(cap: Int, ratio: Float) = minOf(dp(cap), (panelBudget * ratio).toInt()).coerceAtLeast(1)
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setBackgroundColor(theme.background)
            filterTouchesWhenObscured = true
            if (android.os.Build.VERSION.SDK_INT >= 34) setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
        }
        panel.addView(TextView(context).apply {
            text = (if (commandMode) "Sprachbefehle" else "Diktat") + " · " + layoutProfile.languageTag.uppercase()
            textSize = minOf(17f, budgetDp * 0.08f).coerceAtLeast(11f)
            includeFontPadding = false; setTextColor(theme.textPrimary); typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LayoutParams(LayoutParams.MATCH_PARENT, portion(25, 0.13f)))
        voiceStatus = TextView(context).apply {
            text = if (Prefs.networkSpeechAllowed(context)) "Lokale Erkennung bevorzugt · Systemdienst erlaubt" else "Lokale Spracherkennung"
            textSize = 12f; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false; setTextColor(theme.textSecondary)
        }.also { panel.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, portion(32, 0.16f))) }
        voiceLevel = VoiceLevelView(context, theme).also {
            panel.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, portion(46, 0.23f)))
        }
        voiceTranscript = TextView(context).apply {
            text = "Sprich deinen Text …"
            textSize = 17f; setTextColor(theme.textPrimary); setPadding(0, dp(4), 0, dp(4))
        }.also { transcript ->
            panel.addView(ScrollView(context).apply { addView(transcript); isFillViewport = true },
                LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val actions = LinearLayout(context)
        voiceRecordButton = compactKey("Aufnehmen") {
            if (commandMode) callbacks?.onVoiceCommand() else callbacks?.onVoice()
        }.also { actions.addView(it, LayoutParams(0, portion(38, 0.23f), 1.5f)) }
        actions.addView(compactKey("Zur Tastatur") { callbacks?.onVoiceCancel(); showKeyboard() },
            LayoutParams(0, portion(38, 0.23f), 1.5f))
        val more = compactKey("⋮") {}
        more.setOnClickListener {
            android.widget.PopupMenu(context, more).apply {
                menu.add("Text vorlesen").setOnMenuItemClickListener { callbacks?.onSpeak(); true }
                menu.add("Vorlesen stoppen").setOnMenuItemClickListener { callbacks?.onStopSpeaking(); true }
                show()
            }
        }
        actions.addView(more, LayoutParams(0, portion(38, 0.23f), 0.5f))
        panel.addView(actions)
        StylusUi.usePointerInput(panel)
        swapContent(panel, panelBudget, sensitive = true)
        toolbarView.visibility = GONE
        predictionRowView.visibility = GONE
        voicePanelOpen = true
    }

    fun updateVoicePanel(status: String? = null, transcript: String? = null, phase: VoicePhase? = null, level: Float? = null) {
        status?.let { voiceStatus?.text = it }
        transcript?.let { voiceTranscript?.text = it }
        level?.let { voiceLevel?.setLevel(it) }
        phase?.let {
            voiceLevel?.setPhase(it)
            voiceRecordButton?.text = when (it) {
                VoicePhase.IDLE -> "Aufnehmen"
                VoicePhase.STARTING -> "Startet …"
                VoicePhase.LISTENING -> "Stopp"
                VoicePhase.PROCESSING -> "Umwandeln …"
            }
            voiceRecordButton?.isEnabled = it != VoicePhase.PROCESSING
        }
    }

    fun isClipboardOpen(): Boolean = clipboardPanel != null
    fun updateClipboardStatus(message: String) { clipboardPanel?.showStatus(message) }
    fun clipboardSaveCompletion(): (Boolean) -> Unit {
        val panel = clipboardPanel
        val complete = panel?.saveCompletion()
        return { success -> if (clipboardPanel === panel) complete?.invoke(success) }
    }
    private fun setSensitivePanel(active: Boolean) {
        if (sensitivePanelOpen != active) { sensitivePanelOpen = active; callbacks?.onSensitivePanelChanged(active) }
    }
    private fun clipboardHeight(): Int = keyboardContentHeightPx + if (editorMode == KeyboardEditorMode.NUMBER) 0
        else toolbarView.layoutParams.height + predictionRowView.layoutParams.height

    private fun editClipboardPanel(panel: ClipboardPanelView, editor: EditText?) {
        detachFromParent(panel)
        if (editor != null) {
            panel.setCompactEditorMode(true)
            handwritingPanelContainer.removeAllViews()
            handwritingPanelContainer.addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            handwritingPanelContainer.visibility = VISIBLE
            rebuildKeyboardPanel()
            attachMainPanel(keyboardPanel)
            clipboardPanel = panel
            clipboardEditor = editor
            setSensitivePanel(true)
        } else {
            panel.setCompactEditorMode(false)
            swapContent(panel, clipboardHeight(), sensitive = true)
            toolbarView.visibility = GONE
            predictionRowView.visibility = GONE
            clipboardPanel = panel
            clipboardEditor = null
        }
        clearSuggestions()
    }

    fun showClipboardPanel(items: List<ClipboardEntry>, query: String = "", expiryMinutes: Long = 1440L,
        hasSelection: Boolean = false, message: String? = null) {
        clipboardPanel?.let { it.update(items, expiryMinutes, hasSelection, message); return }
        showKeyboard()
        val actions = callbacks ?: return
        var reference: ClipboardPanelView? = null
        val panel = ClipboardPanelView(context, theme, actions,
            onEditor = { editor -> reference?.let { editClipboardPanel(it, editor) } },
            onClose = { showKeyboard() })
        reference = panel
        swapContent(panel, clipboardHeight(), sensitive = true)
        toolbarView.visibility = GONE
        predictionRowView.visibility = GONE
        clipboardPanel = panel
        panel.update(items, expiryMinutes, hasSelection, message)
        clearSuggestions()
    }

    fun showEmojiPanel() {
        val panel = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }
        val tabs = LinearLayout(context)
        val grid = LinearLayout(context).apply { orientation = VERTICAL }
        fun showCategory(index: Int) {
            emojiAlternatives.dismiss()
            grid.removeAllViews()
            EmojiCatalog.categories[index].keys.chunked(6).forEach { group ->
                val row = LinearLayout(context)
                group.forEach { emoji ->
                    val button = compactKey(emoji) { callbacks?.onEmoji(emoji) }.apply {
                        textSize = 24f
                        contentDescription = "$emoji; gedrückt halten für Varianten"
                    }
                    emojiAlternatives.attach(button) { EmojiCatalog.alternatives(emoji) }
                    row.addView(button, LayoutParams(0, dp(52), 1f))
                }
                repeat(6 - group.size) { row.addView(View(context), LayoutParams(0, dp(52), 1f)) }
                grid.addView(row)
            }
            for (i in 0 until tabs.childCount) {
                tabs.getChildAt(i).background = buttonBackground(special = i == index)
            }
        }
        EmojiCatalog.categories.forEachIndexed { index, category ->
            tabs.addView(compactKey(category.label) { showCategory(index) }, LayoutParams(0, dp(40), 1f))
        }
        panel.addView(tabs)
        panel.addView(grid)
        panel.addView(compactKey("ABC · Zur Tastatur") { showKeyboard() }, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))
        swapContent(panel)
        showCategory(0)
        StylusUi.usePointerInput(panel)
    }

    fun showHandwritingPanel(
        onReady: (
            HandwritingPadView
        ) -> Unit
    ) {
        showKeyboard()
        val panel =
            LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(
                    dp(5),
                    dp(5),
                    dp(5),
                    dp(5)
                )
            }

        val pad =
            HandwritingPadView(context)
                .apply {
                    onStylusPrimaryButton = {
                        callbacks
                            ?.onStylusPrimary()
                    }
                    onStylusSecondaryButton = {
                        callbacks
                            ?.onStylusSecondary()
                    }
                }

        panel.addView(
            pad,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                minOf(dp(132), resources.displayMetrics.heightPixels / 6)
            )
        )

        val controls =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
            }

        controls.addView(
            Button(context).apply {
                text = "Clear"
                setOnClickListener {
                    pad.clearInk()
                }
            },
            LayoutParams(
                0,
                dp(40),
                1f
            )
        )

        controls.addView(
            Button(context).apply {
                text = "Recognize"
                setOnClickListener {
                    callbacks
                        ?.onHandwritingRecognize(
                            pad
                        )
                }
            },
            LayoutParams(
                0,
                dp(40),
                1f
            )
        )

        controls.addView(
            Button(context).apply {
                text = "Keyboard"
                setOnClickListener {
                    showKeyboard()
                }
            },
            LayoutParams(
                0,
                dp(40),
                1f
            )
        )

        panel.addView(controls)
        themeAuxiliaryTree(panel)
        handwritingPanelContainer.addView(panel)
        handwritingPanelContainer.visibility = VISIBLE
        onReady(pad)
    }

    private fun swapContent(
        view: View,
        heightPx: Int? = null,
        sensitive: Boolean = false
    ) {
        if (voicePanelOpen) { voicePanelOpen = false; callbacks?.onVoiceCancel() }
        setSensitivePanel(sensitive)
        clipboardEditor = null
        clipboardPanel = null
        dismissAlternatives()
        handwritingPanelContainer.removeAllViews()
        handwritingPanelContainer.visibility = GONE
        content.removeAllViews()
        detachFromParent(view)
        themeAuxiliaryTree(
            view
        )

        view.background =
            roundedDrawable(
                theme.surface,
                theme.panelCornerDp,
                theme.border
            )

        if (heightPx == null) content.addView(view)
        else content.addView(view, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, heightPx))
    }

    override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 29 &&
            event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED != 0) return false
        return super.onFilterTouchEventForSecurity(event)
    }

    private fun themeAuxiliaryTree(
        view: View
    ) {
        StylusUi.usePointerInput(view)
        when (view) {
            is EditText -> {
                view.setTextColor(
                    theme.textPrimary
                )
                view.setHintTextColor(
                    theme.textSecondary
                )
                view.backgroundTintList =
                    android.content.res
                        .ColorStateList
                        .valueOf(
                            theme.accentSoft
                        )
            }

            is Button -> {
                view.isAllCaps = false
                view.setTextColor(
                    theme.textPrimary
                )
                view.background =
                    buttonBackground(
                        subtle = true
                    )
            }

            is TextView -> {
                view.setTextColor(
                    theme.textPrimary
                )
            }
        }

        if (
            view is ViewGroup
        ) {
            for (
                index in 0 until
                    view.childCount
            ) {
                themeAuxiliaryTree(
                    view.getChildAt(
                        index
                    )
                )
            }
        }
    }

    private fun detachFromParent(
        view: View
    ) {
        (
            view.parent as?
                ViewGroup
            )
            ?.removeView(view)
    }
}

/** Delay refreshed labels until Android has dispatched the original click. */
@SuppressLint("AppCompatCustomView")
private class StableSuggestionButton(context: Context, private val onInteraction: (Boolean) -> Unit) : Button(context) {
    private var interacting = false
    private var generation = 0

    private fun finishInteraction() {
        if (interacting) { interacting = false; onInteraction(false) }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            generation++
            if (!interacting) { interacting = true; onInteraction(true) }
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) finishInteraction()
        else if (event.actionMasked == MotionEvent.ACTION_UP) {
            val releaseGeneration = generation
            if (!post { if (generation == releaseGeneration) finishInteraction() }) finishInteraction()
        }
        return handled
    }

    override fun onDetachedFromWindow() {
        generation++
        finishInteraction()
        super.onDetachedFromWindow()
    }
}

