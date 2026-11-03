package com.romuel.pianosolfege.music

import kotlin.math.abs
import kotlin.math.max

/** A detected head on one visual staff, in a particular system of the PDF. */
data class StaffEvent(
    val systemIndex: Int,
    val staffIndex: Int,
    val x: Float,
    val midi: Int,
    val beats: Double,
    val staffSpacing: Float,
)

/**
 * Arrange the notes in each staff independently and play the staves concurrently.
 * A half-note in the bass is never truncated to the length of a quarter note
 * sharing its onset in the treble.
 *
 * Horizontal distance is used only to detect notes belonging to the same chord;
 * it is NOT directly used as an audio timestamp. A system's timeline is derived
 * from musical durations, keeping beat alignment between two piano hands.
 */
object StaffTimeline {
    fun build(events: List<StaffEvent>): List<TimedNote> {
        if (events.isEmpty()) return emptyList()
        val result = mutableListOf<TimedNote>()
        var systemOffset = 0.0
        events.groupBy { it.systemIndex }.toSortedMap().forEach { (_, systemEvents) ->
            var longestStaff = 0.0
            systemEvents.groupBy { it.staffIndex }.forEach { (staffIndex, staffEvents) ->
                val groups = mutableListOf<MutableList<StaffEvent>>()
                staffEvents.sortedBy { it.x }.forEach { event ->
                    val last = groups.lastOrNull()
                    val tolerance = max(3.0f, event.staffSpacing * 0.78f)
                    val center = last?.map { it.x }?.average()?.toFloat() ?: 0f
                    if (last != null && abs(event.x - center) <= tolerance) {
                        last += event
                    } else {
                        groups += mutableListOf(event)
                    }
                }

                var onset = 0.0
                groups.forEach { group ->
                    // The shortest simultaneous note does not end a longer note
                    // in another voice or on another staff.
                    group.distinctBy { it.midi }.forEach { note ->
                        val duration = note.beats.coerceIn(0.125, 16.0)
                        result += TimedNote(
                            midi = note.midi,
                            startBeats = systemOffset + onset,
                            durationBeats = duration,
                        )
                    }
                    onset += group.maxOf { it.beats }.coerceIn(0.125, 16.0)
                }
                longestStaff = max(longestStaff, onset)
            }
            systemOffset += longestStaff
        }
        return result.sortedWith(
            compareBy<TimedNote> { it.startBeats }.thenBy { it.midi },
        )
    }
}
