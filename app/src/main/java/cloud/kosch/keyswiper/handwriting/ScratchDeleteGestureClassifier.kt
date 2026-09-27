package cloud.kosch.keyswiper.handwriting

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot

data class StylusScreenPoint(
    val x: Float,
    val y: Float,
    val timeMs: Long
)

data class ScratchDeleteGesture(
    val bounds: RectF,
    val reversals: Int
)

object ScratchDeleteGestureClassifier {

    fun classify(
        points: List<StylusScreenPoint>,
        density: Float
    ): ScratchDeleteGesture? {
        if (points.size < 8) return null

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }

        val width = maxX - minX
        val height = maxY - minY
        val minWidth = 72f * density

        if (width < minWidth) return null
        if (height > width * 0.72f) return null

        var pathLength = 0f
        var lastDirection = 0
        var reversals = 0

        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            pathLength += hypot(dx, dy)

            if (abs(dx) < 4f * density) continue

            val direction = if (dx > 0f) 1 else -1
            if (lastDirection != 0 && direction != lastDirection) {
                reversals++
            }
            lastDirection = direction
        }

        if (reversals < 3) return null
        if (pathLength < width * 2.15f) return null

        val horizontalPadding = 8f * density
        val verticalPadding = 14f * density

        return ScratchDeleteGesture(
            bounds = RectF(
                minX - horizontalPadding,
                minY - verticalPadding,
                maxX + horizontalPadding,
                maxY + verticalPadding
            ),
            reversals = reversals
        )
    }
}
