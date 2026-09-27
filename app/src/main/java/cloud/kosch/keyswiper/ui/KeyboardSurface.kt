package cloud.kosch.keyswiper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import cloud.kosch.keyswiper.input.SwipePoint
import cloud.kosch.keyswiper.input.SwipeTrace
import kotlin.math.hypot
import kotlin.math.max

class KeyboardSurface(context: Context) : View(context) {
    interface Listener {
        fun onTap(character: Char)
        fun onSwipe(trace: SwipeTrace)
        fun onStylusPrimaryButton()
        fun onStylusSecondaryButton()
    }

    var listener: Listener? = null

    var shifted: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private data class Cell(
        val value: Char,
        val bounds: RectF
    )

    private var layoutProfile =
        KeyboardLayoutProfiles.byId("en-qwerty")
    private var symbolMode = false
    private var rows: List<String> =
        layoutProfile.letterRows

    private val cells = mutableListOf<Cell>()
    private val traceChars = mutableListOf<Char>()
    private val tracePoints = mutableListOf<SwipePoint>()
    private val path = Path()

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var lastPrimaryButtonEventMs = Long.MIN_VALUE
    private var lastSecondaryButtonEventMs = Long.MIN_VALUE

    private val keyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(49, 53, 64)
        }

    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize =
                resources.displayMetrics.scaledDensity * 20f
        }

    private val tracePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(118, 144, 255)
            style = Paint.Style.STROKE
            strokeWidth =
                resources.displayMetrics.density * 4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    fun setLayout(
        profile: KeyboardLayoutProfile,
        symbols: Boolean
    ) {
        layoutProfile = profile
        symbolMode = symbols
        rows = if (symbols) {
            KeyboardLayoutProfiles.symbolRows
        } else {
            profile.letterRows
        }

        shifted = false
        rebuildCells(
            width.toFloat(),
            height.toFloat()
        )
        invalidate()
    }

    fun currentLayoutId(): String =
        layoutProfile.id

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        rebuildCells(
            w.toFloat(),
            h.toFloat()
        )
    }

    private fun rebuildCells(
        w: Float,
        h: Float
    ) {
        cells.clear()
        if (w <= 0f || h <= 0f) return

        val gap =
            resources.displayMetrics.density * 3f
        val rowHeight =
            h / rows.size.coerceAtLeast(1)

        rows.forEachIndexed { rowIndex, row ->
            val slots =
                max(10, row.length)
            val keyWidth =
                (w - gap * (slots + 1)) / slots
            val usedWidth =
                row.length * keyWidth +
                    (row.length - 1).coerceAtLeast(0) * gap
            val offset =
                ((w - usedWidth) / 2f)
                    .coerceAtLeast(gap)

            val top =
                rowIndex * rowHeight + gap
            val bottom =
                (rowIndex + 1) *
                    rowHeight - gap

            row.forEachIndexed { index, c ->
                val left =
                    offset +
                        index *
                        (keyWidth + gap)

                cells.add(
                    Cell(
                        value = c,
                        bounds = RectF(
                            left,
                            top,
                            left + keyWidth,
                            bottom
                        )
                    )
                )
            }
        }
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)
        canvas.drawColor(
            Color.rgb(28, 31, 38)
        )

        val radius =
            resources.displayMetrics.density * 7f

        for (cell in cells) {
            canvas.drawRoundRect(
                cell.bounds,
                radius,
                radius,
                keyPaint
            )

            val value =
                if (
                    shifted &&
                    !symbolMode &&
                    cell.value.isLetter()
                ) {
                    cell.value.uppercaseChar()
                } else {
                    cell.value
                }

            val baseline =
                cell.bounds.centerY() -
                    (
                        labelPaint.ascent() +
                            labelPaint.descent()
                        ) / 2f

            canvas.drawText(
                value.toString(),
                cell.bounds.centerX(),
                baseline,
                labelPaint
            )
        }

        if (
            dragging &&
            !symbolMode
        ) {
            canvas.drawPath(
                path,
                tracePaint
            )
        }
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {
        if (event.pointerCount == 0) {
            return false
        }

        if (
            event.getToolType(0) ==
            MotionEvent.TOOL_TYPE_STYLUS
        ) {
            if (
                (
                    event.buttonState and
                        MotionEvent.BUTTON_STYLUS_PRIMARY
                    ) != 0 &&
                event.actionMasked ==
                MotionEvent.ACTION_DOWN
            ) {
                dispatchPrimaryButton(
                    event.eventTime
                )
            }

            if (
                (
                    event.buttonState and
                        MotionEvent.BUTTON_STYLUS_SECONDARY
                    ) != 0 &&
                event.actionMasked ==
                MotionEvent.ACTION_DOWN
            ) {
                dispatchSecondaryButton(
                    event.eventTime
                )
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                traceChars.clear()
                tracePoints.clear()
                path.reset()
                path.moveTo(
                    event.x,
                    event.y
                )

                if (!symbolMode) {
                    addTraceCharacter(
                        event.x,
                        event.y
                    )
                    addTracePoint(
                        event.x,
                        event.y,
                        event.eventTime
                    )
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!symbolMode) {
                    if (
                        hypot(
                            event.x - downX,
                            event.y - downY
                        ) >
                        resources
                            .displayMetrics
                            .density * 12f
                    ) {
                        dragging = true
                    }

                    for (
                        i in 0 until
                            event.historySize
                    ) {
                        val hx =
                            event.getHistoricalX(i)
                        val hy =
                            event.getHistoricalY(i)

                        path.lineTo(
                            hx,
                            hy
                        )
                        addTraceCharacter(
                            hx,
                            hy
                        )
                        addTracePoint(
                            hx,
                            hy,
                            event.getHistoricalEventTime(i)
                        )
                    }

                    path.lineTo(
                        event.x,
                        event.y
                    )
                    addTraceCharacter(
                        event.x,
                        event.y
                    )
                    addTracePoint(
                        event.x,
                        event.y,
                        event.eventTime
                    )
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!symbolMode) {
                    addTraceCharacter(
                        event.x,
                        event.y
                    )
                    addTracePoint(
                        event.x,
                        event.y,
                        event.eventTime
                    )
                }

                if (
                    !symbolMode &&
                    dragging &&
                    traceChars.size > 1 &&
                    tracePoints.size > 1
                ) {
                    listener?.onSwipe(
                        SwipeTrace(
                            points =
                                tracePoints.toList(),
                            touchedKeys =
                                traceChars.toList(),
                            layoutId =
                                layoutProfile.id
                        )
                    )
                } else {
                    charAt(
                        event.x,
                        event.y
                    )?.let {
                        val value =
                            if (
                                shifted &&
                                !symbolMode &&
                                it.isLetter()
                            ) {
                                it.uppercaseChar()
                            } else {
                                it
                            }

                        listener?.onTap(
                            value
                        )
                    }
                }

                dragging = false
                traceChars.clear()
                tracePoints.clear()
                path.reset()
                invalidate()
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                traceChars.clear()
                tracePoints.clear()
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

    override fun onGenericMotionEvent(
        event: MotionEvent
    ): Boolean {
        if (
            event.actionMasked ==
            MotionEvent.ACTION_BUTTON_PRESS
        ) {
            when (event.actionButton) {
                MotionEvent.BUTTON_STYLUS_PRIMARY -> {
                    dispatchPrimaryButton(
                        event.eventTime
                    )
                    return true
                }

                MotionEvent.BUTTON_STYLUS_SECONDARY -> {
                    dispatchSecondaryButton(
                        event.eventTime
                    )
                    return true
                }
            }
        }

        return super.onGenericMotionEvent(
            event
        )
    }

    private fun dispatchPrimaryButton(
        eventTimeMs: Long
    ) {
        if (
            eventTimeMs -
                lastPrimaryButtonEventMs <
            80L
        ) {
            return
        }

        lastPrimaryButtonEventMs =
            eventTimeMs
        listener?.onStylusPrimaryButton()
    }

    private fun dispatchSecondaryButton(
        eventTimeMs: Long
    ) {
        if (
            eventTimeMs -
                lastSecondaryButtonEventMs <
            80L
        ) {
            return
        }

        lastSecondaryButtonEventMs =
            eventTimeMs
        listener?.onStylusSecondaryButton()
    }

    private fun addTraceCharacter(
        x: Float,
        y: Float
    ) {
        val c =
            charAt(x, y) ?: return

        if (
            traceChars.lastOrNull() != c
        ) {
            traceChars.add(c)
        }
    }

    private fun addTracePoint(
        x: Float,
        y: Float,
        timeMs: Long
    ) {
        if (
            width <= 0 ||
            height <= 0
        ) {
            return
        }

        val normalized =
            SwipePoint(
                x =
                    (x / width.toFloat())
                        .coerceIn(0f, 1f),
                y =
                    (y / height.toFloat())
                        .coerceIn(0f, 1f),
                timeMs = timeMs
            )

        val previous =
            tracePoints.lastOrNull()

        if (previous != null) {
            val distance =
                hypot(
                    normalized.x -
                        previous.x,
                    normalized.y -
                        previous.y
                )
            val dt =
                normalized.timeMs -
                    previous.timeMs

            if (
                distance < 0.006f &&
                dt < 12L
            ) {
                return
            }
        }

        if (tracePoints.size >= 96) {
            tracePoints.removeAt(
                1.coerceAtMost(
                    tracePoints.lastIndex
                )
            )
        }

        tracePoints.add(normalized)
    }

    private fun charAt(
        x: Float,
        y: Float
    ): Char? =
        cells
            .firstOrNull {
                it.bounds.contains(
                    x,
                    y
                )
            }
            ?.value
}
