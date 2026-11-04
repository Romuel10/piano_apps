package com.romuel.pianosolfege.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreTempoMapTest {
    @Test fun tempoChangeChangesTimeAtThatMeasureOnly() {
        val map = ScoreTempoMap(100, 100, listOf(
            TempoChange(0.0, 100),
            TempoChange(4.0, 200),
        ))
        assertEquals(2.4, map.secondsAtBeat(4.0), 0.001)
        assertEquals(2.7, map.secondsAtBeat(5.0), 0.001)
        assertEquals(3.6, map.secondsAtBeat(8.0), 0.001)
    }

    @Test fun tempoSliderPreservesRelativeScoreChanges() {
        val map = ScoreTempoMap(120, 100, listOf(
            TempoChange(0.0, 100),
            TempoChange(4.0, 150),
        ))
        assertEquals(2.0, map.secondsAtBeat(4.0), 0.001)
        assertEquals(2.333333, map.secondsAtBeat(5.0), 0.001)
    }

    @Test fun noMarkingsUsesOneFixedTempo() {
        val map = ScoreTempoMap(60)
        assertEquals(4.0, map.secondsAtBeat(4.0), 0.001)
    }
}
