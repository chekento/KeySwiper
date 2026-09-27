package cloud.kosch.keyswiper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class KeyboardSurface(context: Context) : View(context) {
    interface Listener {
        fun onTap(character: Char)
        fun onSwipe(trace: List<Char>)
        fun onStylusPrimaryButton()
        fun onStylusSecondaryButton()
    }

    var listener: Listener? = null
    var shifted: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private data class Cell(val value: Char, val bounds: RectF)

    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val cells = mutableListOf<Cell>()
    private val traceChars = mutableListOf<Char>()
    private val path = Path()
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(49, 53, 64)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = resources.displayMetrics.scaledDensity * 20f
    }
    private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(118, 144, 255)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuildCells(w.toFloat(), h.toFloat())
    }

    private fun rebuildCells(w: Float, h: Float) {
        cells.clear()
        if (w <= 0f || h <= 0f) return

        val gap = resources.displayMetrics.density * 3f
        val keyWidth = (w - gap * 11) / 10f
        val rowHeight = h / 3f

        rows.forEachIndexed { rowIndex, row ->
            val offset = when (rowIndex) {
                0 -> gap
                1 -> gap + keyWidth * 0.5f
                else -> gap + keyWidth * 1.5f
            }
            val top = rowIndex * rowHeight + gap
            val bottom = (rowIndex + 1) * rowHeight - gap

            row.forEachIndexed { index, c ->
                val left = offset + index * (keyWidth + gap)
                cells.add(Cell(c, RectF(left, top, left + keyWidth, bottom)))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(28, 31, 38))
        val radius = resources.displayMetrics.density * 7f

        for (cell in cells) {
            canvas.drawRoundRect(cell.bounds, radius, radius, keyPaint)
            val value = if (shifted) cell.value.uppercaseChar() else cell.value
            val baseline = cell.bounds.centerY() -
                (labelPaint.ascent() + labelPaint.descent()) / 2f
            canvas.drawText(value.toString(), cell.bounds.centerX(), baseline, labelPaint)
        }

        if (dragging) canvas.drawPath(path, tracePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount == 0) return false

        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            if ((event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 &&
                event.actionMasked == MotionEvent.ACTION_DOWN
            ) listener?.onStylusPrimaryButton()

            if ((event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0 &&
                event.actionMasked == MotionEvent.ACTION_DOWN
            ) listener?.onStylusSecondaryButton()
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                traceChars.clear()
                path.reset()
                path.moveTo(event.x, event.y)
                addTraceCharacter(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(event.x - downX, event.y - downY) >
                    resources.displayMetrics.density * 12f
                ) dragging = true

                for (i in 0 until event.historySize) {
                    path.lineTo(event.getHistoricalX(i), event.getHistoricalY(i))
                    addTraceCharacter(event.getHistoricalX(i), event.getHistoricalY(i))
                }
                path.lineTo(event.x, event.y)
                addTraceCharacter(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                addTraceCharacter(event.x, event.y)
                if (dragging && traceChars.size > 1) {
                    listener?.onSwipe(traceChars.toList())
                } else {
                    charAt(event.x, event.y)?.let {
                        listener?.onTap(if (shifted) it.uppercaseChar() else it)
                    }
                }
                dragging = false
                traceChars.clear()
                path.reset()
                invalidate()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                traceChars.clear()
                path.reset()
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
                    listener?.onStylusPrimaryButton()
                    return true
                }
                MotionEvent.BUTTON_STYLUS_SECONDARY -> {
                    listener?.onStylusSecondaryButton()
                    return true
                }
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun addTraceCharacter(x: Float, y: Float) {
        val c = charAt(x, y) ?: return
        if (traceChars.lastOrNull() != c) traceChars.add(c)
    }

    private fun charAt(x: Float, y: Float): Char? =
        cells.firstOrNull { it.bounds.contains(x, y) }?.value
}
