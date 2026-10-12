package cloud.kosch.keyswiper.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.util.Base64
import java.security.MessageDigest
import java.util.concurrent.Executor

enum class ClipboardCategory(val label: String) {
    LINK("Links"), EMAIL("E-Mail"), PHONE("Telefon"), ADDRESS("Adressen"), CODE("Code"), TEXT("Text")
}
data class ClipboardEntry(val id: String, val text: String, val category: ClipboardCategory,
    val createdAtMs: Long, val expiresAtMs: Long?, val pinned: Boolean, val saved: Boolean = false)

/** Service operations and persistence run on its dedicated clipboard executor. */
class ClipboardController(private val context: Context,
    private val store: EncryptedClipboardStore = EncryptedClipboardStore(context),
    private val clock: () -> Long = System::currentTimeMillis,
    private val eventExecutor: Executor = Executor { it.run() },
    private val onChanged: () -> Unit = {}, private val onStorageError: () -> Unit = {}) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val preferences = context.getSharedPreferences("keyswiper_clipboard", Context.MODE_PRIVATE)
    private val history = mutableListOf<ClipboardEntry>()
    private var removed = emptyList<ClipboardEntry>()
    private var defaultExpiryMinutes = 1440L
    private var started = false
    private var lastCapturedId: String? = null
    private var lastClipTimestamp = 0L
    private var durable = ClipboardSnapshot()
    @Volatile private var captureAllowed = true
    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        runCatching { eventExecutor.execute { runCatching { capturePrimary(newCopy = true) }
            .onSuccess { if (it) onChanged() }.onFailure { onStorageError() } } }
    }
    init {
        val snapshot = store.read()
        history.addAll(snapshot.entries); lastCapturedId = snapshot.lastCapturedId; lastClipTimestamp = snapshot.lastClipTimestamp
        durable = snapshot
        if (!store.exists) {
            val legacy = preferences.getStringSet("pinned_entries", emptySet()).orEmpty().mapNotNull(::decodeLegacy)
            if (legacy.isNotEmpty()) {
                history.addAll(legacy); persist()
                check(preferences.edit().remove("pinned_entries").commit()) { "Legacy clipboard cleanup failed" }
            }
        }
        purge()
    }
    fun setCaptureAllowed(allowed: Boolean) { captureAllowed = allowed }
    @Synchronized fun start() {
        if (!started) { clipboard.addPrimaryClipChangedListener(listener); started = true }
        capturePrimary()
    }
    @Synchronized fun stop() { clipboard.removePrimaryClipChangedListener(listener); started = false; captureAllowed = false }
    @Synchronized fun setDefaultExpiryMinutes(minutes: Long) { defaultExpiryMinutes = minutes.coerceIn(10, ClipboardRetention.MAX_MINUTES) }
    @Synchronized fun items(query: String = ""): List<ClipboardEntry> {
        capturePrimary(); purge()
        return history.filter { query.isBlank() || it.text.contains(query.trim(), true) || it.category.label.contains(query.trim(), true) }
            .sortedWith(compareByDescending<ClipboardEntry> { it.pinned }.thenByDescending { it.createdAtMs })
    }
    @Synchronized fun textFor(id: String): String? { purge(); return history.firstOrNull { it.id == id }?.text }
    @Synchronized fun togglePin(id: String): Boolean {
        purge()
        val index = history.indexOfFirst { it.id == id }; if (index < 0) return false
        val old = history[index]; val pinned = !old.pinned
        history[index] = old.copy(pinned = pinned, expiresAtMs = if (pinned || old.saved) null else expiry())
        persist(); return true
    }
    @Synchronized fun toggleSaved(id: String): Boolean {
        purge()
        val index = history.indexOfFirst { it.id == id }; if (index < 0) return false
        val old = history[index]; val saved = !old.saved
        history[index] = old.copy(saved = saved, expiresAtMs = if (saved || old.pinned) null else expiry())
        persist(); return true
    }
    @Synchronized fun add(value: String, saved: Boolean = false): Boolean {
        purge()
        if (!put(value, saved)) return false
        persist(); return true
    }
    private fun put(value: String, saved: Boolean = false, createdAt: Long = clock()): Boolean {
        if (value.isBlank()) return false
        require(value.length <= MAX_ENTRY_CHARS) { "Der Text ist zu lang. Bitte in kleinere Textbausteine aufteilen." }
        val id = stableId(value); val old = history.firstOrNull { it.id == id }
        require(old != null || history.size < MAX_ENTRIES) { "Zwischenablage voll. Bitte Einträge löschen; bestehende Texte bleiben erhalten." }
        val permanent = saved || old?.saved == true; val pinned = old?.pinned == true
        history.removeAll { it.id == id }
        history.add(0, ClipboardEntry(id, value, ClipboardTextClassifier.category(value), createdAt,
            if (permanent || pinned) null else ClipboardRetention.expiresAt(createdAt, defaultExpiryMinutes), pinned, permanent))
        return true
    }
    @Synchronized fun delete(id: String): Boolean = deleteMany(setOf(id)) > 0
    @Synchronized fun deleteMany(ids: Set<String>): Int {
        removed = history.filter { it.id in ids }; history.removeAll { it.id in ids }
        persist(); return removed.size
    }
    @Synchronized fun clearUnpinned() { deleteMany(history.filter { !it.pinned && !it.saved }.map { it.id }.toSet()) }
    @Synchronized fun undoDelete(): Boolean {
        val recoverable = removed.filter { ClipboardRetention.alive(it, clock()) }
        if (recoverable.isEmpty()) { removed = emptyList(); return false }
        recoverable.forEach { entry -> history.removeAll { it.id == entry.id }; history.add(entry) }
        removed = emptyList(); persist(); return true
    }
    @Synchronized fun copy(id: String) { textFor(id)?.let(::systemCopy) }
    @Synchronized fun copySelection(value: String): Boolean {
        if (!captureAllowed || value.isBlank()) return false
        if (!add(value)) return false
        systemCopy(value)
        return true
    }
    @Synchronized fun save(id: String?, value: String) {
        if (value.isBlank()) return
        val old = history.firstOrNull { it.id == id }
        purge()
        if (!put(value, saved = true)) return
        val nextId = stableId(value)
        if (id != null && id != nextId) history.removeAll { it.id == id }
        if (old?.pinned == true) {
            val index = history.indexOfFirst { it.id == nextId }
            history[index] = history[index].copy(pinned = true)
        }
        persist()
    }
    private fun expiry() = ClipboardRetention.expiresAt(clock(), defaultExpiryMinutes)
    private fun systemCopy(value: String) {
        val clip = ClipData.newPlainText("KeySwiper", value)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        clipboard.setPrimaryClip(clip)
    }
    @Synchronized private fun capturePrimary(newCopy: Boolean = false): Boolean {
        purge()
        if (!captureAllowed) return false
        val clip = try { clipboard.primaryClip } catch (_: SecurityException) { null } ?: return false
        if (clip.itemCount == 0 || clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return false
        val item = clip.getItemAt(0)
        val text = (item.text?.toString() ?: item.uri?.takeIf { it.scheme in setOf("http", "https") }?.toString()).orEmpty()
        if (text.isBlank() || text.length > MAX_ENTRY_CHARS) return false
        val id = stableId(text)
        val timestamp = if (Build.VERSION.SDK_INT >= 26) clip.description.timestamp else 0L
        // Persisted copy identity prevents deleted/expired content returning on restart.
        if (!newCopy && id == lastCapturedId && timestamp <= lastClipTimestamp) return false
        val copiedAt = if (timestamp > 0 && timestamp <= clock()) timestamp else clock()
        val permanent = history.firstOrNull { it.id == id }?.let { it.pinned || it.saved } == true
        if (!permanent && ClipboardRetention.expiresAt(copiedAt, defaultExpiryMinutes) <= clock()) {
            lastCapturedId = id; lastClipTimestamp = timestamp; persist()
            return false
        }
        put(text, createdAt = copiedAt)
        lastCapturedId = id; lastClipTimestamp = timestamp; persist()
        return true
    }
    @Synchronized private fun purge() { if (history.removeAll { !ClipboardRetention.alive(it, clock()) }) persist() }
    private fun stableId(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .take(10).joinToString("") { "%02x".format(it) }
    private fun persist() {
        val next = ClipboardSnapshot(history.toList(), lastCapturedId, lastClipTimestamp)
        try { store.write(next); durable = next }
        catch (error: Exception) {
            history.clear(); history.addAll(durable.entries)
            lastCapturedId = durable.lastCapturedId; lastClipTimestamp = durable.lastClipTimestamp
            throw error
        }
    }
    private fun decodeLegacy(raw: String): ClipboardEntry? = runCatching {
        val p = raw.split("|", limit = 4); require(p.size == 4)
        ClipboardEntry(p[0], String(Base64.decode(p[3], Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8),
            ClipboardCategory.valueOf(p[2]), p[1].toLong(), null, true, true)
    }.getOrNull()
    companion object { private const val MAX_ENTRY_CHARS = 12_000; private const val MAX_ENTRIES = 2000 }
}
