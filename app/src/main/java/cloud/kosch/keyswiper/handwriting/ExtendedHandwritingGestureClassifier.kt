package cloud.kosch.keyswiper.handwriting

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.hypot

sealed interface ExtendedHandwritingGesture {
    data class SelectArea(
        val bounds: RectF
    ) : ExtendedHandwritingGesture

    data class JoinOrSplit(
        val point: PointF
    ) : ExtendedHandwritingGesture

    data class RemoveSpace(
        val start: PointF,
        val end: PointF
    ) : ExtendedHandwritingGesture
}

object ExtendedHandwritingGestureClassifier {

    fun classify(
        points: List<StylusScreenPoint>,
        density: Float
    ): ExtendedHandwritingGesture? {
        if (
            points.size < 4
        ) {
            return null
        }

        val minX =
            points.minOf { it.x }
        val maxX =
            points.maxOf { it.x }
        val minY =
            points.minOf { it.y }
        val maxY =
            points.maxOf { it.y }

        val width =
            maxX - minX
        val height =
            maxY - minY

        var pathLength = 0f
        for (
            index in 1 until
                points.size
        ) {
            pathLength +=
                hypot(
                    points[index].x -
                        points[index - 1].x,
                    points[index].y -
                        points[index - 1].y
                )
        }

        val start =
            points.first()
        val end =
            points.last()

        val endpointDistance =
            hypot(
                end.x - start.x,
                end.y - start.y
            )

        val minCircleSize =
            38f * density

        if (
            width >= minCircleSize &&
            height >= minCircleSize &&
            endpointDistance <=
                maxOf(
                    width,
                    height
                ) * 0.42f &&
            pathLength >=
                (
                    width +
                        height
                    ) * 1.55f
        ) {
            val padding =
                7f * density

            return ExtendedHandwritingGesture
                .SelectArea(
                    RectF(
                        minX - padding,
                        minY - padding,
                        maxX + padding,
                        maxY + padding
                    )
                )
        }

        val minRemoveWidth =
            96f * density

        if (
            width >= minRemoveWidth &&
            height <=
                18f * density &&
            pathLength <=
                width * 1.28f
        ) {
            return ExtendedHandwritingGesture
                .RemoveSpace(
                    start =
                        PointF(
                            start.x,
                            start.y
                        ),
                    end =
                        PointF(
                            end.x,
                            end.y
                        )
                )
        }

        val minVertical =
            62f * density

        if (
            height >= minVertical &&
            width <=
                16f * density &&
            pathLength <=
                height * 1.30f
        ) {
            return ExtendedHandwritingGesture
                .JoinOrSplit(
                    PointF(
                        (minX + maxX) /
                            2f,
                        (minY + maxY) /
                            2f
                    )
                )
        }

        return null
    }
}
