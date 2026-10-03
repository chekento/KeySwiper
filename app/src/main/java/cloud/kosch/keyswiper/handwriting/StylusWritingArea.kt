package cloud.kosch.keyswiper.handwriting

data class StylusWritingArea(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x < right && y >= top && y < bottom
}

/** A stroke belongs to its starting area and cannot turn into a key press later. */
class StylusStrokeBoundary {
    private var area: StylusWritingArea? = null

    fun begin(x: Float, y: Float, writingArea: StylusWritingArea): Boolean {
        area = writingArea.takeIf { it.contains(x, y) }
        return area != null
    }

    fun continueAt(x: Float, y: Float): Boolean {
        if (area?.contains(x, y) != true) area = null
        return area != null
    }

    fun reset() {
        area = null
    }
}
