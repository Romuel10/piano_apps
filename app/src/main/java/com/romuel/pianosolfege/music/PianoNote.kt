package com.romuel.pianosolfege.music

data class PianoNote(
    val label: String,
    val midi: Int?,
    val beats: Float = 1f,
) {
    val isRest: Boolean get() = midi == null
}

data class ParseResult(
    val notes: List<PianoNote>,
    val errors: List<String>,
)
