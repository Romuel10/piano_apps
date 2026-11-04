package com.romuel.pianosolfege.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolfegeInputTest {
    @Test fun acceptsSimpleDoReMiWithoutOctave() {
        val result = NoteParser.parse("Do Ré Mi Fa Sol La Si Do")
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 71, 60), result.notes.map { it.midis.single() })
    }

    @Test fun acceptsOneSolfeggioNoteAlone() {
        val result = NoteParser.parse("Ré")
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(62, result.notes.single().midis.single())
    }

    @Test fun understandsInternationalNotationAndAccidentals() {
        val result = NoteParser.parse("C4 D E F#4 Sib3 Fa♯4 Ré♭5")
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(listOf(60, 62, 64, 66, 70, 66, 73), result.notes.map { it.midis.single() })
    }

    @Test fun notesWithoutOctavesRetainDurationsAndChords() {
        val result = NoteParser.parse("Do:0.25 Ré:0.5 [Do Mi Sol]:2 Pause:1")
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(4, result.notes.size)
        assertEquals(0.25f, result.notes[0].beats, 0.0001f)
        assertEquals(0.5f, result.notes[1].beats, 0.0001f)
        assertEquals(listOf(60, 64, 67), result.notes[2].midis)
    }
}
