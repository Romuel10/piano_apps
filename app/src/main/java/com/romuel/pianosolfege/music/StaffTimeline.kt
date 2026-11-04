package com.romuel.pianosolfege.music

import kotlin.math.abs
import kotlin.math.max

/** Meter in units of quarter-note beats, e.g. 6/8 = 3 quarter-note beats. */
data class ScoreMeter(val numerator: Int, val denominator: Int) {
    init {
        require(numerator in 1..16 && denominator in setOf(2, 4, 8, 16))
    }

    val quarterBeats: Double get() = numerator * 4.0 / denominator
    val display: String get() = "$numerator/$denominator"
}

/** Detected notehead on one visual staff in one PDF system. */
data class StaffEvent(
    val systemIndex: Int,
    val staffIndex: Int,
    val x: Float,
    val midi: Int,
    val beats: Double,
    val staffSpacing: Float,
)

data class RhythmIssue(
    val systemIndex: Int,
    val measureNumber: Int,
    val staffIndex: Int,
    val observedBeats: Double,
    val expectedBeats: Double,
) {
    fun message(): String =
        "Mesure $measureNumber (${if (staffIndex < 0) "portées non délimitées" else "portée ${staffIndex + 1}"}) : ${format(observedBeats)} temps lus / ${format(expectedBeats)} attendus"

    private fun format(beats: Double): String =
        if (abs(beats - beats.toInt().toDouble()) < 0.001) beats.toInt().toString()
        else "%.2f".format(java.util.Locale.US, beats)
}

data class RhythmAnalysis(
    val notes: List<TimedNote>,
    val issues: List<RhythmIssue>,
    val measures: Int,
) {
    val valid: Boolean get() = issues.isEmpty()
}

/**
 * Keeps left and right hand as separate voices, but aligns both hands on each
 * *barline*, not at arbitrary ends of systems. No false claim of "4/4" is made:
 * the meter must come from a recognized time signature or user selection.
 *
 * Important: a rhythm mismatch is REPORTED, never hidden by inventing notes,
 * elongating short notes or silently shortening the bass.
 */
object StaffTimeline {
    private data class Group(val x: Float, val events: List<StaffEvent>)

    /** Backward-compatible path when barlines are not reliably detected. */
    fun build(events: List<StaffEvent>): List<TimedNote> {
        if (events.isEmpty()) return emptyList()
        val result = mutableListOf<TimedNote>()
        var systemOffset = 0.0
        events.groupBy { it.systemIndex }.toSortedMap().forEach { (_, system) ->
            var systemLength = 0.0
            system.groupBy { it.staffIndex }.forEach { (_, staffEvents) ->
                var onset = 0.0
                groups(staffEvents).forEach { group ->
                    for (head in group.events.distinctBy { it.midi }) {
                        if (head.midi >= 21) {
                            result += TimedNote(head.midi, systemOffset + onset, head.beats)
                        }
                    }
                    onset += group.events.maxOf { it.beats }
                }
                systemLength = max(systemLength, onset)
            }
            systemOffset += systemLength
        }
        return result.sortedWith(compareBy<TimedNote> { it.startBeats }.thenBy { it.midi })
    }

    /**
     * [systemBarlines] contains the X-coordinates of vertical measure boundaries,
     * including the left and right edges. If unreliable, do not silently guess.
     */
    fun analyze(
        events: List<StaffEvent>,
        systemBarlines: Map<Int, List<Float>>,
        meter: ScoreMeter,
    ): RhythmAnalysis {
        if (events.isEmpty()) return RhythmAnalysis(emptyList(), emptyList(), 0)
        val notes = mutableListOf<TimedNote>()
        val issues = mutableListOf<RhythmIssue>()
        var measureCounter = 0
        var offset = 0.0

        events.groupBy { it.systemIndex }.toSortedMap().forEach { (systemIndex, systemEvents) ->
            val minGap = (systemEvents.firstOrNull()?.staffSpacing ?: 12f) * 1.8f
            val bars = systemBarlines[systemIndex].orEmpty().sorted()
                .fold(mutableListOf<Float>()) { accepted, bar ->
                    if (accepted.isEmpty() || bar - accepted.last() > minGap) accepted.add(bar)
                    accepted
                }
            if (bars.size < 2) {
                // No validated barlines: keep the old timing as an explicitly
                // approximate fallback, with a diagnostic for each staff.
                val duration = systemEvents.groupBy { it.staffIndex }
                    .values.maxOf { staff -> groups(staff).sumOf { group -> group.events.maxOf { it.beats } } }
                val local = build(systemEvents.map { it.copy(systemIndex = 0) })
                notes += local.map { it.copy(startBeats = it.startBeats + offset) }
                measureCounter += 1
                issues += RhythmIssue(systemIndex, measureCounter, -1, duration, meter.quarterBeats)
                offset += duration
                return@forEach
            }

            val staffIds = systemEvents.map { it.staffIndex }.distinct()
            for (i in 0 until bars.size - 1) {
                val measureLeft = bars[i]
                val measureRight = bars[i + 1]
                measureCounter += 1
                for (staffIndex in staffIds) {
                    val inBar = systemEvents.filter {
                        it.staffIndex == staffIndex &&
                            it.x >= measureLeft - 2f && it.x < measureRight - 2f
                    }
                    if (inBar.isEmpty()) continue // may be a whole-measure rest
                    var cursor = 0.0
                    groups(inBar).forEach { group ->
                        group.events.distinctBy { it.midi }.forEach { head ->
                            if (head.midi >= 21) {
                                notes += TimedNote(
                                    midi = head.midi,
                                    startBeats = offset + cursor,
                                    durationBeats = head.beats.coerceIn(0.125, 16.0),
                                )
                            }
                        }
                        cursor += group.events.maxOf { it.beats }.coerceIn(0.125, 16.0)
                    }
                    if (abs(cursor - meter.quarterBeats) > 0.12) {
                        issues += RhythmIssue(
                            systemIndex, measureCounter, staffIndex, cursor, meter.quarterBeats,
                        )
                    }
                }
                offset += meter.quarterBeats
            }
        }
        return RhythmAnalysis(
            notes.sortedWith(compareBy<TimedNote> { it.startBeats }.thenBy { it.midi }),
            issues, measureCounter,
        )
    }

    private fun groups(events: List<StaffEvent>): List<Group> {
        if (events.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<StaffEvent>>()
        events.sortedBy { it.x }.forEach { event ->
            val last = groups.lastOrNull()
            val tolerance = max(3f, event.staffSpacing * 0.78f)
            val x = last?.map { it.x }?.average()?.toFloat()
            if (x != null && abs(event.x - x) <= tolerance) {
                last += event
            } else groups += mutableListOf(event)
        }
        return groups.map { Group(it.map { n -> n.x }.average().toFloat(), it) }
    }
}
