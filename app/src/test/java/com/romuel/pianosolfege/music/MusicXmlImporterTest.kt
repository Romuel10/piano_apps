package com.romuel.pianosolfege.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MusicXmlImporterTest {
    private val score = """
        <?xml version="1.0" encoding="utf-8"?>
        <score-partwise version="4.0">
          <work><work-title>Piano test</work-title></work>
          <part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>
          <part id="P1">
            <measure number="1">
              <attributes>
                <divisions>100</divisions>
                <time><beats>4</beats><beat-type>4</beat-type></time>
              </attributes>
              <direction><sound tempo="100"/></direction>
              <note><pitch><step>C</step><octave>4</octave></pitch>
                <duration>100</duration><voice>1</voice><type>quarter</type></note>
              <note><pitch><step>D</step><octave>4</octave></pitch>
                <duration>100</duration><voice>1</voice><type>quarter</type></note>
              <backup><duration>200</duration></backup>
              <note><pitch><step>C</step><octave>3</octave></pitch>
                <duration>200</duration><voice>2</voice><type>half</type></note>
            </measure>
            <measure number="2">
              <note><pitch><step>F</step><alter>1</alter><octave>4</octave></pitch>
                <duration>50</duration><voice>1</voice><type>eighth</type></note>
              <note><chord/><pitch><step>A</step><octave>4</octave></pitch>
                <duration>50</duration><voice>1</voice><type>eighth</type></note>
            </measure>
          </part>
        </score-partwise>
    """.trimIndent()

    @Test fun twoHandsRemainSynchronized() {
        val result = MusicXmlImporter.parseBytes(score.toByteArray())
        assertEquals("Piano test", result.title)
        assertEquals(100, result.bpm)
        assertEquals(5, result.notes.size)
        val c3 = result.notes.first { it.midi == 48 }
        assertEquals(0.0, c3.startBeats, 0.001)
        assertEquals(2.0, c3.durationBeats, 0.001)
        assertEquals(1.0, result.notes.first { it.midi == 62 }.startBeats, 0.001)
        assertEquals(4.0, result.notes.first { it.midi == 66 }.startBeats, 0.001)
        assertEquals(4.0, result.notes.first { it.midi == 69 }.startBeats, 0.001)
        assertTrue(result.editablePreview.contains("[Fa#4 La4]"))
    }

    @Test fun compressedMxlIsSupported() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("<container/>".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("score/part.musicxml"))
            zip.write(score.toByteArray())
            zip.closeEntry()
        }
        val result = MusicXmlImporter.parseBytes(bytes.toByteArray())
        assertEquals(5, result.notes.size)
        assertEquals(100, result.bpm)
    }

    @Test fun standardKeySignatureAppliesToUnalteredPitches() {
        val xml = """
            <score-partwise>
              <part id="P1">
                <measure number="1">
                  <attributes><divisions>1</divisions><key><fifths>1</fifths></key></attributes>
                  <note><pitch><step>F</step><octave>4</octave></pitch><duration>1</duration></note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent()
        val result = MusicXmlImporter.parseBytes(xml.toByteArray())
        assertEquals(66, result.notes.single().midi) // F sharp in G major.
    }

    @Test fun tempoMapPreservesChangesAndDottedMetronomeUnits() {
        val xml = """
            <score-partwise>
              <part id="P1">
                <measure number="1">
                  <attributes><divisions>1</divisions></attributes>
                  <direction><sound tempo="100"/></direction>
                  <note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration></note>
                  <direction><direction-type><metronome><beat-unit>eighth</beat-unit>
                    <beat-unit-dot/><per-minute>120</per-minute></metronome></direction-type></direction>
                  <note><pitch><step>D</step><octave>4</octave></pitch><duration>1</duration></note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent()
        val result = MusicXmlImporter.parseBytes(xml.toByteArray())
        assertEquals(100, result.bpm)
        assertEquals(2, result.tempoChanges.size)
        assertEquals(0.0, result.tempoChanges[0].startBeats, 0.001)
        assertEquals(1.0, result.tempoChanges[1].startBeats, 0.001)
        assertEquals(90, result.tempoChanges[1].bpm) // dotted eighth = 0.75 quarter notes.
    }

    @Test fun tiesAcrossMeasuresExtendNotes() {
        val xml = """
            <score-partwise>
              <part id="P1">
                <measure number="1">
                  <attributes><divisions>10</divisions></attributes>
                  <note><pitch><step>G</step><octave>4</octave></pitch>
                    <duration>40</duration><tie type="start"/></note>
                </measure>
                <measure number="2">
                  <note><pitch><step>G</step><octave>4</octave></pitch>
                    <duration>20</duration><tie type="stop"/></note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent()
        val parsed = MusicXmlImporter.parseBytes(xml.toByteArray())
        assertEquals(1, parsed.notes.size)
        assertEquals(6.0, parsed.notes.first().durationBeats, 0.001)
    }
}
