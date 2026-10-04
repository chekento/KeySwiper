package cloud.kosch.keyswiper.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import cloud.kosch.keyswiper.prediction.PredictionKind
import cloud.kosch.keyswiper.prediction.PredictionSuggestion
import cloud.kosch.keyswiper.settings.Prefs

class KeyboardRootView(
    context: Context
) : LinearLayout(context) {

    interface Callbacks {
        fun onCharacter(value: Char)
        fun onSwipe(trace: SwipeTrace)
        fun onBackspace()
        fun onBackspaceRepeat(elapsedMs: Long) {}
        fun onBackspaceHoldEnd() {}
        fun onSpaceRepeat(elapsedMs: Long) {}
        fun onSpace()
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
                override fun onSpaceRepeat(elapsedMs: Long) = editClipboard(target, " ".repeat(HoldAcceleration.step(elapsedMs).spaces))
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
        val amount = if (start == end) HoldAcceleration.deleteLength(target.text.take(start).toString(), HoldAcceleration.step(elapsedMs).unit) else 0
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
    private lateinit var suggestionScrollView: HorizontalScrollView

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

    private var shifted = false
    private var capsLocked = false
    private var lastShiftTap = 0L
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
            shifted = false
            capsLocked = false
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

        suggestionScrollView =
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled =
                    false
                setBackgroundColor(
                    theme.surface
                )
                addView(
                    suggestions.apply {
                        orientation =
                            HORIZONTAL
                        gravity =
                            Gravity.CENTER_VERTICAL
                        setPadding(
                            dp(4),
                            dp(2),
                            dp(4),
                            dp(2)
                        )
                    }
                )
            }

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
            suggestionScrollView,
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

                override fun onBackspaceRepeat(elapsedMs: Long) { callbacks?.onBackspaceRepeat(elapsedMs) }
                override fun onBackspaceHoldEnd() { callbacks?.onBackspaceHoldEnd() }
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
        toolbarView.layoutParams =
            toolbarView.layoutParams.apply {
                height = sizing.toolbarHeightPx
            }
        predictionRowView.layoutParams =
            predictionRowView.layoutParams.apply {
                height = sizing.predictionHeightPx
            }

        keyboardSurface.setLayout(
            profile = layoutProfile,
            symbols = symbolMode,
            page = symbolPage
        )
        keyboardSurface.shifted =
            shifted && !symbolMode
        keyboardSurface.capsLocked = capsLocked

        keyboardPanel.addView(
            keyboardSurface,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                sizing.surfaceHeightPx
            )
        )

        if (!symbolMode) {
            keyboardPanel.addView(
                buildAccentAndLayoutRow(),
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    sizing.accentRowHeightPx
                )
            )
        }

        keyboardPanel.addView(
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

    private fun buildAccentAndLayoutRow(): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(4), dp(1), dp(4), dp(1))
        }
        fun key(label: String, weight: Float = 1f, action: () -> Unit): Button {
            val button = compactKey(label, action)
            row.addView(button, LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
            return button
        }
        listOf('!', '?', ':', ';', ',', '.').forEach { character ->
            val button = key(character.toString()) { callbacks?.onCharacter(character) }.apply {
                textSize = 22f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                contentDescription = when (character) {
                    '!' -> "Ausrufezeichen"; '?' -> "Fragezeichen"; ':' -> "Doppelpunkt"
                    ';' -> "Semikolon"; ',' -> "Komma"; else -> "Punkt"
                }
            }
            textAlternatives.attach(button) {
                KeyAlternatives.forKey(character, layoutProfile.id, false, true)
            }
        }
        return row
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
        capsLocked -> word.uppercase()
        shifted -> word.replaceFirstChar { it.uppercase() }
        else -> word
    }

    private fun consumeShift(value: String) {
        if (value.any { it.isLetter() }) lastShiftTap = 0L
        if (shifted && !capsLocked && value.any { it.isLetter() }) {
            shifted = false
            updateShiftAppearance()
        }
    }

    private fun toggleShift() {
        val now = android.os.SystemClock.uptimeMillis()
        if (capsLocked) {
            capsLocked = false
            shifted = false
            lastShiftTap = 0L
            updateShiftAppearance()
            return
        } else if (lastShiftTap != 0L && now - lastShiftTap < 350L) {
            capsLocked = true
            shifted = true
        } else {
            shifted = !shifted
        }
        lastShiftTap = now
        updateShiftAppearance()
    }

    private fun updateShiftAppearance() {
        keyboardSurface.shifted = shifted && !symbolMode
        keyboardSurface.capsLocked = capsLocked
        shiftButton?.apply {
            text = if (capsLocked) "⇪" else "⇧"
            contentDescription = if (capsLocked) "Feststelltaste aktiv" else if (shifted) "Großschreibung aktiv" else "Umschalten; zweimal tippen für Feststelltaste"
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
            space.textSize = 13f
            space.contentDescription = "Leerzeichen; halten für beschleunigte Wiederholung"
            HoldRepeater(space, { elapsed -> callbacks?.onSpaceRepeat(elapsed) }).attach()
            if (symbolMode || editorMode != KeyboardEditorMode.TEXT) character('.')
        }
        key("↵", 1f) { callbacks?.onEnter() }.contentDescription = "Eingabe"
        return row
    }

    fun cancelActiveGestures() {
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
        mode: KeyboardEditorMode
    ) {
        editorMode = mode
        capsLocked = false
        symbolPage = 0
        symbolMode =
            mode ==
                KeyboardEditorMode.NUMBER

        if (
            mode !=
            KeyboardEditorMode.TEXT
        ) {
            shifted = false
        }

        rebuildKeyboardPanel()
    }

    fun setAutoShift(enabled: Boolean) {
        shifted = capsLocked || (enabled && editorMode == KeyboardEditorMode.TEXT)
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

    private fun renderSuggestions(values: List<PredictionSuggestion>, onChoose: (PredictionSuggestion) -> Unit) {
        if (clipboardPanel != null) { clearSuggestions(); return }
        suggestions.removeAllViews()
        suggestionScrollView.scrollTo(0, 0)
        values.take(6).forEachIndexed { index, suggestion ->
            val correction = suggestion.kind == PredictionKind.CORRECTION
            suggestions.addView(Button(context).apply {
                // Words are readable labels; action/status icons do not belong in their text.
                text = suggestion.commitText
                isAllCaps = false
                textSize = 16f
                setTextColor(if (correction) theme.accent else theme.textPrimary)
                typeface = android.graphics.Typeface.create("sans-serif", if (index == 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                background = roundedDrawable(theme.surfaceRaised, theme.keyCornerDp,
                    if (correction) theme.accent else theme.border)
                minWidth = dp(80); minimumWidth = 0; minHeight = 0; minimumHeight = 0
                maxWidth = dp(240)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(8), 0, dp(8), 0)
                contentDescription = when (suggestion.kind) {
                    PredictionKind.CORRECTION -> "Korrektur: ${suggestion.commitText}"
                    PredictionKind.KEEP_TYPED -> "Schreibweise behalten: ${suggestion.commitText}"
                    PredictionKind.SENTENCE, PredictionKind.NEURAL -> "Textvorschlag: ${suggestion.commitText}"
                    else -> "Wortvorschlag: ${suggestion.commitText}"
                }
                setOnClickListener { onChoose(suggestion) }
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
        }
        StylusUi.usePointerInput(suggestions)
    }

    fun setSwipeCandidates(values: List<String>) {
        renderSuggestions(values.mapIndexed { index, value ->
            PredictionSuggestion(value, value, PredictionKind.SWIPE_CORRECTION, confidence = (0.96f - index * 0.10f).coerceAtLeast(0.45f))
        }) { callbacks?.onCandidate(it.commitText) }
    }

    fun clearSuggestions() {
        suggestions.removeAllViews()
    }

    fun showKeyboard() {
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
        shifted = capsLocked
        rebuildKeyboardPanel()
        attachMainPanel(
            keyboardPanel
        )
    }

    fun setEditHistoryState(
        canUndo: Boolean,
        canRedo: Boolean
    ) {
        undoButton.visibility = if (canUndo) VISIBLE else GONE
        redoButton.visibility = if (canRedo) VISIBLE else GONE
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
        suggestionScrollView.setBackgroundColor(theme.surface)
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

    fun showVoicePanel() {
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(theme.background)
        }
        voiceStatus = TextView(context).apply {
            text = "Diktat · ${layoutProfile.languageTag.uppercase()}"
            textSize = 17f; setTextColor(theme.accent)
        }.also { panel.addView(it) }
        voiceTranscript = TextView(context).apply {
            text = "Sprich deinen Text. Teilresultate erscheinen hier."
            textSize = 17f; setTextColor(theme.textPrimary)
            setPadding(0, dp(8), 0, dp(8)); minLines = 3
        }.also { panel.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, dp(105))) }
        val actions = LinearLayout(context)
        fun action(label: String, block: () -> Unit) {
            actions.addView(compactKey(label, block), LayoutParams(0, dp(44), 1f))
        }
        action("Aufnahme / Stopp") { callbacks?.onVoice() }
        action("Abbrechen") { callbacks?.onVoiceCancel(); showKeyboard() }
        panel.addView(actions)
        val speech = LinearLayout(context)
        speech.addView(compactKey("Text vorlesen") { callbacks?.onSpeak() }, LayoutParams(0, dp(44), 1f))
        speech.addView(compactKey("Vorlesen stoppen") { callbacks?.onStopSpeaking() }, LayoutParams(0, dp(44), 1f))
        panel.addView(speech)
        StylusUi.usePointerInput(panel)
        swapContent(panel)
    }

    fun updateVoicePanel(status: String? = null, transcript: String? = null) {
        status?.let { voiceStatus?.text = it }
        transcript?.let { voiceTranscript?.text = it }
    }

    fun showClipboardPanel(items: List<ClipboardEntry>, query: String = "", expiryMinutes: Long = 60L) {
        clipboardPanel?.let { it.update(items, expiryMinutes); return }
        showKeyboard()
        val actions = callbacks ?: return
        val panel = ClipboardPanelView(context, theme, actions,
            onEditor = { clipboardEditor = it; clearSuggestions() },
            onClose = { showKeyboard() })
        clipboardPanel = panel
        handwritingPanelContainer.addView(panel)
        handwritingPanelContainer.visibility = VISIBLE
        panel.update(items, expiryMinutes)
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
        view: View
    ) {
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

        content.addView(view)
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

