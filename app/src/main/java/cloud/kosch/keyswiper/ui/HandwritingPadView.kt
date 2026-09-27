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
        if (event.toolType(0) == MotionEvent.TOOL_TYPE_STYLUS &&
            (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 &&
            event.actionMasked == MotionEvent.ACTION_DOWN
        ) {
            onStylusPrimaryButton?.invoke()
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
        if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS &&
            event.actionButton == MotionEvent.BUTTON_STYLUS_PRIMARY
        ) {
            onStylusPrimaryButton?.invoke()
            return true
        }
        return super.onGenericMotionEvent(event)
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
