package cloud.kosch.keyswiper.debug

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import cloud.kosch.keyswiper.input.SwipeTrace
import cloud.kosch.keyswiper.ui.HandwritingPadView
import cloud.kosch.keyswiper.ui.KeyboardLayoutProfiles
import cloud.kosch.keyswiper.ui.KeyboardRootView
import cloud.kosch.keyswiper.ui.KeyboardSurface
import cloud.kosch.keyswiper.ui.StylusUi

/** Debug-only checks against Android's real MotionEvent/View implementation. */
object InputRegressionChecks {
    fun run(context: Context) {
        val surface = KeyboardSurface(context)
        surface.setLayout(KeyboardLayoutProfiles.byId("en-qwerty"), false)
        layout(surface)
        val taps = mutableListOf<Char>()
        val swipes = mutableListOf<SwipeTrace>()
        surface.listener = object : KeyboardSurface.Listener {
            override fun onTap(character: Char) { taps.add(character) }
            override fun onSwipe(trace: SwipeTrace) { swipes.add(trace) }
            override fun onBackspace() = Unit
            override fun onStylusPrimaryButton() = Unit
            override fun onStylusSecondaryButton() = Unit
        }

        stroke(surface, MotionEvent.TOOL_TYPE_STYLUS, listOf(450f to 100f, 453f to 103f))
        check(taps == listOf('t') && swipes.isEmpty()) { "Pen tap must type exactly one key" }
        taps.clear()
        stroke(surface, MotionEvent.TOOL_TYPE_STYLUS,
            listOf(450f to 100f, 250f to 100f, 166f to 300f, 450f to 100f))
        check(taps.isEmpty() && swipes.isEmpty()) { "Pen drag on keys must neither type nor swipe" }

        stroke(surface, MotionEvent.TOOL_TYPE_FINGER,
            listOf(450f to 100f, 250f to 100f, 166f to 300f, 450f to 100f))
        check(swipes.size == 1 && taps.isEmpty()) { "Finger loop must commit a swipe" }
        swipes.clear()
        stroke(surface, MotionEvent.TOOL_TYPE_FINGER,
            listOf(450f to 100f, 250f to -20f, 450f to 100f))
        check(swipes.isEmpty() && taps.isEmpty()) { "Crossing surface boundary must cancel input" }

        // A visual gap between keys remains tappable rather than dropping pen taps.
        stroke(surface, MotionEvent.TOOL_TYPE_STYLUS, listOf(101f to 100f, 101f to 100f))
        check(taps == listOf('w')) { "Key gaps must belong to the adjacent touch target" }

        var selections = 0
        val menu = LinearLayout(context)
        val button = object : Button(context) {
            // Detached test views have no queue to deliver View's posted click.
            // Let View use its synchronous performClick fallback.
            override fun post(action: Runnable): Boolean = false
        }.apply { setOnClickListener { selections++ } }
        val field = EditText(context)
        menu.addView(button)
        menu.addView(field)
        StylusUi.usePointerInput(menu)
        layout(button)
        stroke(button, MotionEvent.TOOL_TYPE_STYLUS, listOf(50f to 50f, 50f to 50f))
        check(selections == 1) { "Pen must select menu buttons" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            check(!field.isAutoHandwritingEnabled) { "Menu text fields must not auto-start handwriting" }
        }

        val root = KeyboardRootView(context)
        var pad: HandwritingPadView? = null
        root.showHandwritingPanel { pad = it }
        root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val letterSurface = descendants(root).filterIsInstance<KeyboardSurface>().single()
        check(pad != null && pad!!.height > 0 && letterSurface.height > 0)
        check(topInRoot(pad!!, root) + pad!!.height <= topInRoot(letterSurface, root)) {
            "Handwriting pad must stay above the visible letter keyboard"
        }
        Log.i("KeySwiperInputChecks", "PASS: pen taps, menu selection, finger swipe, boundaries, upper handwriting pad")
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
        else emptyList()

    private fun topInRoot(view: View, root: View): Int {
        var current = view
        var top = 0
        while (current !== root) {
            top += current.top
            current = current.parent as View
        }
        return top
    }

    private fun layout(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1000, 600)
    }

    private fun stroke(view: View, tool: Int, points: List<Pair<Float, Float>>) {
        val start = SystemClock.uptimeMillis()
        points.forEachIndexed { index, point ->
            val action = when (index) {
                0 -> MotionEvent.ACTION_DOWN
                points.lastIndex -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = tool }
            val coordinates = MotionEvent.PointerCoords().apply {
                x = point.first; y = point.second; pressure = 1f; size = 1f
            }
            val event = MotionEvent.obtain(start, start + index * 30L, action, 1,
                arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0,
                if (tool == MotionEvent.TOOL_TYPE_STYLUS) InputDevice.SOURCE_STYLUS
                else InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }
}
