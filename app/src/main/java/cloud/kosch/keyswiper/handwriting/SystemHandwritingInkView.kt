package cloud.kosch.keyswiper.handwriting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.digitalink.recognition.Ink

class SystemHandwritingInkView(context: Context) : View(context) {

    var onStrokeFinished: (() -> Unit)? = null

    private var strokeBuilder: Ink.Stroke.Builder? = null
    private val strokes = mutableListOf<Ink.Stroke>()
    private val paths = mutableListOf<Path>()
    private var activePath: Path? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(63, 191, 255)
        strokeWidth = resources.displayMetrics.density * 3.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        alpha = 220
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paths.forEach { canvas.drawPath(it, linePaint) }
        activePath?.let { canvas.drawPath(it, linePaint) }
    }

    fun consumeStylusEvent(event: MotionEvent): Boolean {
        if (event.pointerCount == 0) return false

        val tool = event.getToolType(0)
        if (
            tool != MotionEvent.TOOL_TYPE_STYLUS &&
            tool != MotionEvent.TOOL_TYPE_ERASER
        ) {
            return false
        }

        if (tool == MotionEvent.TOOL_TYPE_ERASER) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                clearInk()
            }
            return true
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

                strokeBuilder?.build()?.let { strokes.add(it) }
                activePath?.let { paths.add(it) }

                strokeBuilder = null
                activePath = null
                invalidate()
                onStrokeFinished?.invoke()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                strokeBuilder = null
                activePath = null
                invalidate()
                return true
            }
        }

        return false
    }

    fun hasInk(): Boolean =
        strokes.isNotEmpty() || strokeBuilder != null

    val isStrokeInProgress: Boolean
        get() = strokeBuilder != null

    fun discardLastStroke(): Boolean {
        if (strokes.isEmpty()) return false

        strokes.removeAt(strokes.lastIndex)
        if (paths.isNotEmpty()) {
            paths.removeAt(paths.lastIndex)
        }
        invalidate()
        return true
    }

    fun drainInk(): Ink {
        val builder = Ink.builder()
        strokes.forEach { builder.addStroke(it) }
        val result = builder.build()
        // Completed ink can be drained without erasing a newly started stroke.
        strokes.clear()
        paths.clear()
        invalidate()
        return result
    }

    fun clearInk() {
        strokeBuilder = null
        strokes.clear()
        paths.clear()
        activePath = null
        invalidate()
    }
}
