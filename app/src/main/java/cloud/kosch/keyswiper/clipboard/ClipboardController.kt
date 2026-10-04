package cloud.kosch.keyswiper.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import java.security.MessageDigest

enum class ClipboardCategory(val label: String) {
    LINK("Links"), EMAIL("E-Mail"), PHONE("Telefon"), ADDRESS("Adressen"), CODE("Code"), TEXT("Text")
}
data class ClipboardEntry(val id: String, val text: String, val category: ClipboardCategory,
    val createdAtMs: Long, val expiresAtMs: Long?, val pinned: Boolean)

class ClipboardController(private val context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val preferences = context.getSharedPreferences("keyswiper_clipboard", Context.MODE_PRIVATE)
    private val history = mutableListOf<ClipboardEntry>()
    private var removed = emptyList<ClipboardEntry>()
    private var defaultExpiryMinutes = 60L
    private var started = false
    private var lastCapturedId: String? = null
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { capturePrimary(newCopy = true) }
    init { preferences.getStringSet("pinned_entries", emptySet()).orEmpty().mapNotNull(::decode).forEach(history::add) }

    fun start() {
        if (!started) { clipboard.addPrimaryClipChangedListener(listener); started = true }
        capturePrimary()
    }
    fun stop() { clipboard.removePrimaryClipChangedListener(listener); started = false }
    fun setDefaultExpiryMinutes(minutes: Long) { defaultExpiryMinutes = minutes.coerceIn(10, 1440) }
    fun items(query: String = ""): List<ClipboardEntry> {
        capturePrimary(); purge()
        return history.filter { query.isBlank() || it.text.contains(query.trim(), true) || it.category.label.contains(query.trim(), true) }
            .sortedWith(compareByDescending<ClipboardEntry> { it.pinned }.thenByDescending { it.createdAtMs })
    }
    fun textFor(id: String): String? { purge(); return history.firstOrNull { it.id == id }?.text }
    fun togglePin(id: String): Boolean {
        purge()
        val index = history.indexOfFirst { it.id == id }
        if (index < 0) return false
        val old = history[index]
        history[index] = old.copy(pinned = !old.pinned, expiresAtMs = if (old.pinned) expiry() else null)
        persist(); return true
    }
    fun delete(id: String): Boolean = deleteMany(setOf(id)) > 0
    fun deleteMany(ids: Set<String>): Int {
        removed = history.filter { it.id in ids }
        history.removeAll { it.id in ids }
        persist(); return removed.size
    }
    fun clearUnpinned() { deleteMany(history.filter { !it.pinned }.map { it.id }.toSet()) }
    fun undoDelete(): Boolean {
        if (removed.isEmpty()) return false
        removed.forEach { entry ->
            history.removeAll { it.id == entry.id }
            history.add(entry.copy(expiresAtMs = if (entry.pinned) null else expiry()))
        }
        removed = emptyList(); trim(); persist(); return true
    }
    fun copy(id: String) { textFor(id)?.let { clipboard.setPrimaryClip(ClipData.newPlainText("KeySwiper", it)) } }
    fun save(id: String?, value: String) {
        if (value.isBlank()) return
        val text = value.take(MAX_ENTRY_CHARS)
        val old = history.firstOrNull { it.id == id }
        val next = entry(text, old?.pinned ?: true)
        history.removeAll { it.id == id || it.id == next.id }
        history.add(0, next); trim(); persist()
    }
    private fun expiry() = System.currentTimeMillis() + defaultExpiryMinutes * 60_000L
    private fun entry(text: String, pinned: Boolean) = ClipboardEntry(stableId(text), text,
        ClipboardTextClassifier.category(text), System.currentTimeMillis(), if (pinned) null else expiry(), pinned)
    private fun capturePrimary(newCopy: Boolean = false) {
        purge()
        val clip = try { clipboard.primaryClip } catch (_: SecurityException) { null } ?: return
        if (clip.itemCount == 0 || clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString()?.take(MAX_ENTRY_CHARS).orEmpty()
        if (text.isBlank()) return
        val id = stableId(text)
        // Reading the same system clip is not a new copy. Deleted/expired cards
        // must stay gone and merely opening the panel must not renew expiry.
        if (!newCopy && id == lastCapturedId) return
        lastCapturedId = id
        val pinned = history.firstOrNull { it.id == id }?.pinned ?: false
        history.removeAll { it.id == id }
        history.add(0, entry(text, pinned)); trim()
        if (pinned) persist()
    }
    private fun purge() { val now = System.currentTimeMillis(); history.removeAll { !it.pinned && (it.expiresAtMs ?: Long.MAX_VALUE) <= now } }
    private fun trim() {
        while (history.size > 80) {
            val index = history.indexOfLast { !it.pinned }
            if (index < 0) break
            history.removeAt(index)
        }
    }
    private fun stableId(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .take(10).joinToString("") { "%02x".format(it) }
    private fun persist() {
        preferences.edit().putStringSet("pinned_entries", history.filter { it.pinned }.map { entry ->
            listOf(entry.id, entry.createdAtMs.toString(), entry.category.name,
                Base64.encodeToString(entry.text.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)).joinToString("|")
        }.toSet()).apply()
    }
    private fun decode(raw: String): ClipboardEntry? = runCatching {
        val p = raw.split("|", limit = 4); require(p.size == 4)
        ClipboardEntry(p[0], String(Base64.decode(p[3], Base64.NO_WRAP or Base64.URL_SAFE)),
            ClipboardCategory.valueOf(p[2]), p[1].toLong(), null, true)
    }.getOrNull()
    companion object { private const val MAX_ENTRY_CHARS = 12_000 }
}
