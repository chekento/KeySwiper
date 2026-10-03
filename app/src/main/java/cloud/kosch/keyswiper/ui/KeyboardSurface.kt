package cloud.kosch.keyswiper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import cloud.kosch.keyswiper.input.GestureIntentSample
import cloud.kosch.keyswiper.input.PointerKind
import cloud.kosch.keyswiper.input.SwipeIntentClassifier
import cloud.kosch.keyswiper.input.SwipePoint
import cloud.kosch.keyswiper.input.SwipeTrace
import cloud.kosch.keyswiper.settings.Prefs
import kotlin.math.hypot
import kotlin.math.min

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
    private var downTimeMs = 0L
    private var lastPathX = 0f
    private var lastPathY = 0f
    private var pathLengthPx = 0f
    private var pointerKind = PointerKind.TOUCH
    private var dragging = false
    private var gestureActive = false
    private var activePointerId = -1
    private var gestureCancelled = false
    private var pressedToken: String? = null
    private var downToken: String? = null

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
                    20f
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

    private fun rebuildCells(widthPx: Float, heightPx: Float) {
        cells.clear()
        if (widthPx <= 0f || heightPx <= 0f) return

        val rows = displayRows()
        val rowHeight = heightPx / rows.size.coerceAtLeast(1)
        val density = resources.displayMetrics.density
        val horizontalInset = min(1.2f * density, widthPx / 100f)
        val verticalInset = min(2f * density, rowHeight / 10f)

        rows.forEachIndexed { rowIndex, row ->
            val slotWidth = widthPx / row.size.coerceAtLeast(1)
            row.forEachIndexed { index, token ->
                // Slot centers exactly match KeyboardGeometry. Visual gaps are
                // included in the touch target, so narrow pen taps never vanish.
                cells.add(Cell(
                    token = token,
                    bounds = RectF(
                        index * slotWidth + horizontalInset,
                        rowIndex * rowHeight + verticalInset,
                        (index + 1) * slotWidth - horizontalInset,
                        (rowIndex + 1) * rowHeight - verticalInset
                    ),
                    special = token == BACKSPACE_TOKEN
                ))
            }
        }
        labelPaint.textSize = min(
            resources.displayMetrics.scaledDensity * 20f,
            cells.minOf { min(it.bounds.height() * 0.72f, it.bounds.width() * 0.78f) }
        )
        StylusUi.usePointerInput(this)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount == 0) return false
        val density = resources.displayMetrics.density.coerceAtLeast(0.5f)
        var action = event.actionMasked
        var pointerIndex = if (action == MotionEvent.ACTION_DOWN) event.actionIndex
            else event.findPointerIndex(activePointerId)

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            val incoming = event.getToolType(event.actionIndex)
            if (incoming == MotionEvent.TOOL_TYPE_STYLUS || incoming == MotionEvent.TOOL_TYPE_ERASER) {
                // Prefer a pen arriving while the hand is already resting on glass.
                pointerIndex = event.actionIndex
                action = MotionEvent.ACTION_DOWN
            } else if (gestureActive && pointerKind == PointerKind.STYLUS) {
                return true // Additional finger/palm contact must not break a pen swipe.
            } else {
                resetGestureState()
                invalidate()
                return true
            }
        }
        if (action == MotionEvent.ACTION_POINTER_UP) {
            if (event.getPointerId(event.actionIndex) != activePointerId) return true
            action = MotionEvent.ACTION_UP
        }
        if (action == MotionEvent.ACTION_CANCEL ||
            (action == MotionEvent.ACTION_UP && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                (event.flags and MotionEvent.FLAG_CANCELED) != 0)
        ) {
            resetGestureState()
            invalidate()
            return true
        }
        if (pointerIndex < 0) return false
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                resetGestureState()
                gestureActive = true
                activePointerId = event.getPointerId(pointerIndex)
                downX = x
                downY = y
                downTimeMs = event.eventTime
                lastPathX = x
                lastPathY = y
                val tool = event.getToolType(pointerIndex)
                pointerKind = if (
                    tool == MotionEvent.TOOL_TYPE_STYLUS ||
                    tool == MotionEvent.TOOL_TYPE_ERASER
                ) PointerKind.STYLUS else PointerKind.TOUCH
                downToken = tokenAt(x, y)
                gestureCancelled = downToken == null || tool == MotionEvent.TOOL_TYPE_ERASER

                if (pointerKind == PointerKind.STYLUS) {
                    if ((event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0) {
                        gestureCancelled = true
                        dispatchPrimaryButton(event.eventTime)
                    }
                    if ((event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0) {
                        gestureCancelled = true
                        dispatchSecondaryButton(event.eventTime)
                    }
                }
                pressedToken = if (gestureCancelled) null else downToken
                path.moveTo(x, y)
                if (canSwipe()) {
                    addTraceCharacter(x, y)
                    addTracePoint(x, y, event.eventTime)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (!gestureActive) return false
                for (index in 0 until event.historySize) {
                    recordMotion(
                        event.getHistoricalX(pointerIndex, index),
                        event.getHistoricalY(pointerIndex, index),
                        event.getHistoricalEventTime(index),
                        density
                    )
                }
                recordMotion(x, y, event.eventTime, density)
                if (action == MotionEvent.ACTION_MOVE) {
                    invalidate()
                    return true
                }

                val sample = GestureIntentSample(
                    pointerKind,
                    hypot(x - downX, y - downY) / density,
                    pathLengthPx / density,
                    traceChars.distinct().size,
                    (event.eventTime - downTimeMs).coerceAtLeast(0L)
                )
                if (!gestureCancelled && canSwipe() && dragging &&
                    tracePoints.size > 1 && SwipeIntentClassifier.shouldCommitSwipe(sample)
                ) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onSwipe(SwipeTrace(
                        points = tracePoints.toList(),
                        touchedKeys = traceChars.toList(),
                        layoutId = layoutProfile.id
                    ))
                } else if (!gestureCancelled && !dragging) {
                    val token = downToken
                    if (token != null) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (token == BACKSPACE_TOKEN) {
                            listener?.onBackspace()
                        } else {
                            val character = token.first()
                            listener?.onTap(
                                if (shifted && !symbolMode && character.isLetter())
                                    character.uppercaseChar() else character
                            )
                        }
                    }
                }
                resetGestureState()
                invalidate()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun canSwipe(): Boolean =
        !symbolMode &&
            downToken?.let { it.length == 1 && it.first().isLetter() } == true

    private fun recordMotion(x: Float, y: Float, timeMs: Long, density: Float) {
        if (gestureCancelled) return
        if (x < 0f || y < 0f || x >= width || y >= height) {
            gestureCancelled = true
            pressedToken = null
            return
        }
        val displacementDp = hypot(x - downX, y - downY) / density
        if (!canSwipe()) {
            // Symbols and command keys remain buttons for both tools.
            if (displacementDp > 12f) {
                gestureCancelled = true
                pressedToken = null
            }
            return
        }
        if (SwipeIntentClassifier.shouldStartDrag(pointerKind, displacementDp)) {
            dragging = true
            pressedToken = null
        }
        pathLengthPx += hypot(x - lastPathX, y - lastPathY)
        lastPathX = x
        lastPathY = y
        path.lineTo(x, y)
        addTraceCharacter(x, y)
        addTracePoint(x, y, timeMs)
    }

    private fun resetGestureState() {
        gestureActive = false
        activePointerId = -1
        gestureCancelled = false
        dragging = false
        pressedToken = null
        downToken = null
        pathLengthPx = 0f
        traceChars.clear()
        tracePoints.clear()
        path.reset()
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
            resetGestureState()
            invalidate()
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
            lastPrimaryButtonEventMs != Long.MIN_VALUE && eventTimeMs -
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
            lastSecondaryButtonEventMs != Long.MIN_VALUE && eventTimeMs -
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

        if (tracePoints.size >= 192) {
            // Keep the full route instead of repeatedly deleting its beginning.
            val reduced = tracePoints.filterIndexed { index, _ ->
                index % 2 == 0 || index == tracePoints.lastIndex
            }
            tracePoints.clear()
            tracePoints.addAll(reduced)
        }

        tracePoints.add(
            normalized
        )
    }

    private fun tokenAt(x: Float, y: Float): String? {
        if (x < 0f || y < 0f || x >= width || y >= height) return null
        val rows = displayRows()
        val row = rows[(y / height * rows.size).toInt().coerceIn(0, rows.lastIndex)]
        return row[(x / width * row.size).toInt().coerceIn(0, row.lastIndex)]
    }

    companion object {
        private const val BACKSPACE_TOKEN =
            "__BACKSPACE__"
    }
}
