package com.romuel.pianosolfege.music

data class PianoNote(
    val label: String,
    val midis: List<Int>,
    val beats: Float = 1f,
) {
    val midi: Int? get() = midis.firstOrNull()
    val isRest: Boolean get() = midis.isEmpty()
    val isChord: Boolean get() = midis.size > 1
}

data class ParseResult(
    val notes: List<PianoNote>,
    val errors: List<String>,
)
