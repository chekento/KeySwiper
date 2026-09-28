package cloud.kosch.keyswiper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import cloud.kosch.keyswiper.input.SwipePoint
import cloud.kosch.keyswiper.input.SwipeTrace
import cloud.kosch.keyswiper.settings.Prefs
import kotlin.math.hypot

class KeyboardSurface(
    context: Context
) : View(context) {

    interface Listener {
        fun onTap(character: Char)
        fun onBackspace()
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
        val token: String,
        val bounds: RectF,
        val special: Boolean
    )

    private var layoutProfile =
        KeyboardLayoutProfiles
            .byId(
                "en-qwerty"
            )

    private var symbolMode = false

    private val cells =
        mutableListOf<Cell>()

    private val traceChars =
        mutableListOf<Char>()

    private val tracePoints =
        mutableListOf<SwipePoint>()

    private val path =
        Path()

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var pressedToken: String? = null

    private var lastPrimaryButtonEventMs =
        Long.MIN_VALUE
    private var lastSecondaryButtonEventMs =
        Long.MIN_VALUE

    private var theme =
        KeyboardThemes.byId(
            Prefs.keyboardThemeId(
                context
            )
        )

    private val keyPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        )

    private val borderPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            style =
                Paint.Style.STROKE
            strokeWidth =
                resources
                    .displayMetrics
                    .density
        }

    private val labelPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            textAlign =
                Paint.Align.CENTER
            textSize =
                resources
                    .displayMetrics
                    .scaledDensity *
                    19f
            typeface =
                android.graphics.Typeface
                    .create(
                        "sans-serif-medium",
                        android.graphics
                            .Typeface.NORMAL
                    )
        }

    private val tracePaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            style =
                Paint.Style.STROKE
            strokeWidth =
                resources
                    .displayMetrics
                    .density *
                    4.2f
            strokeCap =
                Paint.Cap.ROUND
            strokeJoin =
                Paint.Join.ROUND
        }

    fun setLayout(
        profile: KeyboardLayoutProfile,
        symbols: Boolean
    ) {
        layoutProfile = profile
        symbolMode = symbols
        theme =
            KeyboardThemes.byId(
                Prefs.keyboardThemeId(
                    context
                )
            )
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

    private fun displayRows():
        List<List<String>> {
        val base =
            if (symbolMode) {
                KeyboardLayoutProfiles
                    .symbolRows
            } else {
                layoutProfile
                    .letterRows
            }

        return base
            .mapIndexed {
                    index,
                    row ->
                val tokens =
                    row.map {
                        it.toString()
                    }.toMutableList()

                if (
                    index ==
                    base.lastIndex
                ) {
                    tokens.add(
                        BACKSPACE_TOKEN
                    )
                }

                tokens
            }
    }

    private fun rebuildCells(
        widthPx: Float,
        heightPx: Float
    ) {
        cells.clear()

        if (
            widthPx <= 0f ||
            heightPx <= 0f
        ) {
            return
        }

        val rows =
            displayRows()

        val gap =
            resources
                .displayMetrics
                .density *
                3.2f

        val sidePadding =
            resources
                .displayMetrics
                .density *
                3.5f

        val rowHeight =
            heightPx /
                rows.size
                    .coerceAtLeast(1)

        rows.forEachIndexed {
                rowIndex,
                row ->

            val count =
                row.size
                    .coerceAtLeast(1)

            val usableWidth =
                widthPx -
                    sidePadding * 2f -
                    gap *
                    (
                        count -
                            1
                        )

            val keyWidth =
                usableWidth /
                    count

            val top =
                rowIndex *
                    rowHeight +
                    gap

            val bottom =
                (
                    rowIndex +
                        1
                    ) *
                    rowHeight -
                    gap

            row.forEachIndexed {
                    index,
                    token ->

                val left =
                    sidePadding +
                        index *
                        (
                            keyWidth +
                                gap
                            )

                cells.add(
                    Cell(
                        token = token,
                        bounds =
                            RectF(
                                left,
                                top,
                                left +
                                    keyWidth,
                                bottom
                            ),
                        special =
                            token ==
                                BACKSPACE_TOKEN
                    )
                )
            }
        }
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(
            canvas
        )

        canvas.drawColor(
            theme.background
        )

        val radius =
            resources
                .displayMetrics
                .density *
                theme.keyCornerDp

        borderPaint.color =
            theme.border
        tracePaint.color =
            theme.trace
        labelPaint.color =
            theme.textPrimary

        for (cell in cells) {
            keyPaint.color =
                when {
                    pressedToken ==
                        cell.token &&
                        !dragging ->
                        theme.keyPressed

                    cell.special ->
                        theme.keySpecial

                    else ->
                        theme.key
                }

            canvas.drawRoundRect(
                cell.bounds,
                radius,
                radius,
                keyPaint
            )

            canvas.drawRoundRect(
                cell.bounds,
                radius,
                radius,
                borderPaint
            )

            val label =
                if (
                    cell.token ==
                    BACKSPACE_TOKEN
                ) {
                    "⌫"
                } else {
                    val char =
                        cell.token
                            .first()

                    if (
                        shifted &&
                        !symbolMode &&
                        char.isLetter()
                    ) {
                        char
                            .uppercaseChar()
                            .toString()
                    } else {
                        char.toString()
                    }
                }

            val baseline =
                cell.bounds
                    .centerY() -
                    (
                        labelPaint.ascent() +
                            labelPaint
                                .descent()
                        ) /
                        2f

            canvas.drawText(
                label,
                cell.bounds
                    .centerX(),
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
        if (
            event.pointerCount ==
            0
        ) {
            return false
        }

        if (
            event.getToolType(0) ==
            MotionEvent.TOOL_TYPE_STYLUS
        ) {
            if (
                (
                    event.buttonState and
                        MotionEvent
                            .BUTTON_STYLUS_PRIMARY
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
                        MotionEvent
                            .BUTTON_STYLUS_SECONDARY
                    ) != 0 &&
                event.actionMasked ==
                MotionEvent.ACTION_DOWN
            ) {
                dispatchSecondaryButton(
                    event.eventTime
                )
            }
        }

        when (
            event.actionMasked
        ) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                pressedToken =
                    tokenAt(
                        event.x,
                        event.y
                    )
                traceChars.clear()
                tracePoints.clear()
                path.reset()
                path.moveTo(
                    event.x,
                    event.y
                )

                if (
                    !symbolMode &&
                    tokenAt(
                        event.x,
                        event.y
                    ) !=
                    BACKSPACE_TOKEN
                ) {
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
                            event.x -
                                downX,
                            event.y -
                                downY
                        ) >
                        resources
                            .displayMetrics
                            .density *
                        12f
                    ) {
                        dragging =
                            true
                        pressedToken =
                            null
                    }

                    for (
                        index in 0 until
                            event.historySize
                    ) {
                        val x =
                            event
                                .getHistoricalX(
                                    index
                                )
                        val y =
                            event
                                .getHistoricalY(
                                    index
                                )

                        path.lineTo(
                            x,
                            y
                        )
                        addTraceCharacter(
                            x,
                            y
                        )
                        addTracePoint(
                            x,
                            y,
                            event
                                .getHistoricalEventTime(
                                    index
                                )
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
                    traceChars.size >
                    1 &&
                    tracePoints.size >
                    1
                ) {
                    performHapticFeedback(
                        HapticFeedbackConstants
                            .KEYBOARD_TAP
                    )

                    listener
                        ?.onSwipe(
                            SwipeTrace(
                                points =
                                    tracePoints
                                        .toList(),
                                touchedKeys =
                                    traceChars
                                        .toList(),
                                layoutId =
                                    layoutProfile.id
                            )
                        )
                } else {
                    when (
                        val token =
                            tokenAt(
                                event.x,
                                event.y
                            )
                    ) {
                        BACKSPACE_TOKEN -> {
                            performHapticFeedback(
                                HapticFeedbackConstants
                                    .KEYBOARD_TAP
                            )
                            listener
                                ?.onBackspace()
                        }

                        null ->
                            Unit

                        else -> {
                            val char =
                                token.first()

                            val value =
                                if (
                                    shifted &&
                                    !symbolMode &&
                                    char.isLetter()
                                ) {
                                    char
                                        .uppercaseChar()
                                } else {
                                    char
                                }

                            performHapticFeedback(
                                HapticFeedbackConstants
                                    .KEYBOARD_TAP
                            )
                            listener
                                ?.onTap(
                                    value
                                )
                        }
                    }
                }

                dragging = false
                pressedToken = null
                traceChars.clear()
                tracePoints.clear()
                path.reset()
                invalidate()
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                pressedToken = null
                traceChars.clear()
                tracePoints.clear()
                path.reset()
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(
            event
        )
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
            MotionEvent
                .ACTION_BUTTON_PRESS
        ) {
            when (
                event.actionButton
            ) {
                MotionEvent
                    .BUTTON_STYLUS_PRIMARY -> {
                    dispatchPrimaryButton(
                        event.eventTime
                    )
                    return true
                }

                MotionEvent
                    .BUTTON_STYLUS_SECONDARY -> {
                    dispatchSecondaryButton(
                        event.eventTime
                    )
                    return true
                }
            }
        }

        return super
            .onGenericMotionEvent(
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
        listener
            ?.onStylusPrimaryButton()
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
        listener
            ?.onStylusSecondaryButton()
    }

    private fun addTraceCharacter(
        x: Float,
        y: Float
    ) {
        val token =
            tokenAt(
                x,
                y
            ) ?: return

        if (
            token ==
            BACKSPACE_TOKEN ||
            token.length !=
            1
        ) {
            return
        }

        val char =
            token.first()

        if (
            !char.isLetter()
        ) {
            return
        }

        if (
            traceChars
                .lastOrNull() !=
            char
        ) {
            traceChars.add(
                char
            )
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

        if (
            tokenAt(
                x,
                y
            ) ==
            BACKSPACE_TOKEN
        ) {
            return
        }

        val normalized =
            SwipePoint(
                x =
                    (
                        x /
                            width
                                .toFloat()
                        )
                        .coerceIn(
                            0f,
                            1f
                        ),
                y =
                    (
                        y /
                            height
                                .toFloat()
                        )
                        .coerceIn(
                            0f,
                            1f
                        ),
                timeMs =
                    timeMs
            )

        val previous =
            tracePoints
                .lastOrNull()

        if (
            previous != null
        ) {
            val distance =
                hypot(
                    normalized.x -
                        previous.x,
                    normalized.y -
                        previous.y
                )

            val delta =
                normalized.timeMs -
                    previous.timeMs

            if (
                distance <
                0.006f &&
                delta <
                12L
            ) {
                return
            }
        }

        if (
            tracePoints.size >=
            96
        ) {
            tracePoints.removeAt(
                1.coerceAtMost(
                    tracePoints
                        .lastIndex
                )
            )
        }

        tracePoints.add(
            normalized
        )
    }

    private fun tokenAt(
        x: Float,
        y: Float
    ): String? =
        cells
            .firstOrNull {
                it.bounds
                    .contains(
                        x,
                        y
                    )
            }
            ?.token

    companion object {
        private const val BACKSPACE_TOKEN =
            "__BACKSPACE__"
    }
}
