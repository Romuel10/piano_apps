package com.romuel.pianosolfege.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreMarkingsTest {
    @Test fun tempoMatchesPrintedFrereJacquesMarking() {
        assertEquals(100, ScoreMarkings.detectTempo("♩ = 100"))
        assertEquals(100, ScoreMarkings.detectTempo("/=100"))
        assertEquals(100, ScoreMarkings.detectTempo("q = 100"))
        assertEquals(100, ScoreMarkings.detectTempo("100 BPM"))
        assertNull(ScoreMarkings.detectTempo("Frère Jacques 2026"))
    }

    @Test fun supportsThreeFourFourFourAndSixEight() {
        assertEquals(ScoreMeter(4, 4), ScoreMarkings.detectMeter("4/4"))
        assertEquals(ScoreMeter(3, 4), ScoreMarkings.detectMeter("3 / 4"))
        assertEquals(ScoreMeter(6, 8), ScoreMarkings.detectMeter("6/8"))
    }

    @Test fun acceptsStackedPrintedTimeSignatureFromOcr() {
        assertEquals(ScoreMeter(4, 4), ScoreMarkings.detectMeter("4\n4"))
    }
}
