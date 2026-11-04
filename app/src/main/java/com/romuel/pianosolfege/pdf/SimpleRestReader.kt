package com.romuel.pianosolfege.pdf

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Conservative detection of rectangular whole and half rests. */
internal data class RestReading(val x: Int, val beats: Float, val confidence: Float)

internal class SimpleRestReader(
    private val pixels: ByteArray,
    private val width: Int,
    private val height: Int,
    private val staffLines: IntArray,
    private val spacing: Float,
) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height)
        require(staffLines.size == 5)
        require(spacing > 0)
    }

    /**
     * Whole rest hangs UNDER the second staff line from the top (4 beats).
     * Half rest sits ABOVE the middle line (2 beats).
     *
     * These narrow geometric constraints are intentional: ordinary notes,
     * stems or a beam must never be accepted as rests without evidence.
     */
    fun detect(
        left: Int,
        right: Int,
        headXs: List<Int>,
        barlineXs: List<Int> = emptyList(),
    ): List<RestReading> {
        val xMin = left.coerceIn(0, width - 1)
        val xMax = right.coerceIn(xMin, width - 1)
        val yMin = max(0, staffLines[1] - (spacing * 0.2f).roundToInt())
        val yMax = min(height - 1, staffLines[2] + (spacing * 0.15f).roundToInt())
        val tested = BooleanArray((xMax - xMin + 1) * (yMax - yMin + 1))
        fun index(x: Int, y: Int) = (y - yMin) * (xMax - xMin + 1) + x - xMin
        fun onLine(y: Int) = staffLines.any { abs(it - y) <= max(0, (spacing * 0.04f).roundToInt()) }
        fun isInk(x: Int, y: Int) = pixels[y * width + x].toInt() != 0

        val matches = mutableListOf<RestReading>()
        for (y in yMin..yMax) {
            if (onLine(y)) continue
            for (x in xMin..xMax) {
                if (tested[index(x, y)] || !isInk(x, y)) continue
                tested[index(x, y)] = true
                val queue = ArrayDeque<Pair<Int, Int>>()
                queue.add(x to y)
                var area = 0
                var top = y
                var bottom = y
                var x0 = x
                var x1 = x

                while (queue.isNotEmpty()) {
                    val (px, py) = queue.removeFirst()
                    area++
                    top = min(top, py); bottom = max(bottom, py)
                    x0 = min(x0, px); x1 = max(x1, px)
                    for (dy in -1..1) for (dx in -1..1) {
                        val xx = px + dx
                        val yy = py + dy
                        if (xx in xMin..xMax && yy in yMin..yMax && !onLine(yy) &&
                            isInk(xx, yy) && !tested[index(xx, yy)]) {
                            tested[index(xx, yy)] = true
                            queue.add(xx to yy)
                        }
                    }
                }
                val boxWidth = x1 - x0 + 1
                val boxHeight = bottom - top + 1
                val centerX = (x0 + x1) / 2
                if (boxWidth !in max(3, (spacing * 0.52f).roundToInt())..
                        max(4, (spacing * 1.45f).roundToInt())) continue
                if (boxHeight !in max(2, (spacing * 0.12f).roundToInt())..
                        max(3, (spacing * 0.58f).roundToInt())) continue
                if (boxWidth.toFloat() / boxHeight < 1.8f) continue
                if (area.toFloat() / (boxWidth * boxHeight) < 0.70f) continue
                if (headXs.any { abs(centerX - it) < spacing * 1.25f } ||
                    barlineXs.any { abs(centerX - it) < spacing * 0.85f }) continue

                val whole = top > staffLines[1] &&
                    top <= staffLines[1] + spacing * 0.28f &&
                    bottom < staffLines[1] + spacing * 0.60f
                val half = bottom < staffLines[2] &&
                    bottom >= staffLines[2] - spacing * 0.28f &&
                    top > staffLines[2] - spacing * 0.62f
                val beats = when {
                    whole -> 4f
                    half -> 2f
                    else -> continue
                }
                matches += RestReading(centerX, beats, 0.78f)
            }
        }
        return matches.sortedBy { it.x }
    }
}
