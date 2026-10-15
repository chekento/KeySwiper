package cloud.kosch.keyswiper.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import cloud.kosch.keyswiper.clipboard.ClipboardEntry
import cloud.kosch.keyswiper.clipboard.ClipboardRetention

/** Recycled text cards are the primary action; tools stay in overflow menus. */
class ClipboardPanelView(context: Context, private val theme: KeyboardThemeProfile,
    private val actions: KeyboardRootView.Callbacks, private val onEditor: (EditText?) -> Unit,
    private val onClose: () -> Unit) : LinearLayout(context) {
    private var entries = emptyList<ClipboardEntry>()
    private var shown = emptyList<ClipboardEntry>()
    private var filter = "Verlauf"
    private val cards = ListView(context).apply {
        divider = null; isFastScrollEnabled = true; cacheColorHint = android.graphics.Color.TRANSPARENT
    }
    private val body = FrameLayout(context)
    private val form = LinearLayout(context).apply { orientation = VERTICAL; visibility = GONE }
    private val empty = TextView(context)
    private val search = EditText(context)
    val searchEditor: EditText get() = search
    private val title = TextView(context)
    private val status = TextView(context)
    private val filters = LinearLayout(context)
    private val copySelection: Button
    private var expiry = 1440L
    private var editing = false
    private var saving = false
    private var saveGeneration = 0
    private var touching = false
    private var scrolling = false
    private var pendingRender = false
    private var previousQuery = ""
    private var previousFilter = "Verlauf"
    private val expiryRefresh = Runnable { render() }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun background(color: Int, accent: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(10).toFloat()
        if (accent) setStroke(dp(1), theme.accentSoft)
    }
    private fun button(label: String, description: String = label, action: () -> Unit) = Button(context).apply {
        text = label; contentDescription = description; isAllCaps = false; textSize = 15f
        setTextColor(theme.textPrimary)
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(4), 0, dp(4), 0); background = background(theme.surfaceRaised)
        if (Build.VERSION.SDK_INT >= 26) tooltipText = description
        setOnClickListener { action() }
    }
    private fun icon(label: String, description: String, action: () -> Unit) = button(label, description, action).apply {
        textSize = 18f
        background = RippleDrawable(ColorStateList.valueOf(theme.keyPressed), null, background(android.graphics.Color.WHITE))
    }
    private data class Card(val container: View, val row: LinearLayout, val text: TextView, val detail: TextView,
        val pin: Button, val options: Button, var entry: ClipboardEntry? = null)
    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = shown[position].id.take(15).toLongOrNull(16) ?: shown[position].id.hashCode().toLong()
        override fun hasStableIds() = true
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val holder = convertView?.tag as? Card ?: createCard()
            val entry = shown[position]
            holder.entry = entry
            holder.text.text = entry.text
            holder.detail.text = remaining(entry, System.currentTimeMillis())
            holder.row.background = background(theme.surfaceRaised, entry.pinned || entry.saved)
            holder.pin.text = if (entry.pinned) "◆" else "◇"
            holder.pin.contentDescription = if (entry.pinned) "Eintrag lösen" else "Eintrag anpinnen"
            holder.pin.setTextColor(if (entry.pinned) theme.accent else theme.textSecondary)
            return holder.container
        }
    }
    init {
        orientation = VERTICAL; setPadding(dp(7), dp(4), dp(7), dp(4)); setBackgroundColor(theme.background)
        filterTouchesWhenObscured = true
        if (Build.VERSION.SDK_INT >= 34) setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        title.apply { text = "Zwischenablage"; textSize = 15f; setTextColor(theme.textPrimary); typeface = android.graphics.Typeface.DEFAULT_BOLD }
        header.addView(title, LayoutParams(0, dp(36), 1f))
        copySelection = icon("⧉", "Markierten Text kopieren und hinzufügen") { actions.onClipboardCaptureSelection() }
        header.addView(copySelection, LayoutParams(dp(34), dp(34)))
        header.addView(icon("⇩", "Aktuellen Text aus der Android-Zwischenablage übernehmen") {
            actions.onClipboardImportSystem()
        }, LayoutParams(dp(34), dp(34)).apply { marginStart = dp(3) })
        header.addView(icon("⌕", "Zwischenablage durchsuchen") {
            if (search.visibility == VISIBLE && !editing) { search.visibility = GONE; onEditor(null); render() }
            else { finishEditing(); search.visibility = VISIBLE; search.requestFocus(); onEditor(search); render() }
        }, LayoutParams(dp(34), dp(34)).apply { marginStart = dp(3) })
        val more = icon("⋮", "Weitere Zwischenablage-Aktionen") {}
        more.setOnClickListener {
            PopupMenu(context, more).apply {
                menu.add("Neuer Textbaustein").setOnMenuItemClickListener { edit(null); true }
                menu.add("Löschen rückgängig").setOnMenuItemClickListener { actions.onClipboardUndoDelete(); true }
                menu.add("Temporären Verlauf löschen").setOnMenuItemClickListener { actions.onClipboardClearUnpinned(); true }
                listOf(10L to "10 Minuten", 60L to "1 Stunde", 1440L to "1 Tag", 10080L to "7 Tage", 43200L to "30 Tage").forEach { pair ->
                    menu.add((if (expiry == pair.first) "✓ " else "") + "Neue Einträge: " + pair.second)
                        .setOnMenuItemClickListener { actions.onClipboardExpiryChanged(pair.first); true }
                }
                show()
            }
        }
        header.addView(more, LayoutParams(dp(32), dp(34)).apply { marginStart = dp(3) })
        header.addView(icon("×", "Zwischenablage schließen", onClose), LayoutParams(dp(32), dp(34)).apply { marginStart = dp(3) })
        addView(header)
        listOf("Verlauf", "Pins", "Gespeichert").forEach { name ->
            filters.addView(button(name) { finishEditing(); filter = name; render() }.apply { textSize = 12f },
                LayoutParams(0, dp(27), 1f).apply { marginEnd = dp(3) })
        }
        addView(filters)
        search.apply {
            hint = "Texte durchsuchen"; textSize = 14f; setTextColor(theme.textPrimary); setHintTextColor(theme.textSecondary)
            setSingleLine(); showSoftInputOnFocus = false; visibility = GONE
            setOnFocusChangeListener { _, focused -> if (focused) onEditor(this) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (!editing) render() }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        addView(search, LayoutParams(LayoutParams.MATCH_PARENT, dp(36)))
        status.apply { textSize = 10f; setTextColor(theme.textSecondary); setPadding(dp(4), dp(2), dp(4), dp(2)) }
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, dp(20)))
        empty.apply { textSize = 14f; setTextColor(theme.textSecondary); setPadding(dp(10), dp(18), dp(10), dp(18)) }
        body.addView(cards, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        body.addView(empty, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        body.addView(form, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        cards.emptyView = empty
        cards.adapter = adapter
        cards.setOnItemClickListener { _, _, position, _ ->
            shown.getOrNull(position)?.let { actions.onClipboardInsert(it.id); onClose() }
        }
        cards.setOnItemLongClickListener { _, view, position, _ ->
            shown.getOrNull(position)?.let { showOptions(it, (view.tag as Card).options) }; true
        }
        cards.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, state: Int) {
                scrolling = state != AbsListView.OnScrollListener.SCROLL_STATE_IDLE
                if (!scrolling && !touching && pendingRender) render()
            }
            override fun onScroll(view: AbsListView?, first: Int, visible: Int, total: Int) = Unit
        })
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        StylusUi.usePointerInput(this)
    }
    private fun createCard(): Card {
        val container = FrameLayout(context).apply { setPadding(0, dp(4), 0, 0) }
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(9), dp(6), dp(3), dp(6)); minimumHeight = dp(52)
        }
        val preview = LinearLayout(context).apply { orientation = VERTICAL }
        val text = TextView(context).apply { textSize = 14f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setTextColor(theme.textPrimary) }
        val detail = TextView(context).apply { textSize = 10f; setTextColor(theme.textSecondary); setPadding(0, dp(3), 0, 0) }
        preview.addView(text); preview.addView(detail); row.addView(preview, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val pin = icon("◇", "Eintrag anpinnen") {}.apply { isFocusable = false; textSize = 15f }
        val options = icon("⋮", "Aktionen für diesen Text") {}.apply { isFocusable = false }
        row.addView(pin, LayoutParams(dp(32), dp(40))); row.addView(options, LayoutParams(dp(30), dp(40)))
        container.addView(row, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val holder = Card(container, row, text, detail, pin, options)
        container.tag = holder
        pin.setOnClickListener { holder.entry?.let { actions.onClipboardTogglePin(it.id) } }
        options.setOnClickListener { holder.entry?.let { showOptions(it, options) } }
        StylusUi.usePointerInput(container)
        return holder
    }
    private fun showOptions(entry: ClipboardEntry, anchor: View) {
        if (!ClipboardRetention.alive(entry, System.currentTimeMillis())) { showStatus("Eintrag bereits abgelaufen."); render(); return }
        PopupMenu(context, anchor).apply {
            menu.add("Kopieren").setOnMenuItemClickListener { actions.onClipboardCopy(entry.id); true }
            menu.add(if (entry.saved) "Dauerhaftes Speichern aufheben" else "Dauerhaft speichern")
                .setOnMenuItemClickListener { actions.onClipboardToggleSaved(entry.id); true }
            menu.add("Ansehen / Bearbeiten").setOnMenuItemClickListener { edit(entry); true }
            menu.add("Löschen").setOnMenuItemClickListener { actions.onClipboardDelete(entry.id); true }
            show()
        }
    }
    fun update(items: List<ClipboardEntry>, minutes: Long, hasSelection: Boolean = false, message: String? = null) {
        entries = items; expiry = minutes
        copySelection.visibility = if (hasSelection) VISIBLE else GONE
        status.text = message.orEmpty()
        status.visibility = if (message.isNullOrBlank()) GONE else VISIBLE
        if (!editing) render()
    }
    fun showStatus(message: String) { status.text = message; status.visibility = VISIBLE }
    fun saveCompletion(): (Boolean) -> Unit {
        val generation = saveGeneration
        return { success -> if (generation == saveGeneration) completeSave(success) }
    }
    private fun completeSave(success: Boolean) {
        if (!saving) return
        saving = false
        enableForm(true)
        if (success) finishEditing()
    }
    private fun enableForm(enabled: Boolean) {
        fun update(view: View) {
            view.isEnabled = enabled
            if (view is ViewGroup) for (index in 0 until view.childCount) update(view.getChildAt(index))
        }
        update(form)
    }
    fun setCompactEditorMode(active: Boolean) {
        filters.visibility = if (active) GONE else VISIBLE
        body.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, if (active) dp(92) else 0, if (active) 0f else 1f)
    }
    private fun remaining(entry: ClipboardEntry, now: Long): String {
        if (entry.saved) return "Dauerhaft gespeichert"
        if (entry.pinned) return "Angepinnt · ohne Ablauf"
        val minutes = (((entry.expiresAtMs ?: now) - now + 59_999) / 60_000).coerceAtLeast(1)
        return when {
            minutes >= 1440 -> ((minutes + 1439) / 1440).let { "Noch $it " + if (it == 1L) "Tag" else "Tage" }
            minutes >= 60 -> "Noch " + ((minutes + 59) / 60) + " Std."
            else -> "Noch " + minutes + " Min."
        }
    }
    private fun render() {
        removeCallbacks(expiryRefresh)
        if (editing) return
        if (touching || scrolling) { pendingRender = true; return }
        pendingRender = false
        val position = cards.firstVisiblePosition
        val firstId = shown.getOrNull(position)?.id
        val top = cards.getChildAt(0)?.top ?: 0
        val now = System.currentTimeMillis()
        val query = search.text.toString().trim()
        val sameView = query == previousQuery && filter == previousFilter
        previousQuery = query; previousFilter = filter
        shown = entries.filter { ClipboardRetention.alive(it, now) &&
            (filter == "Verlauf" || filter == "Pins" && it.pinned || filter == "Gespeichert" && it.saved) &&
            (query.isEmpty() || it.text.contains(query, true)) }
        title.text = "Zwischenablage · " + shown.size
        empty.text = if (entries.isEmpty()) "Kopiere Text oder füge eine markierte Auswahl hinzu." else "Keine Einträge in dieser Ansicht."
        for (index in 0 until filters.childCount) {
            val view = filters.getChildAt(index) as Button
            view.setTextColor(if (view.text.toString() == filter) theme.accent else theme.textSecondary)
        }
        adapter.notifyDataSetChanged()
        val retainedPosition = if (sameView) shown.indexOfFirst { it.id == firstId }.takeIf { it >= 0 } ?: position else 0
        cards.setSelectionFromTop(retainedPosition.coerceAtMost((shown.size - 1).coerceAtLeast(0)), if (sameView) top else 0)
        val next = entries.filter { !it.pinned && !it.saved }.mapNotNull { it.expiresAtMs }.filter { it > now }.minOrNull()
        if (isAttachedToWindow && next != null) postDelayed(expiryRefresh, (next - now + 1).coerceAtMost(60_000))
    }
    private fun finishEditing() { editing = false; saving = false; saveGeneration++; enableForm(true); form.visibility = GONE; search.visibility = GONE; onEditor(null); render() }
    private fun edit(entry: ClipboardEntry?) {
        saveGeneration++; saving = false; editing = true; form.removeAllViews(); form.isEnabled = true
        form.visibility = VISIBLE; cards.visibility = GONE; empty.visibility = GONE; search.visibility = GONE
        val editor = EditText(context).apply {
            setText(entry?.text.orEmpty()); setTextColor(theme.textPrimary); textSize = 15f
            hint = "Textbaustein"; setHintTextColor(theme.textSecondary); showSoftInputOnFocus = false
        }
        form.addView(editor, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)))
        val row = LinearLayout(context)
        row.addView(button("Speichern") {
            val value = editor.text.toString()
            if (value.isBlank()) showStatus("Bitte Text eingeben.")
            else { saveGeneration++; saving = true; enableForm(false); actions.onClipboardSave(entry?.id, value) }
        }, LayoutParams(0, dp(32), 1f))
        row.addView(button("Abbrechen") { finishEditing() }, LayoutParams(0, dp(32), 1f))
        form.addView(row); StylusUi.usePointerInput(form); editor.requestFocus(); onEditor(editor)
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) touching = true
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            post { touching = false; if (pendingRender && !scrolling) render() }
        }
        return handled
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!editing) render() }
    override fun onDetachedFromWindow() { removeCallbacks(expiryRefresh); touching = false; scrolling = false; super.onDetachedFromWindow() }
}
