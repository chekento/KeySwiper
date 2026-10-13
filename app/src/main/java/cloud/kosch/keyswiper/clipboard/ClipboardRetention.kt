package cloud.kosch.keyswiper.clipboard

object ClipboardRetention {
    const val MAX_MINUTES = 43_200L
    fun alive(entry: ClipboardEntry, now: Long): Boolean =
        entry.pinned || entry.saved || (entry.expiresAtMs ?: Long.MIN_VALUE) > now
    fun expiresAt(now: Long, minutes: Long): Long = now + minutes.coerceIn(10, MAX_MINUTES) * 60_000L
}
data class ClipboardSnapshot(val entries: List<ClipboardEntry> = emptyList(),
    val lastCapturedId: String? = null, val lastClipTimestamp: Long = 0L)
