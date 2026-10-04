package cloud.kosch.keyswiper.input

import cloud.kosch.keyswiper.ui.KeyboardLayoutProfiles
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object KeyboardGeometry {

    fun center(
        character: Char,
        layoutId: String = "en-qwerty",
        offset: KeyOffset = KeyOffset()
    ): Pair<Float, Float>? {
        val profile = KeyboardLayoutProfiles.byId(layoutId)
        val c = character.lowercaseChar()

        KeyboardLayoutProfiles.slots(profile).forEachIndexed { rowIndex, row ->
            val index = row.indexOfFirst { it.token == c.toString() }
            if (index >= 0) {
                val total = row.sumOf { it.weight.toDouble() }.toFloat()
                val left = row.take(index).sumOf { it.weight.toDouble() }.toFloat()
                return Pair(
                    ((left + row[index].weight / 2f) / total + offset.dx).coerceIn(0f, 1f),
                    ((rowIndex + 0.5f) / 3f + offset.dy).coerceIn(0f, 1f)
                )
            }
        }

        return null
    }
}

object SwipeGeometryScorer {

    class PreparedTrace internal constructor(
        internal val trace: SwipeTrace,
        internal val observed: List<Pair<Float, Float>>
    )

    fun prepare(trace: SwipeTrace): PreparedTrace =
        PreparedTrace(trace, resample(trace.points.map { Pair(it.x, it.y) }, 32))

    fun endpointDistance(
        trace: SwipeTrace,
        word: String,
        offsetFor: (Char) -> KeyOffset = { KeyOffset() }
    ): Float {
        if (trace.points.isEmpty() || word.isEmpty()) return 0f
        val first = KeyboardGeometry.center(word.first(), trace.layoutId, offsetFor(word.first()))
            ?: return 2f
        val last = KeyboardGeometry.center(word.last(), trace.layoutId, offsetFor(word.last()))
            ?: return 2f
        val start = trace.points.first()
        val end = trace.points.last()
        return hypot(start.x - first.first, start.y - first.second) +
            hypot(end.x - last.first, end.y - last.second)
    }

    fun score(
        trace: SwipeTrace,
        word: String,
        offsetFor: (Char) -> KeyOffset = { KeyOffset() }
    ): Float = score(prepare(trace), word, offsetFor)

    fun score(
        prepared: PreparedTrace,
        word: String,
        offsetFor: (Char) -> KeyOffset = { KeyOffset() }
    ): Float {
        val trace = prepared.trace
        if (trace.points.size < 2 || word.isBlank()) return 0f
        val ideal = word.mapNotNull { c ->
            KeyboardGeometry.center(c, trace.layoutId, offsetFor(c))
        }
        if (ideal.size < 2) return 0f
        val observed = prepared.observed
        val idealResampled = resample(ideal, 32)
        val idealLength = pathLength(ideal)
        val lengthPenalty = abs(pathLength(observed) - idealLength) /
            idealLength.coerceAtLeast(0.5f)
        return dtwDistance(observed, idealResampled) * 160f +
            endpointDistance(trace, word, offsetFor) * 80f +
            lengthPenalty.coerceAtMost(3f) * 10f +
            directionPenalty(observed, idealResampled) * 14f +
            velocityCornerPenalty(trace, ideal) * 10f
    }

    fun estimateLetterOffsets(
        trace: SwipeTrace,
        word: String
    ): Map<Char, KeyOffset> {
        if (trace.points.size < 2 || word.isBlank()) return emptyMap()

        val sampled = resample(
            trace.points.map { Pair(it.x, it.y) },
            word.length.coerceAtLeast(2)
        )
        val sums = mutableMapOf<Char, Pair<Float, Float>>()
        val counts = mutableMapOf<Char, Int>()

        word.lowercase().forEachIndexed { index, c ->
            val center = KeyboardGeometry.center(
                character = c,
                layoutId = trace.layoutId
            ) ?: return@forEachIndexed

            val point = sampled[index.coerceAtMost(sampled.lastIndex)]

            val dx = (point.first - center.first).coerceIn(-0.10f, 0.10f)
            val dy = (point.second - center.second).coerceIn(-0.10f, 0.10f)
            val previous = sums[c] ?: Pair(0f, 0f)

            sums[c] = Pair(previous.first + dx, previous.second + dy)
            counts[c] = (counts[c] ?: 0) + 1
        }

        return sums.mapValues { (c, sum) ->
            val n = (counts[c] ?: 1).toFloat()
            KeyOffset(sum.first / n, sum.second / n)
        }
    }

