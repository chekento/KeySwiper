package cloud.kosch.keyswiper.ui

import android.content.Context
import android.graphics.Color
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

    private fun dp(value: Int) =
        (value * density).toInt()

    private val status =
        TextView(context)

    private val suggestions =
        LinearLayout(context)

    private val content =
        FrameLayout(context)

    private val keyboardPanel =
        LinearLayout(context)

    private val keyboardSurface =
        KeyboardSurface(context)

    private var shifted = false
    private var symbolMode = false
    private var layoutProfile =
        KeyboardLayoutProfiles.byId(
            Prefs.keyboardLayoutId(context)
        )

    init {
        orientation = VERTICAL
        setBackgroundColor(
            Color.rgb(18, 20, 25)
        )

        status.apply {
            setTextColor(
                Color.rgb(
                    214,
                    219,
                    230
                )
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
                dp(48)
            )
        )

        val suggestionScroll =
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled =
                    false
                setBackgroundColor(
                    Color.rgb(
                        24,
                        27,
                        34
                    )
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

        addView(
            suggestionScroll,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        keyboardPanel.orientation =
            VERTICAL

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
                    dp(2),
                    0,
                    dp(2),
                    0
                )
            }

        fun tool(
            label: String,
            action: () -> Unit
        ): Button =
            Button(context).apply {
                text = label
                textSize = 15f
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
            }

        listOf(
            tool("⌨") {
                showKeyboard()
            },
            tool("🌐") {
                callbacks
                    ?.onTranslationPanelRequested()
            },
            tool("🎙") {
                callbacks?.onVoice()
            },
            tool("📋") {
                callbacks?.onClipboard()
            },
            tool("😀") {
                showEmojiPanel()
            },
            tool("✍") {
                callbacks
                    ?.onHandwritingRequested()
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
                dp(186)
            )
        )

        if (!symbolMode) {
            keyboardPanel.addView(
                buildAccentAndLayoutRow(),
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    dp(42)
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
                    dp(2),
                    dp(1),
                    dp(2),
                    dp(1)
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
                    textSize = 14f
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
                    dp(2),
                    dp(2),
                    dp(2),
                    dp(2)
                )
            }

        fun key(
            label: String,
            weight: Float = 1f,
            action: () -> Unit
        ) {
            row.addView(
                Button(context).apply {
                    text = label
                    isAllCaps = false
                    textSize = 16f
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
                )
            )
        }

        if (symbolMode) {
            key(
                "ABC",
                1.1f
            ) {
                symbolMode = false
                shifted = false
                rebuildKeyboardPanel()
            }
        } else {
            key(
                "?123",
                1.1f
            ) {
                symbolMode = true
                shifted = false
                rebuildKeyboardPanel()
            }

            key(
                "⇧",
                0.9f
            ) {
                shifted = !shifted
                keyboardSurface.shifted =
                    shifted
            }
        }

        key(
            "space",
            3.2f
        ) {
            callbacks?.onSpace()
        }

        key(
            ".",
            0.75f
        ) {
            callbacks
                ?.onCharacter('.')
        }

        key(
            "⌫",
            1f
        ) {
            callbacks?.onBackspace()
        }

        key(
            "↵",
            1f
        ) {
            callbacks?.onEnter()
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
        symbolMode = false
        shifted = false
        rebuildKeyboardPanel()
        content.removeAllViews()
        detachFromParent(
            keyboardPanel
        )
        content.addView(
            keyboardPanel
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
        content.addView(view)
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
