package cloud.kosch.keyswiper.voice

/** One dictation session may consist of multiple recognizer requests. */
internal class VoiceTranscriptBuffer(private val maxChars: Int = 12_000) {
    private val parts = mutableListOf<String>()
    val text: String get() = parts.joinToString(" ")
    val isEmpty: Boolean get() = parts.isEmpty()
    val isFull: Boolean get() = text.length >= maxChars

    fun add(segment: String): Boolean {
        val cleaned = segment.trim()
        if (cleaned.isBlank()) return false
        if (parts.lastOrNull() == cleaned) return false // repeated final callback
        if (text.length + cleaned.length + (if (parts.isEmpty()) 0 else 1) > maxChars) return false
        parts.add(cleaned)
        return true
    }
    fun preview(partial: String): String = listOf(text, partial.trim()).filter { it.isNotBlank() }.joinToString(" ")
}
