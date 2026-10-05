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
import cloud.kosch.keyswiper.ui.HoldRepeater
import cloud.kosch.keyswiper.clipboard.ClipboardEntry
import cloud.kosch.keyswiper.clipboard.ClipboardCategory
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
        WordCommitRegressionChecks.run(context)
        checkShiftAndClipboard(context)
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

        stroke(surface, MotionEvent.TOOL_TYPE_STYLUS, listOf(400f to 100f, 403f to 103f))
        check(taps == listOf('t') && swipes.isEmpty()) { "Pen tap must type exactly one key" }
        taps.clear()
        stroke(surface, MotionEvent.TOOL_TYPE_STYLUS,
            listOf(400f to 100f, 222f to 100f, 166f to 300f, 400f to 100f))
        check(taps.isEmpty() && swipes.size == 1) { "Pen loop must commit a swipe without a tap" }
        swipes.clear()

        stroke(surface, MotionEvent.TOOL_TYPE_FINGER,
            listOf(400f to 100f, 222f to 100f, 166f to 300f, 400f to 100f))
        check(swipes.size == 1 && taps.isEmpty()) { "Finger loop must commit a swipe" }
        swipes.clear()
        for (tool in listOf(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS)) {
            stroke(surface, tool, listOf(400f to 100f, 222f to 100f, 222f to -20f, 166f to 300f, 400f to 100f))
            check(swipes.size == 1 && taps.isEmpty()) { "Re-entry after crossing the edge must keep the original swipe" }
            check(swipes.single().points.all { it.x in 0f..1f && it.y in 0f..1f })
            swipes.clear()
            stroke(surface, tool, listOf(400f to 100f, 222f to 100f, 166f to 300f, -30f to 300f))
            check(swipes.size == 1 && taps.isEmpty()) { "Lifting outside must finish the valid swipe once" }
            swipes.clear()
            stroke(surface, tool, listOf(400f to -20f, 222f to 100f, 166f to 300f))
            check(swipes.isEmpty() && taps.isEmpty()) { "Input starting outside cannot become a keyboard gesture" }
        }

        // A pen can take over from palm contact and survive the palm lifting first.
        val palm = Contact(7, MotionEvent.TOOL_TYPE_FINGER, 800f, 550f)
        val pen = Contact(9, MotionEvent.TOOL_TYPE_STYLUS, 400f, 100f)
        contactEvent(surface, MotionEvent.ACTION_DOWN, listOf(palm))
        contactEvent(surface, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(palm, pen))
        contactEvent(surface, MotionEvent.ACTION_MOVE, listOf(palm, pen.copy(x = 222f)))
        contactEvent(surface, MotionEvent.ACTION_POINTER_UP, listOf(palm, pen.copy(x = 222f)))
        contactEvent(surface, MotionEvent.ACTION_MOVE, listOf(pen.copy(x = 166f, y = 300f)))
        contactEvent(surface, MotionEvent.ACTION_UP, listOf(pen))
        check(swipes.size == 1 && taps.isEmpty()) { "Palm contact must not corrupt the active pen path" }
        swipes.clear()

        contactEvent(surface, MotionEvent.ACTION_DOWN, listOf(pen))
        contactEvent(surface, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(pen, palm))
        contactEvent(surface, MotionEvent.ACTION_MOVE, listOf(pen.copy(x = 222f), palm))
        contactEvent(surface, MotionEvent.ACTION_POINTER_UP, listOf(pen.copy(x = 166f, y = 300f), palm))
        contactEvent(surface, MotionEvent.ACTION_UP, listOf(palm))
        check(swipes.size == 1 && taps.isEmpty()) { "Pen lift must finish once even while palm stays down" }
        swipes.clear()

        val finger = pen.copy(tool = MotionEvent.TOOL_TYPE_FINGER)
        contactEvent(surface, MotionEvent.ACTION_DOWN, listOf(finger))
        contactEvent(surface, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(finger, palm))
        contactEvent(surface, MotionEvent.ACTION_MOVE, listOf(finger.copy(x = 222f), palm))
        contactEvent(surface, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(finger.copy(x = 222f), palm))
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
                    awaitPopupLayout(surface) { vowelPopup ->
                        val screen = IntArray(2).also { surface.getLocationOnScreen(it) }
                        check(vowelPopup.bottom <= screen[1] + surface.height / 3f + 3 * density) {
                            "Alternatives must appear above their key, in the correct window coordinates"
                        }
                        contactEvent(surface, MotionEvent.ACTION_UP, listOf(pen))
                        check(commits == listOf("ä")) { "Holding a vowel must commit only its umlaut" }
                        surface.setLayout(profile, true)
                        val finger = Contact(0, MotionEvent.TOOL_TYPE_FINGER, surface.width / 20f, surface.height / 6f)
                        contactEvent(surface, MotionEvent.ACTION_DOWN, listOf(finger))
                        awaitPopupLayout(surface) { numberPopup ->
                            surface.getLocationOnScreen(screen)
                            check(numberPopup.bottom <= screen[1] + 3 * density)
                            val choice = finger.copy(x = numberPopup.left + numberPopup.width() * 0.3f - screen[0],
                                y = numberPopup.centerY() - screen[1])
                            contactEvent(surface, MotionEvent.ACTION_MOVE, listOf(choice))
                            contactEvent(surface, MotionEvent.ACTION_UP, listOf(choice))
                            check(commits == listOf("ä", "½")) { "Sliding into a popup must select the variant without the base key" }
                            check(!surface.isShowingAlternatives)
                            surface.setLayout(profile, false)
                            val dotBounds = checkNotNull(surface.keyBounds("."))
                            val dot = Contact(0, MotionEvent.TOOL_TYPE_STYLUS, dotBounds.centerX(), dotBounds.centerY())
                            contactEvent(surface, MotionEvent.ACTION_DOWN, listOf(dot))
                            contactEvent(surface, MotionEvent.ACTION_UP, listOf(dot))
                            check(commits.last() == ".") { "The key beside M must tap a period" }
                            contactEvent(surface, MotionEvent.ACTION_DOWN, listOf(dot))
                            awaitPopupLayout(surface) {
                                contactEvent(surface, MotionEvent.ACTION_UP, listOf(dot))
                                check(commits.takeLast(2) == listOf(".", ",")) { "Holding period must choose punctuation without an extra period" }
                                root.removeView(surface)
                                Log.i("KeySwiperInputChecks", "PASS: attached pen/finger long-press alternatives")
                                Log.i("KeySwiperInputChecks", "PASS: period beside M and held punctuation")
                                runHoldChecks(root, onComplete)
                            }
                        }
                    }
                }
            }
        })
    }

    private fun checkShiftAndClipboard(context: Context) {
        val root = KeyboardRootView(context)
        val externalText = StringBuilder()
        root.callbacks = java.lang.reflect.Proxy.newProxyInstance(
            KeyboardRootView.Callbacks::class.java.classLoader, arrayOf(KeyboardRootView.Callbacks::class.java)
        ) { _, method, args ->
            if (method.name == "onCharacter") externalText.append(args!![0])
            null
        } as KeyboardRootView.Callbacks
        layout(root)
        val keys = descendants(root).filterIsInstance<KeyboardSurface>().single()
        val shift = checkNotNull(keys.keyBounds(KeyboardLayoutProfiles.SHIFT))
        val backspace = checkNotNull(keys.keyBounds(KeyboardLayoutProfiles.BACKSPACE))
        val firstBottom = checkNotNull(keys.keyBounds(if (keys.currentLayoutId() == "de-qwertz") "y" else "z"))
        check(shift.right < firstBottom.left && shift.width() > firstBottom.width())
        check(backspace.left > checkNotNull(keys.keyBounds("p")).right)
        val dot = checkNotNull(keys.keyBounds("."))
        val m = checkNotNull(keys.keyBounds("m"))
        check(dot.left > m.right && dot.centerY() == m.centerY())
        check(descendants(root).filterIsInstance<Button>().none { it.text.toString() in listOf("!", "?", ":", ";", ",", ".") })
        fun tap(bounds: android.graphics.RectF) = stroke(keys, MotionEvent.TOOL_TYPE_STYLUS,
            listOf(bounds.centerX() to bounds.centerY(), bounds.centerX() to bounds.centerY()))
        tap(shift); tap(shift)
        check(keys.shifted && keys.capsLocked) { "Two Shift taps must lock capitals" }
        tap(checkNotNull(keys.keyBounds("a")))
        check(externalText.toString() == "A" && keys.capsLocked)
        tap(shift)
        check(!keys.capsLocked && !keys.shifted)
        externalText.setLength(0)
        root.showClipboardPanel(listOf(ClipboardEntry("check", "Testeintrag", ClipboardCategory.TEXT, 0, null, true)))
        root.callbacks?.onCharacter('k')
        val search = descendants(root).filterIsInstance<EditText>().single()
        check(search.text.toString() == "k" && externalText.isEmpty()) { "Clipboard search must never type into the external app" }
        root.callbacks?.onBackspace()
        check(search.text.isEmpty())
        root.showKeyboard()
        root.callbacks?.onCharacter('x')
        check(externalText.toString() == "x") { "Closing clipboard must restore normal app input" }
        val vocabulary = cloud.kosch.keyswiper.language.UserVocabularyStore(context)
        vocabulary.rememberWord("KeySwiperProbe", "de")
        vocabulary.observeWord("keyswiperprobe", listOf("de"))
        check(vocabulary.prefixMatches("keysw", emptyList(), 20).any { it.first == "KeySwiperProbe" })
        vocabulary.forgetWord("KeySwiperProbe")
        val learning = cloud.kosch.keyswiper.prediction.PredictionLearningStore(object : android.content.ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                super.getSharedPreferences("input_checks_$name", mode)
        })
        repeat(12) { learning.learnTransition(listOf("anders", "probe", "häufig")) }
        learning.learnTransition(listOf("kontext", "probe", "passend"))
        check(learning.learnedFollowers(listOf("kontext", "probe"), 4).first().first == "passend") {
            "Exact longer context must beat a more frequent unrelated bigram"
        }
        learning.learnTransition(listOf("neu", "probe", "aktuell"))
        check(learning.learnedFollowers(listOf("neu", "probe"), 4).first().first == "aktuell") {
            "New learning must invalidate the follower index"
        }
        learning.reset()
        Log.i("KeySwiperInputChecks", "PASS: Shift caps lock, command layout, clipboard input isolation and personal spelling")
    }

    private fun runHoldChecks(root: FrameLayout, onComplete: () -> Unit) {
        val button = Button(root.context)
        var clicks = 0
        var repeats = 0
        button.setOnClickListener { clicks++ }
        HoldRepeater(button, { repeats++ }).attach()
        root.addView(button, FrameLayout.LayoutParams(160, 100))
        button.post {
            val finger = Contact(0, MotionEvent.TOOL_TYPE_FINGER, 50f, 40f)
            contactEvent(button, MotionEvent.ACTION_DOWN, listOf(finger))
            contactEvent(button, MotionEvent.ACTION_UP, listOf(finger))
            check(clicks == 1 && repeats == 0)
            contactEvent(button, MotionEvent.ACTION_DOWN, listOf(finger))
            button.postDelayed({
                check(repeats > 0) { "Holding Space must start repeat without opening language selection" }
                contactEvent(button, MotionEvent.ACTION_UP, listOf(finger))
                val stopped = repeats
                contactEvent(button, MotionEvent.ACTION_DOWN, listOf(finger))
                contactEvent(button, MotionEvent.ACTION_CANCEL, listOf(finger))
                button.postDelayed({
                    check(repeats == stopped && clicks == 1) { "Release/cancel must stop repeats and must not add a tap" }
                    root.removeView(button)
                    checkClipboardHistory(root.context)
                    Log.i("KeySwiperInputChecks", "PASS: held key release/cancel and clipboard delete/undo/persistence")
                    onComplete()
                }, 550)
            }, 700)
        }
    }

    @android.annotation.TargetApi(28)
    private fun checkClipboardHistory(context: Context) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        manager.setPrimaryClip(android.content.ClipData.newPlainText("check", "  Clipboard Probe  "))
        val controller = cloud.kosch.keyswiper.clipboard.ClipboardController(context)
        val entry = controller.items().first { it.text == "  Clipboard Probe  " }
        check(controller.delete(entry.id))
        check(controller.items().none { it.id == entry.id }) { "Deleted current clip must not reappear on opening" }
        check(controller.undoDelete() && controller.textFor(entry.id) == "  Clipboard Probe  ")
        controller.save(entry.id, "Bearbeiteter Textbaustein")
        val edited = controller.items().first { it.text == "Bearbeiteter Textbaustein" }
        controller.togglePin(edited.id)
        val restored = cloud.kosch.keyswiper.clipboard.ClipboardController(context)
        check(restored.textFor(edited.id) == "Bearbeiteter Textbaustein")
        restored.delete(edited.id)
        manager.clearPrimaryClip()
    }

    private fun awaitPopupLayout(surface: KeyboardSurface, onReady: (android.graphics.RectF) -> Unit) {
        val deadline = SystemClock.uptimeMillis() + ViewConfiguration.getLongPressTimeout() + 10_000L
        val checkLayout = object : Runnable {
            override fun run() {
                val bounds = surface.alternativesBoundsOnScreen
                if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    onReady(bounds)
                    return
                }
                check(SystemClock.uptimeMillis() < deadline) { "Long-press popup did not become laid out" }
                // Showing a PopupWindow is asynchronous. A fixed sleep after long-press
                // can expire before its first layout on a busy emulator.
                surface.postDelayed(this, 32L)
            }
        }
        surface.post(checkLayout)
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
