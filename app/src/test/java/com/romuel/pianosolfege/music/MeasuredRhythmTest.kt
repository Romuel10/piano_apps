package com.romuel.pianosolfege.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rhythm of the supplied Frère Jacques example is 4/4, ♩=100. */
class MeasuredRhythmTest {
    private val quarter = 1.0
    private val half = 2.0

    @Test fun fourQuartersAndTwoHalvesAreSynchronizedInFourFour() {
        val events = listOf(
            StaffEvent(0, 0, 120f, 60, quarter, 12f),
            StaffEvent(0, 0, 165f, 62, quarter, 12f),
            StaffEvent(0, 0, 210f, 64, quarter, 12f),
            StaffEvent(0, 0, 260f, 60, quarter, 12f),
            StaffEvent(0, 1, 120f, 48, half, 12f),
            StaffEvent(0, 1, 210f, 55, half, 12f),
            StaffEvent(0, 0, 335f, 60, quarter, 12f),
            StaffEvent(0, 0, 380f, 62, quarter, 12f),
            StaffEvent(0, 0, 430f, 64, quarter, 12f),
            StaffEvent(0, 0, 485f, 60, quarter, 12f),
            StaffEvent(0, 1, 335f, 48, half, 12f),
            StaffEvent(0, 1, 430f, 55, half, 12f),
        )
        val result = StaffTimeline.analyze(
            events, mapOf(0 to listOf(95f, 300f, 525f)), ScoreMeter(4, 4),
        )
        assertEquals(2, result.measures)
        assertTrue(result.valid)
        assertEquals(12, result.notes.size)
        assertEquals(4.0, result.notes.first { it.midi == 60 && it.startBeats >= 4.0 }.startBeats, 0.001)
        assertEquals(6.0, result.notes.first { it.midi == 55 && it.startBeats > 2 }.startBeats, 0.001)
        assertEquals(8.0, result.notes.maxOf { it.startBeats + it.durationBeats }, 0.001)
    }

    @Test fun eighthNotesCountAsHalfABeat() {
        val events = listOf(
            StaffEvent(0, 0, 120f, 67, 0.5, 12f),
            StaffEvent(0, 0, 160f, 69, 0.5, 12f),
            StaffEvent(0, 0, 200f, 67, 0.5, 12f),
            StaffEvent(0, 0, 240f, 65, 0.5, 12f),
            StaffEvent(0, 0, 280f, 64, 1.0, 12f),
            StaffEvent(0, 0, 325f, 60, 1.0, 12f),
            StaffEvent(0, 1, 120f, 48, 2.0, 12f),
            StaffEvent(0, 1, 280f, 55, 2.0, 12f),
        )
        val result = StaffTimeline.analyze(
            events, mapOf(0 to listOf(90f, 400f)), ScoreMeter(4, 4),
        )
        assertTrue(result.valid)
        assertEquals(1, result.measures)
        assertEquals(0.5, result.notes.first { it.midi == 67 }.durationBeats, 0.001)
        assertEquals(3.0, result.notes.first { it.midi == 60 }.startBeats, 0.001)
    }

    @Test fun missingBeatRaisesWarningInsteadOfInventingDuration() {
        val events = listOf(
            StaffEvent(0, 0, 110f, 60, 1.0, 12f),
            StaffEvent(0, 0, 160f, 62, 1.0, 12f),
            StaffEvent(0, 0, 210f, 64, 1.0, 12f),
            StaffEvent(0, 1, 110f, 48, 2.0, 12f),
            StaffEvent(0, 1, 220f, 55, 2.0, 12f),
        )
        val result = StaffTimeline.analyze(
            events, mapOf(0 to listOf(95f, 300f)), ScoreMeter(4, 4),
        )
        assertFalse(result.valid)
        assertEquals(1, result.issues.size)
        assertEquals(3.0, result.issues.single().observedBeats, 0.001)
        assertEquals(4.0, result.issues.single().expectedBeats, 0.001)
    }

    @Test fun sixEightIsThreeQuarterBeatsNotSix() {
        val events = listOf(
            StaffEvent(0, 0, 110f, 60, 0.5, 12f),
            StaffEvent(0, 0, 150f, 62, 0.5, 12f),
            StaffEvent(0, 0, 190f, 64, 0.5, 12f),
            StaffEvent(0, 0, 230f, 65, 0.5, 12f),
            StaffEvent(0, 0, 270f, 67, 0.5, 12f),
            StaffEvent(0, 0, 310f, 69, 0.5, 12f),
        )
        val result = StaffTimeline.analyze(
            events, mapOf(0 to listOf(90f, 370f)), ScoreMeter(6, 8),
        )
        assertTrue(result.valid)
        assertEquals(3.0, result.notes.maxOf { it.startBeats + it.durationBeats }, 0.001)
    }

    @Test fun finalDoubleBarlineIsNotAnExtraMeasure() {
        val events = listOf(
            StaffEvent(0, 0, 120f, 60, 4.0, 12f),
        )
        val result = StaffTimeline.analyze(
            events, mapOf(0 to listOf(95f, 450f, 458f)), ScoreMeter(4, 4),
        )
        assertEquals(1, result.measures)
        assertTrue(result.valid)
    }
}
