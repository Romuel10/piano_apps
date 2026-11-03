package com.romuel.pianosolfege.music

/**
 * Conservative OCR normalization of printed musical markings.
 * A detected tempo is not a substitute for reading each note duration.
 */
object ScoreMarkings {
    private val bpmRegex = Regex("""(?i)(?:tempo\s*[:=]?\s*)?(\d{2,3})\s*bpm""")
    private val tempoLabel = Regex("""(?i)\b(?:tempo|metronome)\s*[:=]\s*(\d{2,3})""")
    // OCR of the printed quarter-note sign can look like "/=100".
    private val quarterEquals = Regex(
        """(?im)^\s*(?:♩|♪|♬|♫|q|j|[^\p{L}\p{N}\s]{1,3})?\s*=\s*(\d{2,3})\s*$""",
    )
    private val inlineQuarterEquals = Regex(
        """(?i)(?:♩|♪|♬|♫|q)\s*=\s*(\d{2,3})(?!\d)""",
    )
    private val meterRatio = Regex(
        """(?m)(?:^|\s)([2-9]|1[0-2])\s*[/⁄]\s*(2|4|8|16)(?:\s|$)""",
    )
    private val stackedMeter = Regex("""(?m)^\s*([2-9])\s*\n\s*(2|4|8|16)\s*$""")

    fun detectTempo(text: String): Int? {
        val match = bpmRegex.find(text) ?: tempoLabel.find(text)
            ?: quarterEquals.find(text) ?: inlineQuarterEquals.find(text)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.takeIf { it in 40..220 }
    }

    fun detectMeter(text: String): ScoreMeter? {
        val match = meterRatio.find(text) ?: stackedMeter.find(text) ?: return null
        val top = match.groupValues[1].toIntOrNull() ?: return null
        val bottom = match.groupValues[2].toIntOrNull() ?: return null
        return runCatching { ScoreMeter(top, bottom) }.getOrNull()
    }
}
