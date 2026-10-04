package cloud.kosch.keyswiper.handwriting

object StylusTapClassifier {
    fun isTap(points: List<StylusScreenPoint>, density: Float): Boolean {
        if (points.isEmpty()) return false
        val duration = points.last().timeMs - points.first().timeMs
        return duration in 0..550 &&
            points.maxOf { it.x } - points.minOf { it.x } <= 8f * density &&
            points.maxOf { it.y } - points.minOf { it.y } <= 8f * density
    }
}
