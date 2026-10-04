package cloud.kosch.keyswiper.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.View
import android.widget.*
import cloud.kosch.keyswiper.clipboard.ClipboardCategory
import cloud.kosch.keyswiper.clipboard.ClipboardEntry

class ClipboardPanelView(context: Context, private val theme: KeyboardThemeProfile,
    private val actions: KeyboardRootView.Callbacks, private val onEditor: (EditText?) -> Unit,
    private val onClose: () -> Unit) : LinearLayout(context) {
    private var entries = emptyList<ClipboardEntry>()
    private var filter = "Alle"
    private var selecting = false
    private val selected = mutableSetOf<String>()
    private val list = LinearLayout(context).apply { orientation = VERTICAL }
    private val search = EditText(context)
    val searchEditor: EditText get() = search
    private val count = TextView(context)
    private val selectionButton: Button
    private var expiry = 60L
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun background(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(10).toFloat(); setStroke(dp(1), theme.border) }
    private fun button(label: String, action: () -> Unit) = Button(context).apply {
        text = label; isAllCaps = false; textSize = 12f; setTextColor(theme.textPrimary)
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(7), 0, dp(7), 0); background = background(theme.surfaceRaised)
        setOnClickListener { action() }
    }
    private fun row() = LinearLayout(context).apply { orientation = HORIZONTAL }
    private fun LinearLayout.addAction(view: View, weight: Float = 1f) {
        addView(view, LayoutParams(0, dp(36), weight).apply { marginEnd = dp(3) })
    }
    init {
        orientation = VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(6)); setBackgroundColor(theme.background)
        val header = row()
        count.setTextColor(theme.accent); count.textSize = 15f
        header.addView(count, LayoutParams(0, dp(36), 2f))
        header.addAction(button("＋ Neu") { edit(null) })
        header.addAction(button("↶") { actions.onClipboardUndoDelete() }.apply { contentDescription = "Löschen rückgängig" })
        val more = button("⋯") {}
        more.setOnClickListener {
            PopupMenu(context, more).apply {
                menu.add("Nicht angeheftete löschen").setOnMenuItemClickListener { actions.onClipboardClearUnpinned(); true }
                listOf(10L to "10 Minuten", 60L to "1 Stunde", 1440L to "1 Tag").forEach { (minutes, label) ->
                    menu.add("${if (expiry == minutes) "✓ " else ""}Aufbewahren: $label")
                        .setOnMenuItemClickListener { actions.onClipboardExpiryChanged(minutes); true }
                }
                show()
            }
        }
        header.addAction(more)
        header.addAction(button("×", onClose).apply { contentDescription = "Zwischenablage schließen" })
        addView(header)
        val searchRow = row()
        search.apply {
            hint = "Zwischenablage durchsuchen"; textSize = 14f; setTextColor(theme.textPrimary); setHintTextColor(theme.textSecondary)
            setSingleLine(); showSoftInputOnFocus = false
            setOnFocusChangeListener { _, focused -> if (focused) onEditor(this) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render() }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        searchRow.addView(search, LayoutParams(0, dp(38), 3f))
        selectionButton = button("Auswahl") {
            if (selecting && selected.isNotEmpty()) {
                val ids = selected.toSet(); selected.clear(); selecting = false
                actions.onClipboardDeleteMany(ids)
            } else { selecting = !selecting; selected.clear(); render() }
        }
        searchRow.addAction(selectionButton, 1.1f); addView(searchRow)
        val tabs = row()
        (listOf("Alle", "Merkliste") + ClipboardCategory.entries.map { it.label }).forEach { label ->
            tabs.addView(button(label) { filter = label; render() }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(32)).apply { marginEnd = dp(4) })
        }
        addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(tabs) })
        addView(ScrollView(context).apply { addView(list); isFillViewport = true }, LayoutParams(LayoutParams.MATCH_PARENT, dp(150)))
        StylusUi.usePointerInput(this)
    }
    fun update(items: List<ClipboardEntry>, minutes: Long) {
        entries = items; expiry = minutes
        selected.retainAll(items.map { it.id }.toSet())
        render()
    }
    private fun render() {
        list.removeAllViews()
        val query = search.text.toString().trim()
        val shown = entries.filter { (filter == "Alle" || filter == "Merkliste" && it.pinned || it.category.label == filter) &&
            (query.isEmpty() || it.text.contains(query, true) || it.category.label.contains(query, true)) }
        count.text = "Zwischenablage · ${shown.size}/${entries.size}"
        selectionButton.text = if (!selecting) "Auswahl" else if (selected.isEmpty()) "Abbrechen" else "${selected.size} löschen"
        if (shown.isEmpty()) list.addView(TextView(context).apply {
            text = if (entries.isEmpty()) "Kopierte Texte erscheinen hier. Mit ＋ Neu kannst du Textbausteine anlegen." else "Keine passenden Einträge."
            textSize = 14f; setTextColor(theme.textSecondary); setPadding(dp(6), dp(18), dp(6), dp(18))
        })
        shown.forEach { entry ->
            val card = LinearLayout(context).apply {
                orientation = VERTICAL; setPadding(dp(9), dp(6), dp(9), dp(6))
                background = background(if (entry.id in selected) theme.keyPressed else theme.surface)
            }
            card.addView(TextView(context).apply {
                text = "${if (entry.pinned) "📌 " else ""}${entry.category.label} · ${DateUtils.getRelativeTimeSpanString(entry.createdAtMs)}"
                textSize = 11f; setTextColor(theme.textSecondary)
            })
            card.addView(TextView(context).apply {
                text = entry.text; maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 15f; setTextColor(theme.textPrimary)
                setOnClickListener { if (selecting) { if (!selected.add(entry.id)) selected.remove(entry.id); render() }
                    else { actions.onClipboardInsert(entry.id); onClose() } }
            })
            val buttons = row()
            if (selecting) buttons.addAction(button(if (entry.id in selected) "✓ Ausgewählt" else "Auswählen") {
                if (!selected.add(entry.id)) selected.remove(entry.id); render()
            }) else {
                buttons.addAction(button("Einfügen") { actions.onClipboardInsert(entry.id); onClose() })
                buttons.addAction(button(if (entry.pinned) "Lösen" else "Merken") { actions.onClipboardTogglePin(entry.id) })
                val options = button("⋯") {}
                options.setOnClickListener {
                    PopupMenu(context, options).apply {
                        menu.add("Ansehen / Bearbeiten").setOnMenuItemClickListener { edit(entry); true }
                        menu.add("Kopieren").setOnMenuItemClickListener { actions.onClipboardCopy(entry.id); true }
                        menu.add("Löschen").setOnMenuItemClickListener { actions.onClipboardDelete(entry.id); true }
                        show()
                    }
                }
                buttons.addAction(options, 0.6f)
            }
            card.addView(buttons)
            list.addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(5) })
        }
        StylusUi.usePointerInput(list)
    }
    private fun edit(entry: ClipboardEntry?) {
        list.removeAllViews()
        val editor = EditText(context).apply {
            setText(entry?.text.orEmpty()); setTextColor(theme.textPrimary); textSize = 15f
            hint = "Textbaustein"; setHintTextColor(theme.textSecondary); showSoftInputOnFocus = false
            setOnFocusChangeListener { _, focused -> if (focused) onEditor(this) }
        }
        list.addView(editor, LayoutParams(LayoutParams.MATCH_PARENT, dp(100)))
        val actionsRow = row()
        actionsRow.addAction(button("Speichern") {
            val value = editor.text.toString(); onEditor(null)
            actions.onClipboardSave(entry?.id, value); render()
        })
        actionsRow.addAction(button("Abbrechen") { onEditor(null); render() })
        list.addView(actionsRow); StylusUi.usePointerInput(list); editor.requestFocus(); onEditor(editor)
    }
}
