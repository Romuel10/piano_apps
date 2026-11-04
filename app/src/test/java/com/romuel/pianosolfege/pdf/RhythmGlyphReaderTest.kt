package com.romuel.pianosolfege.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Generated raster glyphs: tests measure what the program reads, not labels supplied to it. */
class RhythmGlyphReaderTest {
    private class Raster {
        val width = 240
        val height = 190
        val spacing = 20f
        val lines = intArrayOf(70, 90, 110, 130, 150)
        val ink = ByteArray(width * height)

        fun rect(x1: Int, y1: Int, x2: Int, y2: Int) {
            for (y in y1.coerceAtLeast(0)..y2.coerceAtMost(height - 1)) {
                for (x in x1.coerceAtLeast(0)..x2.coerceAtMost(width - 1)) {
                    ink[y * width + x] = 1
                }
            }
        }

        fun head(x: Int, y: Int) {
            rect(x - 5, y - 4, x + 5, y + 4)
        }

        fun stem(x: Int, y: Int) {
            rect(x + 7, y - 68, x + 9, y - 3)
        }

        fun read(vararg heads: GlyphHead) =
            RhythmGlyphReader(ink, width, height, lines, spacing).recognize(heads.toList())
    }

    @Test fun quarterHasOneBeat() {
        val r = Raster()
        r.head(50, 130)
        r.stem(50, 130)
        val result = r.read(GlyphHead(50, 130, filled = true)).single()
        assertEquals(1f, result.beats, 0.0001f)
        assertTrue(result.stemDetected)
    }

    @Test fun oneBeamMeansEighthNote() {
        val r = Raster()
        r.head(50, 130); r.head(100, 130)
        r.stem(50, 130); r.stem(100, 130)
        r.rect(57, 62, 109, 65)
        val result = r.read(GlyphHead(50, 130, true), GlyphHead(100, 130, true))
        assertEquals(0.5f, result[0].beats, 0.0001f)
        assertEquals(0.5f, result[1].beats, 0.0001f)
        assertEquals(1, result[0].beamCount)
    }

    @Test fun twoBeamsMeanSixteenthNote() {
        val r = Raster()
        r.head(50, 130); r.head(100, 130)
        r.stem(50, 130); r.stem(100, 130)
        r.rect(57, 62, 109, 65)
        r.rect(57, 72, 109, 75)
        val result = r.read(GlyphHead(50, 130, true), GlyphHead(100, 130, true))
        assertEquals(0.25f, result[0].beats, 0.0001f)
        assertEquals(0.25f, result[1].beats, 0.0001f)
        assertEquals(2, result[0].beamCount)
    }

    @Test fun threeBeamsMeanThirtySecondNote() {
        val r = Raster()
        r.head(50, 130); r.head(100, 130)
        r.stem(50, 130); r.stem(100, 130)
        r.rect(57, 57, 109, 59)
        r.rect(57, 66, 109, 68)
        r.rect(57, 75, 109, 77)
        val result = r.read(GlyphHead(50, 130, true), GlyphHead(100, 130, true))
        assertEquals(0.125f, result[0].beats, 0.0001f)
        assertEquals(3, result[0].beamCount)
    }

    @Test fun singleFlagAndDoubleFlagAreRecognized() {
        val single = Raster()
        single.head(50, 130); single.stem(50, 130)
        single.rect(66, 67, 77, 70)
        val one = single.read(GlyphHead(50, 130, true)).single()
        assertEquals(0.5f, one.beats, 0.0001f)

        val double = Raster()
        double.head(50, 130); double.stem(50, 130)
        double.rect(66, 67, 77, 70)
        double.rect(66, 80, 77, 83)
        val two = double.read(GlyphHead(50, 130, true)).single()
        assertEquals(0.25f, two.beats, 0.0001f)
    }

    @Test fun augmentationDotAddsHalfOfTheDuration() {
        val r = Raster()
        r.head(50, 130); r.stem(50, 130)
        r.rect(77, 121, 80, 124)
        val result = r.read(GlyphHead(50, 130, true)).single()
        assertEquals(1.5f, result.beats, 0.0001f)
        assertTrue(result.dotted)
    }

    @Test fun hollowHalfAndWholeKeepTheirDurations() {
        val half = Raster()
        half.stem(50, 130)
        assertEquals(2f, half.read(GlyphHead(50, 130, false)).single().beats, 0.0001f)
        val whole = Raster()
        assertEquals(4f, whole.read(GlyphHead(50, 130, false)).single().beats, 0.0001f)
    }

    @Test fun staffLinesAloneCannotPretendToBeBeams() {
        val r = Raster()
        for (y in r.lines) r.rect(0, y, r.width - 1, y)
        r.head(50, 130); r.head(100, 130)
        r.stem(50, 130); r.stem(100, 130)
        val result = r.read(GlyphHead(50, 130, true), GlyphHead(100, 130, true))
        assertEquals(1f, result[0].beats, 0.0001f)
        assertEquals(1f, result[1].beats, 0.0001f)
    }
}
