package cloud.kosch.keyswiper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.digitalink.recognition.Ink

class HandwritingPadView(context: Context) : View(context) {
    var onStylusPrimaryButton: (() -> Unit)? = null
    var onStylusSecondaryButton: (() -> Unit)? = null
    private var lastPrimaryButtonEventMs = Long.MIN_VALUE
    private var lastSecondaryButtonEventMs = Long.MIN_VALUE

    private var inkBuilder: Ink.Builder = Ink.builder()
    private var strokeBuilder: Ink.Stroke.Builder? = null
    private val paths = mutableListOf<Path>()
    private var activePath: Path? = null

    private val backgroundPaint = Paint().apply { color = Color.rgb(245, 247, 250) }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(45, 52, 65)
        strokeWidth = resources.displayMetrics.density * 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        paths.forEach { canvas.drawPath(it, linePaint) }
        activePath?.let { canvas.drawPath(it, linePaint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount == 0) return false
        val outside = event.x < 0f || event.y < 0f || event.x >= width || event.y >= height ||
            (0 until event.historySize).any {
                event.getHistoricalX(it) < 0f || event.getHistoricalY(it) < 0f ||
                    event.getHistoricalX(it) >= width || event.getHistoricalY(it) >= height
            }
        if (outside) {
            strokeBuilder = null
            activePath = null
            invalidate()
            return event.actionMasked != MotionEvent.ACTION_DOWN
        }
        if (
            event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS &&
            event.actionMasked == MotionEvent.ACTION_DOWN
        ) {
            if (
                (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
            ) {
                dispatchPrimaryButton(event.eventTime)
            }

            if (
                (event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0
            ) {
                dispatchSecondaryButton(event.eventTime)
            }
            if ((event.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or
                    MotionEvent.BUTTON_STYLUS_SECONDARY)) != 0) {
                strokeBuilder = null
                activePath = null
                invalidate()
                return true
            }
        }

        val x = event.x
        val y = event.y
        val t = event.eventTime

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                strokeBuilder = Ink.Stroke.builder().also {
                    it.addPoint(Ink.Point.create(x, y, t))
                }
                activePath = Path().apply { moveTo(x, y) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val stroke = strokeBuilder ?: return true
                for (i in 0 until event.historySize) {
                    val hx = event.getHistoricalX(i)
                    val hy = event.getHistoricalY(i)
                    val ht = event.getHistoricalEventTime(i)
                    stroke.addPoint(Ink.Point.create(hx, hy, ht))
                    activePath?.lineTo(hx, hy)
                }
                stroke.addPoint(Ink.Point.create(x, y, t))
                activePath?.lineTo(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                strokeBuilder?.addPoint(Ink.Point.create(x, y, t))
                activePath?.lineTo(x, y)
                strokeBuilder?.build()?.let { inkBuilder.addStroke(it) }
                activePath?.let { paths.add(it) }
                strokeBuilder = null
                activePath = null
                invalidate()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                strokeBuilder = null
                activePath = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS) {
            when (event.actionButton) {
                MotionEvent.BUTTON_STYLUS_PRIMARY -> {
                    dispatchPrimaryButton(event.eventTime)
                    return true
                }
                MotionEvent.BUTTON_STYLUS_SECONDARY -> {
                    dispatchSecondaryButton(event.eventTime)
                    return true
                }
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun dispatchPrimaryButton(eventTimeMs: Long) {
        if (lastPrimaryButtonEventMs != Long.MIN_VALUE && eventTimeMs - lastPrimaryButtonEventMs < 80L) return
        lastPrimaryButtonEventMs = eventTimeMs
        onStylusPrimaryButton?.invoke()
    }

    private fun dispatchSecondaryButton(eventTimeMs: Long) {
        if (lastSecondaryButtonEventMs != Long.MIN_VALUE && eventTimeMs - lastSecondaryButtonEventMs < 80L) return
        lastSecondaryButtonEventMs = eventTimeMs
        onStylusSecondaryButton?.invoke()
    }

    fun snapshotInk(): Ink = inkBuilder.build()

    fun clearInk() {
        inkBuilder = Ink.builder()
        strokeBuilder = null
        paths.clear()
        activePath = null
        invalidate()
    }
}
