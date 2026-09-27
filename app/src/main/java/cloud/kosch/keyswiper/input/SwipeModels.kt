package cloud.kosch.keyswiper.input

data class SwipePoint(
    val x: Float,
    val y: Float,
    val timeMs: Long
)

data class SwipeTrace(
    val points: List<SwipePoint>,
    val touchedKeys: List<Char>
) {
    val durationMs: Long
        get() = if (points.size < 2) 0L else (points.last().timeMs - points.first().timeMs).coerceAtLeast(0L)

    fun normalizedKeys(): List<Char> =
        touchedKeys.map { it.lowercaseChar() }.fold(mutableListOf()) { acc, c ->
            if (acc.lastOrNull() != c) acc.add(c)
            acc
        }
}

data class KeyOffset(
    val dx: Float = 0f,
    val dy: Float = 0f
)
