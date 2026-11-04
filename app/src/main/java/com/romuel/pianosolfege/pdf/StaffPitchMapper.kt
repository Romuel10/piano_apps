package com.romuel.pianosolfege.pdf

import com.romuel.pianosolfege.music.NoteParser

/**
 * Staff half-steps count diatonic positions from the bottom line.
 * Clef of G: E4 at step 0. Clef of F: G2 at step 0.
 * Accidentals/key signatures require a separate symbol recognizer;
 * never infer a sharp from a note's vertical position.
 */
internal object StaffPitchMapper {
    private val naturals = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    fun treble(step: Int): Int = midiForStep(4, 2, step)
    fun bass(step: Int): Int = midiForStep(2, 4, step)
    fun trebleName(step: Int): String = NoteParser.labelForMidi(treble(step))
    fun bassName(step: Int): String = NoteParser.labelForMidi(bass(step))

    private fun midiForStep(baseOctave: Int, baseLetter: Int, step: Int): Int {
        val diatonic = baseOctave * 7 + baseLetter + step
        val octave = Math.floorDiv(diatonic, 7)
        val letter = Math.floorMod(diatonic, 7)
        return (octave + 1) * 12 + naturals[letter]
    }
}
