package cloud.kosch.keyswiper.clipboard

import android.content.ClipboardManager
import android.content.Context

class ClipboardController(context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val history = mutableListOf<String>()

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { capturePrimary() }

    fun start() {
        clipboard.addPrimaryClipChangedListener(listener)
        capturePrimary()
    }

    fun stop() {
        clipboard.removePrimaryClipChangedListener(listener)
    }

    fun items(): List<String> {
        capturePrimary()
        return history.toList()
    }

    private fun capturePrimary() {
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val value = clip.getItemAt(0).coerceToText(null)?.toString()?.trim().orEmpty()
        if (value.isBlank()) return
        history.remove(value)
        history.add(0, value)
        while (history.size > 12) history.removeAt(history.lastIndex)
    }
}
