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
import cloud.kosch.keyswiper.clipboard.ClipboardEntry
import cloud.kosch.keyswiper.input.EditTimelineEntry
import cloud.kosch.keyswiper.input.SwipeTrace
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

    var callbacks: Callbacks? = null

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

    private val status =
        TextView(context)

    private val suggestions =
        LinearLayout(context)

    private val undoButton =
        Button(context)

    private val redoButton =
        Button(context)

    private val content =
        FrameLayout(context)

    private val keyboardPanel =
        LinearLayout(context)

    private val keyboardSurface =
        KeyboardSurface(context)

    private var shifted = false
    private var symbolMode = false
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

        status.apply {
            setTextColor(
                theme.textSecondary
            )
            textSize = 12f
            setPadding(
                dp(10),
                dp(4),
                dp(10),
                dp(4)
            )
            visibility = GONE
        }

        addView(
            status,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        )

        addView(
            buildToolbar(),
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(40)
            )
        )

        val suggestionScroll =
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

        val predictionRow =
            LinearLayout(context).apply {
                orientation =
                    HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
                setBackgroundColor(
                    theme.surface
                )
            }

        predictionRow.addView(
            undoButton,
            LayoutParams(
                dp(44),
                LayoutParams.MATCH_PARENT
            )
        )

        predictionRow.addView(
            suggestionScroll,
            LayoutParams(
                0,
                LayoutParams.MATCH_PARENT,
                1f
            )
        )

        predictionRow.addView(
            redoButton,
            LayoutParams(
                dp(44),
                LayoutParams.MATCH_PARENT
            )
        )

        addView(
            predictionRow,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(46)
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

                override fun onTap(
                    character: Char
                ) {
                    callbacks
                        ?.onCharacter(
                            character
                        )

                    if (
                        shifted &&
                        character.isLetter()
                    ) {
                        shifted = false
                        keyboardSurface.shifted =
                            false
                    }
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
        keyboardPanel.removeAllViews()

        layoutProfile =
            KeyboardLayoutProfiles.byId(
                Prefs.keyboardLayoutId(
                    context
                )
            )

        keyboardSurface.setLayout(
            profile = layoutProfile,
            symbols = symbolMode
        )
        keyboardSurface.shifted =
            shifted && !symbolMode

        keyboardPanel.addView(
            keyboardSurface,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(190)
            )
        )

        if (!symbolMode) {
            keyboardPanel.addView(
                buildAccentAndLayoutRow(),
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    dp(38)
                )
            )
        }

        keyboardPanel.addView(
            buildBottomRow(),
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(54)
            )
        )
    }

    private fun buildAccentAndLayoutRow(): View {
        val row =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(
                    dp(4),
                    dp(1),
                    dp(4),
                    dp(1)
                )
                setBackgroundColor(
                    theme.background
                )
            }

        fun addButton(
            label: String,
            weight: Float = 1f,
            action: () -> Unit
        ) {
            row.addView(
                Button(context).apply {
                    text = label
                    isAllCaps = false
                    textSize = 13f
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
                        dp(2),
                        0,
                        dp(2),
                        0
                    )
                    setOnClickListener {
                        action()
                    }
                },
                LayoutParams(
                    0,
                    LayoutParams.MATCH_PARENT,
                    weight
                )
            )
        }

        addButton(
            layoutProfile
                .languageTag
                .uppercase(),
            1.15f
        ) {
            cycleLayout()
        }

        if (
            layoutProfile
                .accentKeys
                .isEmpty()
        ) {
            addButton(
                "Aa",
                2.3f
            ) {
                shifted = !shifted
                keyboardSurface.shifted =
                    shifted
            }
        } else {
            layoutProfile
                .accentKeys
                .forEach { character ->
                    addButton(
                        character.toString()
                    ) {
                        callbacks
                            ?.onCharacter(
                                if (shifted) {
                                    character
                                        .uppercaseChar()
                                } else {
                                    character
                                }
                            )
                        shifted = false
                        keyboardSurface.shifted =
                            false
                    }
                }
        }

        return row
    }

    private fun buildBottomRow(): View {
        val row =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(
                    dp(4),
                    dp(3),
                    dp(4),
                    dp(4)
                )
                setBackgroundColor(
                    theme.background
                )
            }

        fun key(
            label: String,
            weight: Float = 1f,
            special: Boolean = false,
            action: () -> Unit
        ) {
            row.addView(
                Button(context).apply {
                    text = label
                    isAllCaps = false
                    textSize =
                        if (
                            label == "space"
                        ) {
                            13f
                        } else {
                            16f
                        }
                    setTextColor(
                        if (special) {
                            theme.accent
                        } else {
                            theme.textPrimary
                        }
                    )
                    background =
                        buttonBackground(
                            special =
                                special
                        )
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(
                        dp(3),
                        0,
                        dp(3),
                        0
                    )
                    setOnClickListener {
                        action()
                    }
                },
                LayoutParams(
                    0,
                    LayoutParams.MATCH_PARENT,
                    weight
                ).apply {
                    marginStart =
                        dp(2)
                    marginEnd =
                        dp(2)
                }
            )
        }

        if (symbolMode) {
            if (
                editorMode ==
                KeyboardEditorMode.NUMBER
            ) {
                key(
                    "123",
                    1.05f,
                    true
                ) {}
            } else {
                key(
                    "ABC",
                    1.05f,
                    true
                ) {
                    symbolMode = false
                    shifted = false
                    rebuildKeyboardPanel()
                }
            }
        } else {
            key(
                "?123",
                1.05f,
                true
            ) {
                symbolMode = true
                shifted = false
                rebuildKeyboardPanel()
            }

            key(
                "⇧",
                0.80f,
                shifted
            ) {
                shifted =
                    !shifted
                keyboardSurface.shifted =
                    shifted
            }
        }

        when (editorMode) {
            KeyboardEditorMode.EMAIL -> {
                key(
                    "@",
                    0.75f
                ) {
                    callbacks
                        ?.onCharacter('@')
                }

                key(
                    "😀",
                    0.78f
                ) {
                    showEmojiPanel()
                }

                key(
                    "space",
                    2.55f
                ) {
                    callbacks
                        ?.onSpace()
                }

                key(
                    ".",
                    0.72f
                ) {
                    callbacks
                        ?.onCharacter('.')
                }
            }

            KeyboardEditorMode.URL -> {
                key(
                    "/",
                    0.72f
                ) {
                    callbacks
                        ?.onCharacter('/')
                }

                key(
                    "😀",
                    0.78f
                ) {
                    showEmojiPanel()
                }

                key(
                    "space",
                    2.35f
                ) {
                    callbacks
                        ?.onSpace()
                }

                key(
                    ".",
                    0.72f
                ) {
                    callbacks
                        ?.onCharacter('.')
                }
            }

            KeyboardEditorMode.NUMBER -> {
                key(
                    "-",
                    0.85f
                ) {
                    callbacks
                        ?.onCharacter('-')
                }

                key(
                    ".",
                    0.85f
                ) {
                    callbacks
                        ?.onCharacter('.')
                }
            }

            KeyboardEditorMode.TEXT -> {
                key(
                    ",",
                    0.70f
                ) {
                    callbacks
                        ?.onCharacter(',')
                }

                key(
                    "😀",
                    0.80f
                ) {
                    showEmojiPanel()
                }

                key(
                    "space",
                    3.35f
                ) {
                    callbacks
                        ?.onSpace()
                }

                key(
                    ".",
                    0.70f
                ) {
                    callbacks
                        ?.onCharacter('.')
                }
            }
        }

        key(
            "↵",
            0.95f,
            true
        ) {
            callbacks
                ?.onEnter()
        }

        return row
    }

    private fun cycleLayout() {
        val all =
            KeyboardLayoutProfiles.all

        val index =
            all.indexOfFirst {
                it.id ==
                    layoutProfile.id
            }
                .coerceAtLeast(0)

        val next =
            all[
                (index + 1) %
                    all.size
                ]

        Prefs.setKeyboardLayoutId(
            context,
            next.id
        )

        layoutProfile = next
        shifted = false
        symbolMode = false
        rebuildKeyboardPanel()

        setStatus(
            "Layout: ${next.label}"
        )
    }

    fun setEditorMode(
        mode: KeyboardEditorMode
    ) {
        editorMode = mode
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

    fun setAutoShift(
        enabled: Boolean
    ) {
        val next =
            enabled &&
                editorMode ==
                KeyboardEditorMode.TEXT &&
                !symbolMode

        shifted = next
        keyboardSurface.shifted =
            next
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
        status.text =
            message.orEmpty()

        status.visibility =
            if (
                message.isNullOrBlank()
            ) {
                GONE
            } else {
                VISIBLE
            }
    }

    fun setPredictions(
        values: List<PredictionSuggestion>
    ) {
        suggestions.removeAllViews()

        values
            .take(6)
            .forEach { suggestion ->
                suggestions.addView(
                    Button(context).apply {
                        text =
                            suggestion.display
                        isAllCaps = false
                        setTextColor(
                            theme.textPrimary
                        )
                        background =
                            buttonBackground(
                                special =
                                    suggestion.kind ==
                                        PredictionKind.SENTENCE,
                                subtle = true
                            )
                        minWidth =
                            when (
                                suggestion.kind
                            ) {
                                PredictionKind.SENTENCE ->
                                    dp(170)
                                else ->
                                    dp(72)
                            }
                        maxLines = 1
                        textSize =
                            if (
                                suggestion.kind ==
                                PredictionKind.SENTENCE
                            ) {
                                13f
                            } else {
                                15f
                            }
                        alpha =
                            (
                                0.72f +
                                    suggestion
                                        .confidence
                                        .coerceIn(
                                            0f,
                                            1f
                                        ) *
                                    0.28f
                                )
                        setOnClickListener {
                            callbacks
                                ?.onPrediction(
                                    suggestion
                                )
                        }
                    },
                    LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.MATCH_PARENT
                    )
                )
            }
    }

    fun setSwipeCandidates(
        values: List<String>
    ) {
        suggestions.removeAllViews()

        values
            .take(6)
            .forEachIndexed {
                    index,
                    value ->

                val suggestion =
                    PredictionSuggestion(
                        display = value,
                        commitText = value,
                        kind =
                            PredictionKind
                                .SWIPE_CORRECTION,
                        confidence =
                            (
                                0.96f -
                                    index * 0.10f
                                )
                                .coerceAtLeast(
                                    0.45f
                                )
                    )

                suggestions.addView(
                    Button(context).apply {
                        text = value
                        isAllCaps = false
                        setTextColor(
                            theme.textPrimary
                        )
                        background =
                            buttonBackground(
                                special =
                                    index == 0,
                                subtle = true
                            )
                        minWidth = dp(72)
                        alpha =
                            0.75f +
                                suggestion
                                    .confidence *
                                0.25f
                        setOnClickListener {
                            callbacks
                                ?.onCandidate(
                                    value
                                )
                        }
                    },
                    LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.MATCH_PARENT
                    )
                )
            }
    }

    fun clearSuggestions() {
        suggestions.removeAllViews()
    }

    fun showKeyboard() {
        theme =
            KeyboardThemes.byId(
                Prefs.keyboardThemeId(
                    context
                )
            )
        setBackgroundColor(
            theme.background
        )
        oneHandMode =
            Prefs.oneHandMode(
                context
            )
        symbolMode =
            editorMode ==
                KeyboardEditorMode.NUMBER
        shifted = false
        rebuildKeyboardPanel()
        attachMainPanel(
            keyboardPanel
        )
    }

    fun setEditHistoryState(
        canUndo: Boolean,
        canRedo: Boolean
    ) {
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

    fun showClipboardPanel(
        items: List<ClipboardEntry>,
        query: String = "",
        expiryMinutes: Long = 60L
    ) {
        val panel =
            LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(
                    dp(6),
                    dp(5),
                    dp(6),
                    dp(5)
                )
            }

        val searchRow =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
            }

        val search =
            EditText(context).apply {
                setText(query)
                hint = "Search clipboard"
                maxLines = 1
            }

        searchRow.addView(
            search,
            LayoutParams(
                0,
                dp(48),
                3f
            )
        )

        searchRow.addView(
            Button(context).apply {
                text = "Search"
                isAllCaps = false
                setOnClickListener {
                    callbacks
                        ?.onClipboardSearch(
                            search.text
                                .toString()
                        )
                }
            },
            LayoutParams(
                0,
                dp(48),
                1f
            )
        )

        panel.addView(searchRow)

        val controls =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

        fun expiryButton(
            label: String,
            minutes: Long
        ) {
            controls.addView(
                Button(context).apply {
                    text =
                        if (
                            expiryMinutes ==
                            minutes
                        ) {
                            "✓ $label"
                        } else {
                            label
                        }
                    isAllCaps = false
                    setOnClickListener {
                        callbacks
                            ?.onClipboardExpiryChanged(
                                minutes
                            )
                    }
                },
                LayoutParams(
                    0,
                    dp(44),
                    1f
                )
            )
        }

        expiryButton(
            "10m",
            10L
        )
        expiryButton(
            "1h",
            60L
        )
        expiryButton(
            "1d",
            1440L
        )

        controls.addView(
            Button(context).apply {
                text = "Clear"
                isAllCaps = false
                setOnClickListener {
                    callbacks
                        ?.onClipboardClearUnpinned()
                }
            },
            LayoutParams(
                0,
                dp(44),
                1f
            )
        )

        panel.addView(controls)

        val list =
            LinearLayout(context).apply {
                orientation = VERTICAL
            }

        if (items.isEmpty()) {
            list.addView(
                TextView(context).apply {
                    text =
                        if (query.isBlank()) {
                            "Clipboard is empty."
                        } else {
                            "No clipboard item matches this search."
                        }

                    setTextColor(
                        Color.WHITE
                    )
                    setPadding(
                        dp(8),
                        dp(14),
                        dp(8),
                        dp(14)
                    )
                }
            )
        } else {
            items
                .take(20)
                .forEach { entry ->
                    val item =
                        LinearLayout(context).apply {
                            orientation = VERTICAL
                            setPadding(
                                dp(3),
                                dp(3),
                                dp(3),
                                dp(5)
                            )
                        }

                    item.addView(
                        TextView(context).apply {
                            text =
                                buildString {
                                    append(
                                        if (
                                            entry.pinned
                                        ) {
                                            "📌 "
                                        } else {
                                            ""
                                        }
                                    )
                                    append(
                                        entry.category.label
                                    )
                                    append(" · ")
                                    append(
                                        entry.text
                                            .replace(
                                                "\n",
                                                " "
                                            )
                                            .take(110)
                                    )
                                }

                            setTextColor(
                                Color.WHITE
                            )
                            textSize = 13f
                            maxLines = 2
                            setPadding(
                                dp(5),
                                dp(2),
                                dp(5),
                                dp(2)
                            )
                        }
                    )

                    val actions =
                        LinearLayout(context).apply {
                            orientation = HORIZONTAL
                        }

                    actions.addView(
                        Button(context).apply {
                            text = "Paste"
                            isAllCaps = false
                            setOnClickListener {
                                callbacks
                                    ?.onClipboardInsert(
                                        entry.id
                                    )
                                showKeyboard()
                            }
                        },
                        LayoutParams(
                            0,
                            dp(42),
                            2f
                        )
                    )

                    actions.addView(
                        Button(context).apply {
                            text =
                                if (
                                    entry.pinned
                                ) {
                                    "Unpin"
                                } else {
                                    "Pin"
                                }
                            isAllCaps = false
                            setOnClickListener {
                                callbacks
                                    ?.onClipboardTogglePin(
                                        entry.id
                                    )
                            }
                        },
                        LayoutParams(
                            0,
                            dp(42),
                            1f
                        )
                    )

                    actions.addView(
                        Button(context).apply {
                            text = "Delete"
                            isAllCaps = false
                            setOnClickListener {
                                callbacks
                                    ?.onClipboardDelete(
                                        entry.id
                                    )
                            }
                        },
                        LayoutParams(
                            0,
                            dp(42),
                            1f
                        )
                    )

                    item.addView(actions)
                    list.addView(item)
                }
        }

        val scroll =
            ScrollView(context).apply {
                addView(list)
            }

        panel.addView(
            scroll,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(190)
            )
        )

        swapContent(panel)
    }

    fun showEmojiPanel() {
        val panel =
            LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(
                    dp(4),
                    dp(4),
                    dp(4),
                    dp(4)
                )
            }

        val groups =
            listOf(
                listOf(
                    "😀",
                    "😄",
                    "😂",
                    "🥹",
                    "😍",
                    "🥰",
                    "😘",
                    "😎"
                ),
                listOf(
                    "❤️",
                    "❤️‍🔥",
                    "💕",
                    "✨",
                    "🔥",
                    "👍",
                    "🙌",
                    "🙏"
                ),
                listOf(
                    "🤔",
                    "😅",
                    "😢",
                    "😭",
                    "😡",
                    "🤯",
                    "🥳",
                    "🫶"
                )
            )

        groups.forEach { group ->
            val row =
                LinearLayout(context)
                    .apply {
                        orientation =
                            HORIZONTAL
                    }

            group.forEach { emoji ->
                row.addView(
                    Button(context).apply {
                        text = emoji
                        textSize = 20f
                        background =
                            buttonBackground(
                                subtle = true
                            )
                        setPadding(
                            0,
                            0,
                            0,
                            0
                        )
                        setOnClickListener {
                            callbacks
                                ?.onEmoji(
                                    emoji
                                )
                        }
                    },
                    LayoutParams(
                        0,
                        dp(52),
                        1f
                    )
                )
            }

            panel.addView(row)
        }

        panel.addView(
            Button(context).apply {
                text = "Back to keyboard"
                isAllCaps = false
                setTextColor(
                    theme.accent
                )
                background =
                    buttonBackground(
                        special = true
                    )
                setOnClickListener {
                    showKeyboard()
                }
            }
        )

        swapContent(panel)
    }

    fun showHandwritingPanel(
        onReady: (
            HandwritingPadView
        ) -> Unit
    ) {
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
                dp(185)
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
                dp(52),
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
                dp(52),
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
                dp(52),
                1f
            )
        )

        panel.addView(controls)
        swapContent(panel)
        onReady(pad)
    }

    private fun swapContent(
        view: View
    ) {
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
