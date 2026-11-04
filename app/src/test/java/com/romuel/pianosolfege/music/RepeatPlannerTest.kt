package com.romuel.pianosolfege.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatPlannerTest {
    @Test fun plainMeasuresArePlayedOnce() {
        val result = RepeatPlanner.expand(List(3) { MeasureMarkings() })
        assertEquals(listOf(0, 1, 2), result.indices)
    }

    @Test fun repeatsTwiceWithForwardBackwardBarlines() {
        val result = RepeatPlanner.expand(
            listOf(
                MeasureMarkings(forwardRepeat = true),
                MeasureMarkings(),
                MeasureMarkings(backwardRepeatTimes = 2),
                MeasureMarkings(),
            ),
        )
        assertEquals(listOf(0, 1, 2, 0, 1, 2, 3), result.indices)
    }

    @Test fun repeatWithoutForwardStartsAtFirstMeasure() {
        val result = RepeatPlanner.expand(
            listOf(MeasureMarkings(), MeasureMarkings(backwardRepeatTimes = 2)),
        )
        assertEquals(listOf(0, 1, 0, 1), result.indices)
    }

    @Test fun firstAndSecondEndingsAreDistinct() {
        val result = RepeatPlanner.expand(
            listOf(
                MeasureMarkings(forwardRepeat = true),
                MeasureMarkings(),
                MeasureMarkings(backwardRepeatTimes = 2, endingPasses = setOf(1)),
                MeasureMarkings(endingPasses = setOf(2)),
                MeasureMarkings(),
            ),
        )
        assertEquals(listOf(0, 1, 2, 0, 1, 3, 4), result.indices)
    }

    @Test fun repeatExpansionHasHardBound() {
        val result = RepeatPlanner.expand(
            listOf(MeasureMarkings(forwardRepeat = true), MeasureMarkings(backwardRepeatTimes = 5000)),
        )
        assertTrue(result.indices.size <= 5000)
        assertTrue(result.warnings.isNotEmpty())
    }
}
