package com.romuel.pianosolfege.music

/**
 * Common MusicXML barline repeats and first/second endings.
 * Da Capo, Dal Segno and nested repeats require a full road-map interpreter;
 * they are explicitly reported as limitations rather than silently ignored.
 */
internal data class MeasureMarkings(
    val forwardRepeat: Boolean = false,
    val backwardRepeatTimes: Int? = null,
    val endingPasses: Set<Int> = emptySet(),
)

internal data class RepeatPlan(
    val indices: List<Int>,
    val warnings: List<String>,
)

internal object RepeatPlanner {
    fun expand(measures: List<MeasureMarkings>): RepeatPlan {
        if (measures.isEmpty()) return RepeatPlan(emptyList(), emptyList())
        val warnings = mutableListOf<String>()
        val result = mutableListOf<Int>()
        val counts = mutableMapOf<Int, Int>()
        var index = 0
        var begin = 0
        var pass = 1
        var budget = 0

        while (index in measures.indices && budget++ < 5000 && result.size < 5000) {
            val marker = measures[index]
            if (marker.forwardRepeat && pass == 1) begin = index

            if (marker.endingPasses.isNotEmpty() && pass !in marker.endingPasses) {
                // Skip a first ending on the second pass, including its backward barline.
                index++
                continue
            }

            result += index
            if (marker.backwardRepeatTimes != null) {
                val times = marker.backwardRepeatTimes
                if (times !in 1..4) {
                    warnings += "Nombre de reprises inhabituel : limité à 4 passages."
                }
                val passes = times.coerceIn(1, 4)
                val performed = counts[index] ?: 1
                if (performed < passes) {
                    counts[index] = performed + 1
                    pass = performed + 1
                    index = begin
                    continue
                }
                // Preserve the pass number while entering the second ending.
                if (measures.getOrNull(index + 1)?.endingPasses?.contains(pass) != true) {
                    pass = 1
                }
                begin = index + 1
            } else if (marker.endingPasses.isNotEmpty() &&
                measures.getOrNull(index + 1)?.endingPasses?.isEmpty() == true) {
                pass = 1
            }
            index++
        }

        if (budget >= 5000 || result.size >= 5000) {
            warnings += "Reprises trop nombreuses : transcription interrompue."
        }
        return RepeatPlan(result, warnings.distinct())
    }
}
