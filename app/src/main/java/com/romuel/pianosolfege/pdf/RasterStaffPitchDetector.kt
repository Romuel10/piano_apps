package com.romuel.pianosolfege.pdf

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Finds actual oval noteheads, not just patches of ink at theoretical pitches.
 *
 * On clean printed staves, long horizontal strokes are removed first, then
 * a local morphology closes hollow noteheads and separates thin stems. The
 * remaining compact connected components are quantized to a staff half-step.
 *
 * Pixels are binary (1=ink, 0=background), and the detector is Android-free
 * so we can regression-test it with synthetic and real PDF raster fixtures.
 */
internal data class RasterNotehead(
    val x: Int,
    val y: Int,
    val step: Int,
    val filled: Boolean,
    val confidence: Float,
)

internal class RasterStaffPitchDetector(
    private val ink: ByteArray,
    private val width: Int,
    private val height: Int,
) {
    init {
        require(width > 0 && height > 0 && ink.size == width * height)
    }

    fun detect(
        staffLines: IntArray,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
    ): List<RasterNotehead> {
        if (staffLines.size != 5) return emptyList()
        val spacing = (staffLines.last() - staffLines.first()) / 4f
        if (spacing < 5f || spacing > 90f) return emptyList()

        val xStart = left.coerceIn(0, width - 1)
        val xEnd = right.coerceIn(xStart, width - 1)
        val yStart = top.coerceIn(0, height - 1)
        val yEnd = bottom.coerceIn(yStart, height - 1)
        val roiWidth = xEnd - xStart + 1
        val roiHeight = yEnd - yStart + 1
        if (roiWidth < spacing * 4 || roiHeight < spacing * 3) return emptyList()

        var pixels = ByteArray(roiWidth * roiHeight)
        for (y in 0 until roiHeight) {
            System.arraycopy(ink, (yStart + y) * width + xStart, pixels, y * roiWidth, roiWidth)
        }

        // Staff lines (and long beams) are removed only in the working copy:
        // the original ink remains available to measure whether a head is filled.
        val minHorizontalRun = max(18, (spacing * 3.1f).roundToInt())
        for (y in 0 until roiHeight) {
            val base = y * roiWidth
            var x = 0
            while (x < roiWidth) {
                if (pixels[base + x].toInt() == 0) {
                    x++
                    continue
                }
                val first = x
                while (x < roiWidth && pixels[base + x].toInt() != 0) x++
                if (x - first >= minHorizontalRun) {
                    for (px in first until x) pixels[base + px] = 0
                }
            }
        }

        // Morphology intentionally scales with distance between staff lines.
        // Rectangular kernels are separable, making every pass O(width*height).
        val closeX = max(3, (spacing * 0.75f).roundToInt() / 2)
        val closeY = max(2, (spacing * 0.56f).roundToInt() / 2)
        val openX = max(2, (spacing * 0.56f).roundToInt() / 2)
        val joinY = max(1, (spacing * 0.34f).roundToInt() / 2)

        pixels = horizontal(pixels, roiWidth, roiHeight, closeX, dilate = true)
        pixels = vertical(pixels, roiWidth, roiHeight, closeY, dilate = true)
        pixels = horizontal(pixels, roiWidth, roiHeight, closeX, dilate = false)
        pixels = vertical(pixels, roiWidth, roiHeight, closeY, dilate = false)
        pixels = horizontal(pixels, roiWidth, roiHeight, openX, dilate = false)
        pixels = horizontal(pixels, roiWidth, roiHeight, openX, dilate = true)
        pixels = vertical(pixels, roiWidth, roiHeight, joinY, dilate = true)
        pixels = vertical(pixels, roiWidth, roiHeight, joinY, dilate = false)

        val visited = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        val candidates = mutableListOf<RasterNotehead>()
        val minWidth = max(5, (spacing * 0.70f).roundToInt())
        val maxWidth = max(minWidth + 2, (spacing * 2.85f).roundToInt())
        val minHeight = max(4, (spacing * 0.48f).roundToInt())
        val maxHeight = max(minHeight + 2, (spacing * 1.80f).roundToInt())

        for (pos in pixels.indices) {
            if (pixels[pos].toInt() == 0 || visited[pos]) continue
            var front = 0
            var size = 1
            queue[0] = pos
            visited[pos] = true
            var xmin = pos % roiWidth
            var xmax = xmin
            var ymin = pos / roiWidth
            var ymax = ymin
            var sumX = 0L
            var sumY = 0L

            while (front < size) {
                val idx = queue[front++]
                val x = idx % roiWidth
                val y = idx / roiWidth
                sumX += x
                sumY += y
                xmin = min(xmin, x)
                xmax = max(xmax, x)
                ymin = min(ymin, y)
                ymax = max(ymax, y)

                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = x + dx
                    val yy = y + dy
                    if (xx !in 0 until roiWidth || yy !in 0 until roiHeight) continue
                    val neighbour = yy * roiWidth + xx
                    if (!visited[neighbour] && pixels[neighbour].toInt() != 0) {
                        visited[neighbour] = true
                        queue[size++] = neighbour
                    }
                }
            }

            val componentWidth = xmax - xmin + 1
            val componentHeight = ymax - ymin + 1
            if (componentWidth !in minWidth..maxWidth ||
                componentHeight !in minHeight..maxHeight ||
                size < spacing * spacing * 0.16f ||
                componentWidth.toFloat() / componentHeight !in 1.05f..3.5f) {
                continue
            }

            val cx = xStart + (sumX.toFloat() / size).roundToInt()
            val cy = yStart + (sumY.toFloat() / size).roundToInt()
            if (cx <= xStart + (spacing * 0.25f).roundToInt() ||
                cx >= xEnd - (spacing * 0.25f).roundToInt()) continue
            val step = ((staffLines.last() - cy) * 2f / spacing).roundToInt()
            if (step !in -8..16) continue
            val expectedY = staffLines.last() - step * spacing / 2f
            if (abs(cy - expectedY) > spacing * 0.26f + 0.8f) continue

            val filled = isFilled(cx, cy, spacing)
            val areaScore = (size / (spacing * spacing * 0.90f)).coerceIn(0.1f, 1f)
            candidates += RasterNotehead(
                cx, cy, step, filled,
                (0.60f + 0.32f * areaScore).coerceIn(0f, 1f),
            )
        }

        val accepted = mutableListOf<RasterNotehead>()
        candidates.sortedByDescending { it.confidence }.forEach { note ->
            if (accepted.none { other ->
                    abs(note.x - other.x) < spacing * 0.66f &&
                        abs(note.y - other.y) < spacing * 0.66f
                }) accepted += note
        }
        return accepted.sortedWith(compareBy<RasterNotehead> { it.x }.thenBy { it.y })
    }

    private fun isFilled(x: Int, y: Int, spacing: Float): Boolean {
        val dx = max(2, (spacing * 0.22f).roundToInt())
        val dy = max(2, (spacing * 0.22f).roundToInt())
        var black = 0
        var total = 0
        for (yy in max(0, y - dy)..min(height - 1, y + dy)) {
            for (xx in max(0, x - dx)..min(width - 1, x + dx)) {
                total++
                if (ink[yy * width + xx].toInt() != 0) black++
            }
        }
        return total > 0 && black.toFloat() / total >= 0.65f
    }

    private fun horizontal(
        pixels: ByteArray, w: Int, h: Int, radius: Int, dilate: Boolean,
    ): ByteArray {
        val output = ByteArray(pixels.size)
        val sums = IntArray(w + 1)
        for (y in 0 until h) {
            val row = y * w
            sums[0] = 0
            for (x in 0 until w) {
                sums[x + 1] = sums[x] + if (pixels[row + x].toInt() != 0) 1 else 0
            }
            for (x in 0 until w) {
                val l = max(0, x - radius)
                val r = min(w - 1, x + radius)
                val count = sums[r + 1] - sums[l]
                if (if (dilate) count > 0 else count == r - l + 1) {
                    output[row + x] = 1
                }
            }
        }
        return output
    }

    private fun vertical(
        pixels: ByteArray, w: Int, h: Int, radius: Int, dilate: Boolean,
    ): ByteArray {
        val output = ByteArray(pixels.size)
        val sums = IntArray(h + 1)
        for (x in 0 until w) {
            sums[0] = 0
            for (y in 0 until h) {
                sums[y + 1] = sums[y] + if (pixels[y * w + x].toInt() != 0) 1 else 0
            }
            for (y in 0 until h) {
                val t = max(0, y - radius)
                val b = min(h - 1, y + radius)
                val count = sums[b + 1] - sums[t]
                if (if (dilate) count > 0 else count == b - t + 1) {
                    output[y * w + x] = 1
                }
            }
        }
        return output
    }
}
