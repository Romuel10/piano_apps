package com.romuel.pianosolfege.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffTimelineTest {
    @Test fun bassHalfNotesRemainHeldWhileTreblePlaysQuarters() {
        val events = listOf(
            StaffEvent(0, 0, 100f, 60, 1.0, 14f),
            StaffEvent(0, 0, 145f, 62, 1.0, 14f),
            StaffEvent(0, 0, 190f, 64, 1.0, 14f),
            StaffEvent(0, 0, 235f, 60, 1.0, 14f),
            StaffEvent(0, 1, 100f, 48, 2.0, 14f),
            StaffEvent(0, 1, 190f, 55, 2.0, 14f),
        )
        val notes = StaffTimeline.build(events)
        assertEquals(6, notes.size)
        assertEquals(0.0, notes.first { it.midi == 48 }.startBeats, 0.001)
        assertEquals(2.0, notes.first { it.midi == 48 }.durationBeats, 0.001)
        assertEquals(1.0, notes.first { it.midi == 62 }.startBeats, 0.001)
        assertEquals(2.0, notes.first { it.midi == 55 }.startBeats, 0.001)
        assertEquals(4.0, notes.maxOf { it.startBeats + it.durationBeats }, 0.001)
    }

    @Test fun successiveSystemsStartAtTheEndOfThePreviousSystem() {
        val events = listOf(
            StaffEvent(0, 0, 100f, 60, 1.0, 12f),
            StaffEvent(0, 0, 150f, 62, 1.0, 12f),
            StaffEvent(0, 1, 100f, 48, 2.0, 12f),
            StaffEvent(1, 0, 95f, 64, 1.0, 12f),
            StaffEvent(1, 1, 95f, 52, 1.0, 12f),
        )
        val notes = StaffTimeline.build(events)
        assertEquals(2.0, notes.first { it.midi == 64 }.startBeats, 0.001)
        assertEquals(2.0, notes.first { it.midi == 52 }.startBeats, 0.001)
    }

    @Test fun verticallyAlignedNotesBecomeASimultaneousChord() {
        val events = listOf(
            StaffEvent(0, 0, 100f, 60, 1.0, 14f),
            StaffEvent(0, 0, 106f, 64, 1.0, 14f),
            StaffEvent(0, 0, 105f, 67, 1.0, 14f),
            StaffEvent(0, 0, 170f, 62, 1.0, 14f),
        )
        val notes = StaffTimeline.build(events)
        assertTrue(notes.filter { it.startBeats == 0.0 }.map { it.midi }.containsAll(listOf(60, 64, 67)))
        assertEquals(1.0, notes.first { it.midi == 62 }.startBeats, 0.001)
    }
}
