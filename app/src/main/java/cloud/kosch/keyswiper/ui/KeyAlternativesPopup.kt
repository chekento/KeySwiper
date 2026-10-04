package cloud.kosch.keyswiper.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow

/** One hold–slide–release interaction shared by letters, numbers, symbols and emoji. */
class KeyAlternativesPopup(private val context: Context, private val onCommit: (String) -> Unit) {
    private var window: PopupWindow? = null
    private var row: LinearLayout? = null
    private var values = emptyList<String>()
    private var buttons = emptyList<Button>()
    private var selected = 0
    private var initialX = 0f
    private var initialY = 0f
    private var selecting = false
    private var owner: View? = null
    val isShowing: Boolean get() = window?.isShowing == true
    internal val boundsOnScreen: RectF?
        get() {
            val panel = row ?: return null
            if (!isShowing || panel.width <= 0) return null
            val location = IntArray(2).also { panel.getLocationOnScreen(it) }
            return RectF(location[0].toFloat(), location[1].toFloat(),
                (location[0] + panel.width).toFloat(), (location[1] + panel.height).toFloat())
        }

    fun show(anchor: View, alternatives: List<String>, bounds: RectF? = null): Boolean {
        dismiss()
        if (alternatives.isEmpty() || anchor.windowToken == null) return false
        val density = context.resources.displayMetrics.density
        val theme = KeyboardThemes.byId(cloud.kosch.keyswiper.settings.Prefs.keyboardThemeId(context))
        values = alternatives.distinct().take(8)
        val location = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val rect = bounds ?: RectF(0f, 0f, anchor.width.toFloat(), anchor.height.toFloat())
        initialX = location[0] + rect.centerX()
        initialY = location[1] + rect.centerY()
        val width = minOf((values.size * 46 * density).toInt(), context.resources.displayMetrics.widthPixels - (12 * density).toInt())
        val height = (56 * density).toInt()
        val panel = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; elevation = 12 * density }
        buttons = values.map { value -> Button(context).apply {
            text = value; textSize = 20f; isAllCaps = false
            contentDescription = value
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setTextColor(theme.textPrimary)
            setOnClickListener { onCommit(value); dismiss() }
            panel.addView(this, LinearLayout.LayoutParams(0, height, 1f))
        } }
        StylusUi.usePointerInput(panel)
        row = panel
        owner = anchor
        selected = 0
        selecting = false
        highlight()
        val popup = PopupWindow(panel, width, height, false).apply {
            isOutsideTouchable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setBackgroundDrawable(GradientDrawable().apply {
                cornerRadius = 10 * density; setColor(theme.surfaceRaised)
                setStroke((density).toInt().coerceAtLeast(1), theme.accent)
            })
            elevation = 12 * density
        }
        window = popup
        val x = (initialX - width / 2).toInt().coerceIn(0, (context.resources.displayMetrics.widthPixels - width).coerceAtLeast(0))
        val y = (location[1] + rect.top - height - 6 * density).toInt().coerceAtLeast(0)
        // showAtLocation uses parent-window coordinates; selection uses screen coordinates.
        // IME windows can begin far below the display origin, unlike a full-screen Activity.
        val inWindow = IntArray(2).also { anchor.getLocationInWindow(it) }
        val windowOriginX = location[0] - inWindow[0]
        val windowOriginY = location[1] - inWindow[1]
        try {
            popup.showAtLocation(anchor, Gravity.TOP or Gravity.LEFT, x - windowOriginX, y - windowOriginY)
        } catch (_: WindowManager.BadTokenException) {
            dismiss()
            return false
        }
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        return true
    }

    fun setGestureOrigin(rawX: Float, rawY: Float) {
        initialX = rawX
        initialY = rawY
    }

    fun move(rawX: Float, rawY: Float) {
        val panel = row ?: return
        if (panel.width <= 0) return
        val density = context.resources.displayMetrics.density
        val location = IntArray(2).also { panel.getLocationOnScreen(it) }
        if (!selecting && kotlin.math.hypot(rawX - initialX, rawY - initialY) < 12 * density) return
        selecting = true
        selected = if (rawX < location[0] - 20 * density || rawX > location[0] + panel.width + 20 * density ||
            rawY < location[1] - 35 * density || rawY > initialY + 30 * density) -1
        else (((rawX - location[0]) / panel.width.coerceAtLeast(1)) * values.size).toInt().coerceIn(0, values.lastIndex)
        highlight()
    }

    fun commitSelection() {
        val value = values.getOrNull(selected)
        dismiss()
        if (value != null) onCommit(value)
    }

    fun dismiss() {
        window?.dismiss(); window = null; row = null; owner = null; values = emptyList(); buttons = emptyList()
    }

    private fun highlight() {
        val theme = KeyboardThemes.byId(cloud.kosch.keyswiper.settings.Prefs.keyboardThemeId(context))
        buttons.forEachIndexed { index, button ->
            button.setBackgroundColor(if (index == selected) theme.keyPressed else theme.surfaceRaised)
            button.isSelected = index == selected
        }
    }

    @SuppressLint("ClickableViewAccessibility") // Normal taps use Button.performClick; this listener handles only a consumed long press.
    fun attach(button: View, alternatives: () -> List<String>) {
        var downRawX = 0f
        var downRawY = 0f
        button.setOnLongClickListener {
            show(it, alternatives()).also { shown -> if (shown) setGestureOrigin(downRawX, downRawY) }
        }
        button.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { if (owner === view) dismiss() }
        })
        button.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                downRawX = event.rawX; downRawY = event.rawY
            }
            if (!isShowing || owner !== button) false else {
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> move(event.rawX, event.rawY)
                    MotionEvent.ACTION_UP -> { button.isPressed = false; button.cancelLongPress(); commitSelection() }
                    MotionEvent.ACTION_CANCEL -> { button.isPressed = false; dismiss() }
                }
                true
            }
        }
    }
}
