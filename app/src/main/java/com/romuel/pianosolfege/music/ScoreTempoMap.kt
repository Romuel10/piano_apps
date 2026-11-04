package com.romuel.pianosolfege.music

/** A MusicXML tempo marking at a quarter-note beat (absolute score position). */
data class TempoChange(val beat: Double, val bpm: Int)

/**
 * Exact, piecewise integration of quarter-note beat positions to seconds.
 * The tempo control scales all MusicXML markings consistently, instead of
 * deleting changes such as ritardando/accelerando expressed by metronome marks.
 */
class ScoreTempoMap(
    playbackBpm: Int,
    originalBpm: Int = playbackBpm,
    changes: List<TempoChange> = emptyList(),
) {
    private val baseline = playbackBpm.coerceIn(30, 300).toDouble()
    private val factor = baseline / originalBpm.coerceIn(30, 300)
    private val segments: List<Pair<Double, Double>>

    init {
        val rows = mutableListOf(0.0 to baseline)
        changes.filter { it.beat >= 0 && it.bpm in 20..400 && it.beat.isFinite() }
            .sortedBy { it.beat }
            .forEach {
                val newSpeed = (it.bpm * factor).coerceIn(20.0, 400.0)
                if (it.beat == rows.last().first) {
                    rows[rows.lastIndex] = it.beat to newSpeed
                } else {
                    rows += it.beat to newSpeed
                }
            }
        segments = rows
    }

    fun secondsAtBeat(beat: Double): Double {
        require(beat >= 0 && beat.isFinite())
        var elapsed = 0.0
        var lastBeat = 0.0
        var activeBpm = segments.first().second
        for (index in 1 until segments.size) {
            val (at, nextBpm) = segments[index]
            if (at > beat) break
            elapsed += (at - lastBeat) * 60.0 / activeBpm
            lastBeat = at
            activeBpm = nextBpm
        }
        return elapsed + (beat - lastBeat) * 60.0 / activeBpm
    }
}
