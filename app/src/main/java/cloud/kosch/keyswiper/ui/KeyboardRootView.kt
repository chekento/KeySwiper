package cloud.kosch.keyswiper.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class KeyboardRootView(context: Context) : LinearLayout(context) {
    interface Callbacks {
        fun onCharacter(value: Char)
        fun onSwipe(trace: List<Char>)
        fun onBackspace()
        fun onSpace()
        fun onEnter()
        fun onCandidate(value: String)
        fun onTranslate()
        fun onVoice()
        fun onClipboard()
        fun onEmoji(value: String)
        fun onHandwritingRequested()
        fun onHandwritingRecognize(view: HandwritingPadView)
        fun onSettings()
        fun onStylusPrimary()
        fun onStylusSecondary()
    }

    var callbacks: Callbacks? = null

    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    private val status = TextView(context)
    private val candidates = LinearLayout(context)
    private val content = FrameLayout(context)
    private val keyboardPanel = LinearLayout(context)
    private val keyboardSurface = KeyboardSurface(context)
    private var shifted = false

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(18, 20, 25))

        status.apply {
            setTextColor(Color.rgb(214, 219, 230))
            textSize = 12f
            setPadding(dp(10), dp(4), dp(10), dp(4))
            visibility = GONE
        }
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val candidateScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(candidates.apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(2), dp(4), dp(2))
            })
        }
        addView(candidateScroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(42)))
        addView(buildToolbar(), LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))

        keyboardPanel.orientation = VERTICAL
        keyboardSurface.listener = object : KeyboardSurface.Listener {
            override fun onTap(character: Char) = callbacks?.onCharacter(character) ?: Unit
            override fun onSwipe(trace: List<Char>) = callbacks?.onSwipe(trace) ?: Unit
            override fun onStylusPrimaryButton() = callbacks?.onStylusPrimary() ?: Unit
            override fun onStylusSecondaryButton() = callbacks?.onStylusSecondary() ?: Unit
        }
        keyboardPanel.addView(keyboardSurface, LayoutParams(LayoutParams.MATCH_PARENT, dp(186)))
        keyboardPanel.addView(buildBottomRow(), LayoutParams(LayoutParams.MATCH_PARENT, dp(54)))

        content.addView(keyboardPanel)
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        showKeyboard()
    }

    private fun buildToolbar(): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), 0, dp(2), 0)
        }

        fun tool(label: String, action: () -> Unit): Button =
            Button(context).apply {
                text = label
                textSize = 15f
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { action() }
            }

        listOf(
            tool("⌨") { showKeyboard() },
            tool("🌐") { callbacks?.onTranslate() },
            tool("🎙") { callbacks?.onVoice() },
            tool("📋") { callbacks?.onClipboard() },
            tool("😀") { showEmojiPanel() },
            tool("✍") { callbacks?.onHandwritingRequested() },
            tool("⚙") { callbacks?.onSettings() }
        ).forEach { row.addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)) }
        return row
    }

    private fun buildBottomRow(): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        fun key(label: String, weight: Float = 1f, action: () -> Unit) {
            row.addView(Button(context).apply {
                text = label
                textSize = 16f
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { action() }
            }, LayoutParams(0, LayoutParams.MATCH_PARENT, weight))
        }

        key("⇧") {
            shifted = !shifted
            keyboardSurface.shifted = shifted
        }
        key(",", 0.8f) { callbacks?.onCharacter(',') }
        key("space", 3.2f) { callbacks?.onSpace() }
        key(".", 0.8f) { callbacks?.onCharacter('.') }
        key("⌫") { callbacks?.onBackspace() }
        key("↵") { callbacks?.onEnter() }
        return row
    }

    fun setStatus(message: String?) {
        status.text = message.orEmpty()
        status.visibility = if (message.isNullOrBlank()) GONE else VISIBLE
    }

    fun setCandidates(values: List<String>) {
        candidates.removeAllViews()
        values.take(5).forEach { value ->
            candidates.addView(Button(context).apply {
                text = value
                isAllCaps = false
                minWidth = dp(68)
                setOnClickListener { callbacks?.onCandidate(value) }
            }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
    }

    fun showKeyboard() {
        content.removeAllViews()
        detachFromParent(keyboardPanel)
        content.addView(keyboardPanel)
    }

    fun showClipboardPanel(items: List<String>) {
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        if (items.isEmpty()) {
            panel.addView(TextView(context).apply {
                text = "Clipboard is empty for this KeySwiper session."
                setTextColor(Color.WHITE)
                setPadding(dp(8), dp(12), dp(8), dp(12))
            })
        } else {
            items.take(8).forEach { item ->
                panel.addView(Button(context).apply {
                    text = item.replace("\n", " ").take(90)
                    isAllCaps = false
                    setOnClickListener {
                        callbacks?.onEmoji(item)
                        showKeyboard()
                    }
                })
            }
        }
        swapContent(panel)
    }

    private fun showEmojiPanel() {
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val groups = listOf(
            listOf("😀", "😄", "😂", "🥹", "😍", "🥰", "😘", "😎"),
            listOf("❤️", "❤️‍🔥", "💕", "✨", "🔥", "👍", "🙌", "🙏"),
            listOf("🤔", "😅", "😢", "😭", "😡", "🤯", "🥳", "🫶")
        )

        groups.forEach { group ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            group.forEach { emoji ->
                row.addView(Button(context).apply {
                    text = emoji
                    textSize = 20f
                    setPadding(0, 0, 0, 0)
                    setOnClickListener { callbacks?.onEmoji(emoji) }
                }, LayoutParams(0, dp(52), 1f))
            }
            panel.addView(row)
        }
        panel.addView(Button(context).apply {
            text = "Back to keyboard"
            setOnClickListener { showKeyboard() }
        })
        swapContent(panel)
    }

    fun showHandwritingPanel(onReady: (HandwritingPadView) -> Unit) {
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        val pad = HandwritingPadView(context).apply {
            onStylusPrimaryButton = { callbacks?.onStylusPrimary() }
        }
        panel.addView(pad, LayoutParams(LayoutParams.MATCH_PARENT, dp(185)))

        val controls = LinearLayout(context).apply { orientation = HORIZONTAL }
        controls.addView(Button(context).apply {
            text = "Clear"
            setOnClickListener { pad.clearInk() }
        }, LayoutParams(0, dp(52), 1f))
        controls.addView(Button(context).apply {
            text = "Recognize"
            setOnClickListener { callbacks?.onHandwritingRecognize(pad) }
        }, LayoutParams(0, dp(52), 1f))
        controls.addView(Button(context).apply {
            text = "Keyboard"
            setOnClickListener { showKeyboard() }
        }, LayoutParams(0, dp(52), 1f))

        panel.addView(controls)
        swapContent(panel)
        onReady(pad)
    }

    private fun swapContent(view: View) {
        content.removeAllViews()
        detachFromParent(view)
        content.addView(view)
    }

    private fun detachFromParent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }
}