    private fun velocityCornerPenalty(
        trace: SwipeTrace,
        ideal: List<Pair<Float, Float>>
    ): Float {
        if (trace.points.size < 3) return 0f

        val speeds = MutableList(trace.points.size) { 0f }
        for (i in 1 until trace.points.size) {
            val a = trace.points[i - 1]
            val b = trace.points[i]
            val dt = max(1L, b.timeMs - a.timeMs).toFloat()
            speeds[i] = hypot(b.x - a.x, b.y - a.y) / dt
        }

        val positive = speeds.filter { it > 0f }
        val median = if (positive.isEmpty()) {
            0f
        } else {
            positive.sorted()[positive.size / 2]
        }
        if (median <= 0f) return 0f

        var total = 0f
        for (target in ideal) {
            var bestIndex = 0
            var bestDistance = Float.MAX_VALUE

            trace.points.forEachIndexed { index, point ->
                val distance = hypot(
                    point.x - target.first,
                    point.y - target.second
                )
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }

            val speedRatio =
                (speeds[bestIndex] / median).coerceIn(0f, 3f)
            val dwellFactor =
                0.65f + 0.35f * speedRatio
            total += bestDistance * dwellFactor
        }

        return total / ideal.size.coerceAtLeast(1)
    }

    private fun directionPenalty(
        a: List<Pair<Float, Float>>,
        b: List<Pair<Float, Float>>
    ): Float {
        if (a.size < 3 || b.size < 3) return 0f
        val count = min(a.size, b.size)
        var total = 0f
        var samples = 0

        for (i in 1 until count - 1) {
            val avx = a[i + 1].first - a[i - 1].first
            val avy = a[i + 1].second - a[i - 1].second
            val bvx = b[i + 1].first - b[i - 1].first
            val bvy = b[i + 1].second - b[i - 1].second

            val amag = hypot(avx, avy)
            val bmag = hypot(bvx, bvy)
            if (amag <= 0.0001f || bmag <= 0.0001f) continue

            val dot = (
                (avx * bvx + avy * bvy) /
                    (amag * bmag)
                ).coerceIn(-1f, 1f)

            total += (acos(dot) / PI).toFloat()
            samples++
        }

        return if (samples == 0) {
            0f
        } else {
            total / samples
        }
    }

    private fun dtwDistance(
        a: List<Pair<Float, Float>>,
        b: List<Pair<Float, Float>>
    ): Float {
        val rows = a.size + 1
        val cols = b.size + 1
        val matrix = Array(rows) {
            FloatArray(cols) { Float.POSITIVE_INFINITY }
        }
        matrix[0][0] = 0f

        for (i in 1 until rows) {
            for (j in 1 until cols) {
                val cost = hypot(
                    a[i - 1].first - b[j - 1].first,
                    a[i - 1].second - b[j - 1].second
                )
                matrix[i][j] = cost + min(
                    matrix[i - 1][j],
                    min(
                        matrix[i][j - 1],
                        matrix[i - 1][j - 1]
                    )
                )
            }
        }

        return matrix[a.size][b.size] /
            max(a.size, b.size).coerceAtLeast(1)
    }

    private fun pathLength(
        points: List<Pair<Float, Float>>
    ): Float {
        if (points.size < 2) return 0f

        var length = 0f
        for (i in 1 until points.size) {
            length += hypot(
                points[i].first - points[i - 1].first,
                points[i].second - points[i - 1].second
            )
        }
        return length
    }

    private fun resample(
        points: List<Pair<Float, Float>>,
        count: Int
    ): List<Pair<Float, Float>> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1 || count <= 1) {
            return List(max(1, count)) { points.first() }
        }

        val cumulative = FloatArray(points.size)
        for (i in 1 until points.size) {
            cumulative[i] =
                cumulative[i - 1] +
                    hypot(
                        points[i].first - points[i - 1].first,
                        points[i].second - points[i - 1].second
                    )
        }

        val total = cumulative.last()
        if (total <= 0.0001f) {
            return List(count) { points.first() }
        }

        return List(count) { sampleIndex ->
            val target =
                total *
                    sampleIndex /
                    (count - 1).coerceAtLeast(1)

            var segment = 1
            while (
                segment < cumulative.size &&
                cumulative[segment] < target
            ) {
                segment++
            }

            if (segment >= cumulative.size) {
                points.last()
            } else {
                val previousDistance =
                    cumulative[segment - 1]
                val segmentDistance =
                    cumulative[segment] -
                        previousDistance

                val ratio = if (segmentDistance <= 0f) {
                    0f
                } else {
                    (target - previousDistance) /
                        segmentDistance
                }

                val a = points[segment - 1]
                val b = points[segment]

                Pair(
                    a.first +
                        (b.first - a.first) * ratio,
                    a.second +
                        (b.second - a.second) * ratio
                )
            }
        }
    }
}

