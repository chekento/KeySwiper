package cloud.kosch.keyswiper.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import cloud.kosch.keyswiper.input.HoldAcceleration

/** A touch owner that stops immediately on release, cancel, leaving or detaching. */
class HoldRepeater(private val view: View, private val onStep: (Long) -> Unit,
    private val onEnd: () -> Unit = {}) {
    private var started = 0L
    private var active = false
    var repeated = false
        private set
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            if (!view.isShown || !view.isAttachedToWindow) { stop(); return }
            repeated = true
            val elapsed = SystemClock.uptimeMillis() - started
            onStep(elapsed)
            if (active) view.postDelayed(this, HoldAcceleration.step(elapsed).intervalMs)
        }
    }
    fun start() {
        stop()
        repeated = false
        started = SystemClock.uptimeMillis()
        active = true
        view.postDelayed(tick, 420)
    }
    fun stop() {
        val wasActive = active
        active = false
        view.removeCallbacks(tick)
        if (wasActive) onEnd()
    }
    fun attach() {
        var pointer = -1
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pointer = event.getPointerId(0); view.isPressed = true; start() }
                MotionEvent.ACTION_MOVE -> {
                    val i = event.findPointerIndex(pointer)
                    if (i < 0 || event.getX(i) !in 0f..view.width.toFloat() || event.getY(i) !in 0f..view.height.toFloat()) {
                        stop(); view.isPressed = false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val click = active && !repeated && event.actionMasked == MotionEvent.ACTION_UP &&
                        (android.os.Build.VERSION.SDK_INT < 33 || event.flags and MotionEvent.FLAG_CANCELED == 0)
                    stop(); view.isPressed = false
                    if (click) view.performClick()
                }
                MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointer) {
                    stop(); view.isPressed = false
                }
            }
            true
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) = stop()
        })
    }
}
