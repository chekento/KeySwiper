package cloud.kosch.keyswiper.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/** One pointer owns the gesture; a drag never inserts a space. */
class SpacebarCursorController(
    private val view: View,
    private val onMove: (Int) -> Unit,
    private val onGesture: (Boolean) -> Unit
) {
    private val threshold = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
    private val stepWidth = 12f * view.resources.displayMetrics.density
    private var pointer = -1
    private var originX = 0f
    private var stepX = 0f
    private var dragging = false
    private var cancelled = false

    fun cancel() {
        if (dragging) onGesture(false)
        pointer = -1
        dragging = false
        cancelled = true
        view.isPressed = false
        view.parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun move(x: Float, y: Float) {
        if (cancelled) return
        if (y !in 0f..view.height.toFloat()) { cancel(); return }
        if (!dragging && abs(x - originX) > threshold) {
            dragging = true
            stepX = originX + if (x > originX) threshold else -threshold
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onGesture(true)
        }
        if (dragging) {
            val steps = ((x - stepX) / stepWidth).toInt()
            if (steps != 0) {
                stepX += steps * stepWidth
                onMove(steps.coerceIn(-64, 64))
            }
        }
    }

    fun attach() {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancel()
                    pointer = event.getPointerId(event.actionIndex)
                    originX = event.getX(event.actionIndex)
                    stepX = originX
                    cancelled = event.getToolType(event.actionIndex) == MotionEvent.TOOL_TYPE_ERASER
                    view.isPressed = !cancelled
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = event.findPointerIndex(pointer)
                    if (index < 0) cancel() else move(event.getX(index), event.getY(index))
                }
                MotionEvent.ACTION_UP -> {
                    val index = event.findPointerIndex(pointer)
                    val valid = index >= 0 && (Build.VERSION.SDK_INT < 33 || event.flags and MotionEvent.FLAG_CANCELED == 0)
                    if (valid) move(event.getX(index), event.getY(index))
                    val click = valid && !cancelled && !dragging && event.getX(index) in 0f..view.width.toFloat()
                    cancel()
                    if (click) view.performClick()
                }
                MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointer) cancel()
                MotionEvent.ACTION_CANCEL -> cancel()
            }
            true
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) = cancel()
        })
    }
}
