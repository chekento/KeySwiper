package cloud.kosch.keyswiper.debug

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import cloud.kosch.keyswiper.input.SwipeTrace
import cloud.kosch.keyswiper.handwriting.SystemHandwritingInkView
import cloud.kosch.keyswiper.ui.HandwritingPadView
import cloud.kosch.keyswiper.ui.KeyboardLayoutProfiles
import cloud.kosch.keyswiper.ui.KeyboardRootView
import cloud.kosch.keyswiper.ui.KeyboardSurface
import cloud.kosch.keyswiper.ui.StylusUi

/** Debug-only checks against Android's real MotionEvent/View implementation. */
object InputRegressionChecks {
    // The smoke host is a platform Activity; this detached test button only
    // substitutes scheduling and does not use AppCompat theming or widgets.
    @SuppressLint("AppCompatCustomView")
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
        check(taps.isEmpty() && swipes.size == 1) { "Pen loop must commit a swipe without a tap" }
        swipes.clear()

        stroke(surface, MotionEvent.TOOL_TYPE_FINGER,
            listOf(450f to 100f, 250f to 100f, 166f to 300f, 450f to 100f))
        check(swipes.size == 1 && taps.isEmpty()) { "Finger loop must commit a swipe" }
        swipes.clear()
        for (tool in listOf(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS)) {
            stroke(surface, tool, listOf(450f to 100f, 250f to 100f, 250f to -20f, 166f to 300f, 450f to 100f))
            check(swipes.size == 1 && taps.isEmpty()) { "Re-entry after crossing the edge must keep the original swipe" }
            check(swipes.single().points.all { it.x in 0f..1f && it.y in 0f..1f })
            swipes.clear()
            stroke(surface, tool, listOf(450f to 100f, 250f to 100f, 166f to 300f, -30f to 300f))
            check(swipes.size == 1 && taps.isEmpty()) { "Lifting outside must finish the valid swipe once" }
            swipes.clear()
            stroke(surface, tool, listOf(450f to -20f, 250f to 100f, 166f to 300f))
            check(swipes.isEmpty() && taps.isEmpty()) { "Input starting outside cannot become a keyboard gesture" }
        }

        // A pen can take over from palm contact and survive the palm lifting first.
        val palm = Contact(7, MotionEvent.TOOL_TYPE_FINGER, 800f, 550f)
        val pen = Contact(9, MotionEvent.TOOL_TYPE_STYLUS, 450f, 100f)
        contactEvent(surface, MotionEvent.ACTION_DOWN, listOf(palm))
        contactEvent(surface, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(palm, pen))
        contactEvent(surface, MotionEvent.ACTION_MOVE, listOf(palm, pen.copy(x = 250f)))
        contactEvent(surface, MotionEvent.ACTION_POINTER_UP, listOf(palm, pen.copy(x = 250f)))
        contactEvent(surface, MotionEvent.ACTION_MOVE, listOf(pen.copy(x = 166f, y = 300f)))
        contactEvent(surface, MotionEvent.ACTION_UP, listOf(pen))
        check(swipes.size == 1 && taps.isEmpty()) { "Palm contact must not corrupt the active pen path" }
        swipes.clear()

        contactEvent(surface, MotionEvent.ACTION_DOWN, listOf(pen))
        contactEvent(surface, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(pen, palm))
        contactEvent(surface, MotionEvent.ACTION_MOVE, listOf(pen.copy(x = 250f), palm))
        contactEvent(surface, MotionEvent.ACTION_POINTER_UP, listOf(pen.copy(x = 166f, y = 300f), palm))
        contactEvent(surface, MotionEvent.ACTION_UP, listOf(palm))
        check(swipes.size == 1 && taps.isEmpty()) { "Pen lift must finish once even while palm stays down" }
        swipes.clear()

