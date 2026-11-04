package com.romuel.pianosolfege.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleRestReaderTest {
    private val width = 220
    private val height = 160
    private val spacing = 20f
    private val lines = intArrayOf(30, 50, 70, 90, 110)

    private fun image(draw: (ByteArray) -> Unit): ByteArray = ByteArray(width * height).also(draw)
    private fun rect(image: ByteArray, x0: Int, y0: Int, x1: Int, y1: Int) {
        for (y in y0..y1) for (x in x0..x1) image[y * width + x] = 1
    }

    @Test fun wholeRestHangingUnderSecondLineIsFourSilentBeats() {
        val ink = image {
            for (y in lines) rect(it, 0, y, width - 1, y)
            rect(it, 88, 51, 105, 57)
        }
        val rests = SimpleRestReader(ink, width, height, lines, spacing)
            .detect(40, 170, emptyList())
        assertEquals(1, rests.size)
        assertEquals(4f, rests.single().beats, 0.0001f)
    }

    @Test fun halfRestOnMiddleLineIsTwoBeats() {
        val ink = image {
            for (y in lines) rect(it, 0, y, width - 1, y)
            rect(it, 88, 63, 105, 69)
        }
        val rests = SimpleRestReader(ink, width, height, lines, spacing)
            .detect(40, 170, emptyList())
        assertEquals(1, rests.size)
        assertEquals(2f, rests.single().beats, 0.0001f)
    }

    @Test fun noteheadOrBarlineLocationIsNeverCountedAsARest() {
        val ink = image {
            rect(it, 88, 51, 105, 57)
            rect(it, 135, 63, 152, 69)
        }
        val rests = SimpleRestReader(ink, width, height, lines, spacing)
            .detect(40, 170, listOf(96), listOf(143))
        assertTrue(rests.isEmpty())
    }
}
