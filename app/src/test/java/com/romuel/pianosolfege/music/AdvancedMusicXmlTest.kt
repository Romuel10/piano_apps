package com.romuel.pianosolfege.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedMusicXmlTest {
    @Test fun repeatedBarlinesArePlayedTwice() {
        val xml = """
            <score-partwise version="4.0">
              <part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>
              <part id="P1">
                <measure number="1">
                  <attributes><divisions>1</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes>
                  <barline location="left"><repeat direction="forward"/></barline>
                  <note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration></note>
                </measure>
                <measure number="2">
                  <note><pitch><step>D</step><octave>4</octave></pitch><duration>4</duration></note>
                  <barline location="right"><repeat direction="backward"/></barline>
                </measure>
                <measure number="3">
                  <note><pitch><step>E</step><octave>4</octave></pitch><duration>4</duration></note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent()
        val score = MusicXmlImporter.parseBytes(xml.toByteArray())
        assertEquals(listOf(60, 62, 60, 62, 64), score.notes.map { it.midi })
        assertEquals(listOf(0.0, 4.0, 8.0, 12.0, 16.0), score.notes.map { it.startBeats })
    }

    @Test fun metronomeMarkingsArePreservedAtTheirPositions() {
        val xml = """
            <score-partwise version="4.0">
              <part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>
              <part id="P1">
                <measure number="1">
                  <attributes><divisions>1</divisions></attributes>
                  <direction><sound tempo="100"/></direction>
                  <note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration></note>
                </measure>
                <measure number="2">
                  <direction><sound tempo="200"/></direction>
                  <note><pitch><step>D</step><octave>4</octave></pitch><duration>4</duration></note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent()
        val score = MusicXmlImporter.parseBytes(xml.toByteArray())
        assertEquals(100, score.bpm)
        assertEquals(listOf(TempoChange(0.0, 100), TempoChange(4.0, 200)), score.tempoChanges)
        assertEquals(3.6, ScoreTempoMap(score.bpm, score.bpm, score.tempoChanges)
            .secondsAtBeat(8.0), 0.001)
    }

    @Test fun concurrentVoicesWithTupletDurationRemainIndependent() {
        val xml = """
            <score-partwise>
              <part id="P1">
                <measure number="1">
                  <attributes><divisions>12</divisions></attributes>
                  <note><pitch><step>C</step><octave>4</octave></pitch>
                    <duration>8</duration><voice>1</voice>
                    <type>eighth</type><time-modification>
                      <actual-notes>3</actual-notes><normal-notes>2</normal-notes>
                    </time-modification>
                  </note>
                  <backup><duration>8</duration></backup>
                  <note><pitch><step>C</step><octave>3</octave></pitch>
                    <duration>24</duration><voice>2</voice><type>half</type>
                  </note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent()
        val score = MusicXmlImporter.parseBytes(xml.toByteArray())
        assertEquals(2, score.notes.size)
        assertTrue(score.notes.any { it.midi == 60 && it.durationBeats < 1 })
        assertTrue(score.notes.any { it.midi == 48 && it.durationBeats == 2.0 })
    }
}
