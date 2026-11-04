package com.romuel.pianosolfege.pdf

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A raster-only rhythm classifier. It operates on thresholded pixels so its
 * geometry can be tested without Android Bitmap mocks.
 *
 * Measures are expressed in QUARTER beats:
 * round = 4, half = 2, quarter = 1,
 * one beam/flag = 1/2, two = 1/4, three = 1/8.
 * A dot multiplies any value by 1.5.
 *
 * This does not claim to recognize all handwriting, tuplets or multiple voices.
 */
internal data class GlyphHead(
    val x: Int,
    val y: Int,
    val filled: Boolean,
)

internal data class GlyphReading(
    val beats: Float,
    val beamCount: Int,
    val flagCount: Int,
    val dotted: Boolean,
    val stemDetected: Boolean,
    val confidence: Float,
)

internal class RhythmGlyphReader(
    private val pixels: ByteArray,
    private val width: Int,
    private val height: Int,
    private val staffLines: IntArray,
    private val spacing: Float,
) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height)
        require(spacing > 0)
    }

    private data class Stem(
        val x: Int,
        val direction: Int,  // -1: up-stem, +1: down-stem
        val tipY: Int,
        val strength: Float,
    )

    fun recognize(heads: List<GlyphHead>): List<GlyphReading> {
        if (heads.isEmpty()) return emptyList()
        val stems = heads.map(::detectStem)
        val beamCounts = IntArray(heads.size)

        // Only successive heads with compatible stems may form a beam.
        // A chord may have multiple heads at nearly identical x: do not
        // count that vertical stack as a pair of beamed eighth notes.
        val ordered = heads.indices.sortedBy { heads[it].x }
        for (order in 0 until ordered.size - 1) {
            val i = ordered[order]
            val next = (order + 1 until min(ordered.size, order + 4))
                .map { ordered[it] }
                .firstOrNull { heads[it].x - heads[i].x > spacing * 1.25f }
                ?: continue
            val first = stems[i] ?: continue
            val second = stems[next] ?: continue
            if (first.direction != second.direction) continue
            val beams = countBeamsBetween(first, second, heads[i], heads[next])
            if (beams > 0) {
                beamCounts[i] = max(beamCounts[i], beams)
                beamCounts[next] = max(beamCounts[next], beams)
            }
        }

        return heads.indices.map { index ->
            val head = heads[index]
            val stem = stems[index]
            val beamCount = if (head.filled && stem != null) beamCounts[index] else 0
            val flagCount = if (head.filled && stem != null && beamCount == 0) {
                countFlags(stem, head)
            } else 0
            val dots = detectDot(head, heads)
            val duration = when {
                !head.filled && stem == null -> 4f
                !head.filled -> 2f
                stem == null -> 1f  // uncertainty; cannot infer flags without a stem
                else -> 1f / (1 shl (beamCount + flagCount).coerceIn(0, 3))
            }
            val confidence = when {
                head.filled && stem == null -> 0.3f
                beamCount > 0 -> 0.9f
                flagCount > 0 -> 0.72f
                else -> 0.68f
            }
            GlyphReading(
                beats = duration * if (dots) 1.5f else 1f,
                beamCount = beamCount,
                flagCount = flagCount,
                dotted = dots,
                stemDetected = stem != null,
                confidence = confidence,
            )
        }
    }

    private fun detectStem(head: GlyphHead): Stem? {
        val s = spacing
        val low = max(2, (s * 0.5f).roundToInt())
        val far = max(low + 4, (s * 3.0f).roundToInt())
        var best: Stem? = null

        for (direction in listOf(-1, 1)) {
            val xLow = max(2, (s * 0.34f).roundToInt())
            val xHigh = max(xLow + 1, (s * 1.00f).roundToInt())
            val xSign = if (direction < 0) 1 else -1

            for (dx in xLow..xHigh) {
                val x = head.x + xSign * dx
                if (x !in 1 until width - 1) continue
                var dark = 0
                var eligible = 0
                var contiguous = 0
                var longest = 0
                for (distance in low..far) {
                    val y = head.y + direction * distance
                    if (y !in 1 until height - 1 || onStaffLine(y)) continue
                    eligible += 1
                    if (isInk(x, y) || (isInk(x - 1, y) && isInk(x + 1, y))) {
                        dark += 1
                        contiguous += 1
                        longest = max(longest, contiguous)
                    } else {
                        contiguous = 0
                    }
                }
                if (eligible < max(5, (s * 1.5f).roundToInt())) continue
                val ratio = dark.toFloat() / eligible
                if (ratio < 0.72f || longest < max(3, (s * 0.75f).roundToInt())) continue

                // Follow the stem toward its distant end. Allow tiny holes in
                // antialiased rasterization, but stop before unrelated symbols.
                var tip = head.y + direction * far
                var empty = 0
                for (distance in far..max(far, (s * 4.9f).roundToInt())) {
                    val y = head.y + direction * distance
                    if (y !in 0 until height) break
                    if (onStaffLine(y)) continue
                    if (isInk(x, y)) {
                        tip = y
                        empty = 0
                    } else {
                        empty += 1
                        if (empty > max(2, (s * 0.30f).roundToInt())) break
                    }
                }

                val candidate = Stem(x, direction, tip, ratio)
                if (best == null || candidate.strength > best.strength) best = candidate
            }
        }
        return best
    }

    private fun countBeamsBetween(
        a: Stem,
        b: Stem,
        first: GlyphHead,
        second: GlyphHead,
    ): Int {
        val left = min(a.x, b.x)
        val right = max(a.x, b.x)
        val span = right - left
        if (span < spacing * 1.20f || span > spacing * 8.5f) return 0
        if (abs(a.tipY - b.tipY) > spacing * 1.5f) return 0

        val startY = max(1, min(a.tipY, b.tipY) - (spacing * 1.1f).roundToInt())
        val endY = min(height - 2, max(a.tipY, b.tipY) + (spacing * 1.1f).roundToInt())
        val sampleX = (1..7).map { left + span * it / 8 }
        val blackRows = mutableListOf<Int>()

        for (y in startY..endY) {
            if (onStaffLine(y)) continue
            // A horizontal staff line or an adjacent notehead is not a beam.
            if (abs(y - first.y) < spacing * 1.55f ||
                abs(y - second.y) < spacing * 1.55f) continue
            val darkPoints = sampleX.count { x ->
                (-1..1).any { dy -> isInk(x, y + dy) && !onStaffLine(y + dy) }
            }
            if (darkPoints >= 6) blackRows += y
        }
        if (blackRows.isEmpty()) return 0

        // Each thick, separate horizontal band corresponds to one beam.
        // A double beam has two distinct bands separated by white pixels.
        val groups = mutableListOf<MutableList<Int>>()
        blackRows.forEach { y ->
            val last = groups.lastOrNull()
            if (last == null || y - last.last() > max(2, (spacing * 0.20f).roundToInt())) {
                groups += mutableListOf(y)
            } else last += y
        }
        return groups.count { band ->
            val center = band.average()
            // Reject distant artifacts such as lyrics or horizontal brackets.
            abs(center - a.tipY) <= spacing * 1.2f &&
                abs(center - b.tipY) <= spacing * 1.2f
        }.coerceAtMost(3)
    }

    private fun countFlags(stem: Stem, head: GlyphHead): Int {
        val s = spacing
        // Upstem flags curl to the right and DOWN from the stem tip.
        // Downstem flags curl to the right and UP.
        val extend = if (stem.direction < 0) 1 else -1
        val x1 = stem.x + max(2, (s * 0.40f).roundToInt())
        val x2 = stem.x + max(3, (s * 1.20f).roundToInt())
        if (x2 !in 0 until width) return 0

        val marks = mutableListOf<Int>()
        for (distance in max(2, (s * 0.15f).roundToInt())..max(3, (s * 1.6f).roundToInt())) {
            val y = stem.tipY + extend * distance
            if (y !in 1 until height - 1 || onStaffLine(y)) continue
            if (abs(y - head.y) < spacing * 1.4f) continue
            val dark = (x1..x2).count { x ->
                isInk(x, y) || isInk(x, y - 1) || isInk(x, y + 1)
            }
            if (dark >= max(2, (s * 0.30f).roundToInt())) marks += distance
        }
        if (marks.isEmpty()) return 0

        val segments = mutableListOf<MutableList<Int>>()
        marks.forEach { distance ->
            val last = segments.lastOrNull()
            if (last == null || distance - last.last() > max(2, (s * 0.20f).roundToInt())) {
                segments += mutableListOf(distance)
            } else last += distance
        }
        return segments.count { it.size >= max(2, (s * 0.13f).roundToInt()) }.coerceAtMost(3)
    }

    private fun detectDot(head: GlyphHead, allHeads: List<GlyphHead>): Boolean {
        val s = spacing
        val left = (head.x + s * 0.96f).roundToInt().coerceIn(0, width - 1)
        val right = (head.x + s * 2.0f).roundToInt().coerceIn(0, width - 1)
        if (right <= left) return false
        val top = max(0, (head.y - s * 0.65f).roundToInt())
        val bottom = min(height - 1, (head.y + s * 0.65f).roundToInt())
        if (top >= bottom) return false
        if (allHeads.any { it !== head &&
                it.x in left - 2..right + 2 && abs(it.y - head.y) < s }) {
            return false
        }

        val visited = BooleanArray((right - left + 1) * (bottom - top + 1))
        fun pos(x: Int, y: Int) = (y - top) * (right - left + 1) + x - left

        for (y in top..bottom) {
            if (onStaffLine(y)) continue
            for (x in left..right) {
                if (!isInk(x, y) || visited[pos(x, y)]) continue
                val queue = ArrayDeque<Pair<Int, Int>>()
                queue.add(x to y)
                visited[pos(x, y)] = true
                var area = 0
                var minX = x
                var maxX = x
                var minY = y
                var maxY = y
                while (queue.isNotEmpty()) {
                    val (px, py) = queue.removeFirst()
                    area += 1
                    minX = min(minX, px); maxX = max(maxX, px)
                    minY = min(minY, py); maxY = max(maxY, py)
                    for (dy in -1..1) for (dx in -1..1) {
                        val xx = px + dx
                        val yy = py + dy
                        if (xx in left..right && yy in top..bottom &&
                            !onStaffLine(yy) && isInk(xx, yy) && !visited[pos(xx, yy)]) {
                            visited[pos(xx, yy)] = true
                            queue.add(xx to yy)
                        }
                    }
                }
                val w = maxX - minX + 1
                val h = maxY - minY + 1
                val minSize = max(2, (s * 0.10f).roundToInt())
                val maxSize = max(4, (s * 0.47f).roundToInt())
                if (area >= 3 && w in minSize..maxSize && h in minSize..maxSize &&
                    area <= (s * s * 0.26f) && abs((minY + maxY) / 2f - head.y) <= s * 0.6f) {
                    return true
                }
            }
        }
        return false
    }

    private fun onStaffLine(y: Int): Boolean =
        staffLines.any { abs(it - y) <= max(0, (spacing * 0.05f).roundToInt()) }

    private fun isInk(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height &&
            pixels[y * width + x].toInt() != 0
}
