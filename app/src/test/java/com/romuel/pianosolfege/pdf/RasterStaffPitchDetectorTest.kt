package com.romuel.pianosolfege.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterStaffPitchDetectorTest {
    private val w = 640
    private val h = 300
    private val spacing = 16
    private val trebleLines = intArrayOf(70, 86, 102, 118, 134)
    private val bassLines = intArrayOf(200, 216, 232, 248, 264)

    private fun paper(): ByteArray = ByteArray(w * h).also { pixels ->
        for (line in trebleLines + bassLines) {
            for (x in 40..600) pixels[line * w + x] = 1
        }
    }

    private fun oval(pixels: ByteArray, x: Int, y: Int, filled: Boolean) {
        // Ratio 12:5 resembles the slanted, engraved noteheads in the source PDF.
        for (dy in -6..6) for (dx in -12..12) {
            val u = (dx + dy * 0.38) / 11.0
            val v = dy / 5.7
            val outside = u * u + v * v
            if (outside <= 1.0 && (filled || outside >= 0.43)) {
                val xx = x + dx
                val yy = y + dy
                pixels[yy * w + xx] = 1
            }
        }
    }

    private fun stem(pixels: ByteArray, x: Int, y: Int) {
        // Upstem extends from the right of the head to ~3.5 staff spaces up.
        for (yy in y - 55..y - 3) {
            for (xx in x + 9..x + 10) pixels[yy * w + xx] = 1
        }
    }

    @Test fun printedTrebleNotesAreQuantizedToCorrectStaffSteps() {
        val pixels = paper()
        val heads = listOf(
            Triple(175, 150, -2), // C4 below the treble staff
            Triple(260, 142, -1), // D4
            Triple(345, 134, 0),  // E4 on the bottom line
            Triple(430, 126, 1),  // F4 in the space
        )
        for ((x, y, _) in heads) { oval(pixels, x, y, true); stem(pixels, x, y) }
        val recognized = RasterStaffPitchDetector(pixels, w, h)
            .detect(trebleLines, 120, 520, 35, 180)
        assertEquals("Number of printed noteheads", 4, recognized.size)
        assertEquals(listOf(-2, -1, 0, 1), recognized.sortedBy { it.x }.map { it.step })
        assertTrue(recognized.all { it.filled })
    }

    @Test fun bassHollowHeadsAreNotMisclassifiedAsFilledBlackNotes() {
        val pixels = paper()
        oval(pixels, 190, 240, false) // bass C3 => step 3
        stem(pixels, 190, 240)
        oval(pixels, 330, 208, false) // bass A3? step 7 from bottom (G3 at 208)
        stem(pixels, 330, 208)
        val recognized = RasterStaffPitchDetector(pixels, w, h)
            .detect(bassLines, 120, 490, 180, 290)
        assertEquals(2, recognized.size)
        assertEquals(listOf(3, 7), recognized.sortedBy { it.x }.map { it.step })
        assertFalse(recognized.any { it.filled })
    }

    @Test fun linesAndStemsWithoutHeadsDoNotBecomeMusicNotes() {
        val pixels = paper()
        for (y in 90..150) for (x in 230..231) pixels[y * w + x] = 1
        val recognized = RasterStaffPitchDetector(pixels, w, h)
            .detect(trebleLines, 120, 520, 35, 185)
        assertTrue(recognized.isEmpty())
    }

    @Test fun allThreeNaturalMelodyPitchesAreDistinct() {
        val pixels = paper()
        oval(pixels, 185, 150, true)
        oval(pixels, 280, 142, true)
        oval(pixels, 370, 134, true)
        val recognized = RasterStaffPitchDetector(pixels, w, h)
            .detect(trebleLines, 130, 450, 35, 182)
        assertEquals(listOf(-2, -1, 0), recognized.map { it.step })
    }
}