        val finger = pen.copy(tool = MotionEvent.TOOL_TYPE_FINGER)
        contactEvent(surface, MotionEvent.ACTION_DOWN, listOf(finger))
        contactEvent(surface, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(finger, palm))
        contactEvent(surface, MotionEvent.ACTION_MOVE, listOf(finger.copy(x = 250f), palm))
        contactEvent(surface, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(finger.copy(x = 250f), palm))
        contactEvent(surface, MotionEvent.ACTION_MOVE, listOf(finger.copy(x = 166f, y = 300f)))
        contactEvent(surface, MotionEvent.ACTION_UP, listOf(finger))
        check(swipes.size == 1 && taps.isEmpty()) { "A second finger/hand contact must not cancel the first finger swipe" }
        swipes.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            contactEvent(surface, MotionEvent.ACTION_DOWN, listOf(pen))
            contactEvent(surface, MotionEvent.ACTION_UP, listOf(pen), MotionEvent.FLAG_CANCELED)
            check(swipes.isEmpty() && taps.isEmpty()) { "Canceled contacts must not type" }
        }

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
        val writingPad = checkNotNull(pad)
        check(writingPad.height > 0 && letterSurface.height > 0)
        check(topInRoot(writingPad, root) + writingPad.height <= topInRoot(letterSurface, root)) {
            "Handwriting pad must stay above the visible letter keyboard"
        }
        val ink = SystemHandwritingInkView(context)
        contactEvent(ink, MotionEvent.ACTION_DOWN, listOf(pen), inkTarget = true)
        contactEvent(ink, MotionEvent.ACTION_UP, listOf(pen.copy(x = 480f)), inkTarget = true)
        contactEvent(ink, MotionEvent.ACTION_DOWN, listOf(pen.copy(x = 510f)), inkTarget = true)
        check(ink.isStrokeInProgress && ink.hasInk())
        ink.drainInk()
        check(ink.isStrokeInProgress && ink.hasInk()) { "Recognition must preserve an active new stroke" }
        contactEvent(ink, MotionEvent.ACTION_CANCEL, listOf(pen), inkTarget = true)
        check(!ink.isStrokeInProgress && !ink.hasInk())
        Log.i("KeySwiperInputChecks", "PASS: pen and finger taps/swipes, menu selection, palm contact, boundaries, handwriting")
    }

    fun runAttached(root: FrameLayout, onComplete: () -> Unit) {
        val surface = KeyboardSurface(root.context)
        val profile = KeyboardLayoutProfiles.byId("de-qwertz")
        surface.setLayout(profile, false)
        val commits = mutableListOf<String>()
        surface.listener = object : KeyboardSurface.Listener {
            override fun onTap(character: Char) { commits.add(character.toString()) }
            override fun onText(value: String) { commits.add(value) }
            override fun onSwipe(trace: SwipeTrace) { error("Long press must not swipe") }
            override fun onBackspace() = Unit
            override fun onStylusPrimaryButton() = Unit
            override fun onStylusSecondaryButton() = Unit
        }
        val density = root.resources.displayMetrics.density
        root.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (180 * density).toInt()).apply {
            topMargin = (80 * density).toInt()
        })
        surface.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (surface.width <= 0 || surface.height <= 0) return
                surface.viewTreeObserver.removeOnGlobalLayoutListener(this)
                surface.post {
            val pen = Contact(0, MotionEvent.TOOL_TYPE_STYLUS, surface.width / 18f, surface.height / 2f)
            contactEvent(surface, MotionEvent.ACTION_DOWN, listOf(pen))
            surface.postDelayed({
                check(surface.isShowingAlternatives) { "Attached pen long press must display alternatives" }
                contactEvent(surface, MotionEvent.ACTION_UP, listOf(pen))
                check(commits == listOf("ä")) { "Holding a vowel must commit only its umlaut" }
                surface.setLayout(profile, true)
                val finger = Contact(0, MotionEvent.TOOL_TYPE_FINGER, surface.width / 20f, surface.height / 6f)
                contactEvent(surface, MotionEvent.ACTION_DOWN, listOf(finger))
                surface.postDelayed({
                    check(surface.isShowingAlternatives) { "Numbers need long-press variants for fingers too" }
                    contactEvent(surface, MotionEvent.ACTION_UP, listOf(finger))
                    check(commits == listOf("ä", "¹")) { "Long press must never also type the base key" }
                    check(!surface.isShowingAlternatives)
                    root.removeView(surface)
                    Log.i("KeySwiperInputChecks", "PASS: attached pen/finger long-press alternatives")
                    onComplete()
                }, ViewConfiguration.getLongPressTimeout().toLong() + 120L)
            }, ViewConfiguration.getLongPressTimeout().toLong() + 120L)
                }
            }
        })
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

    private data class Contact(val id: Int, val tool: Int, val x: Float, val y: Float)

    private fun contactEvent(view: View, action: Int, contacts: List<Contact>, flags: Int = 0, inkTarget: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        val properties = contacts.map { contact -> MotionEvent.PointerProperties().apply {
            id = contact.id; toolType = contact.tool
        } }.toTypedArray()
        val coordinates = contacts.map { contact -> MotionEvent.PointerCoords().apply {
            x = contact.x; y = contact.y; pressure = 1f; size = 1f
        } }.toTypedArray()
        val event = MotionEvent.obtain(now, now, action, contacts.size, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, if (contacts.any { it.tool == MotionEvent.TOOL_TYPE_STYLUS }) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN, flags)
        try {
            if (inkTarget) (view as SystemHandwritingInkView).consumeStylusEvent(event)
            else view.dispatchTouchEvent(event)
        } finally { event.recycle() }
    }
}
