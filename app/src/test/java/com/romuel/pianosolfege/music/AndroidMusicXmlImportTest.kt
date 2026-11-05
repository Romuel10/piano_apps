package com.romuel.pianosolfege.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Regression for Android's DocumentBuilderFactory.setXIncludeAware(false):
 * that call raises UnsupportedOperationException ("Unknown version 0.0")
 * on the actual Android JAXP implementation.
 *
 * Unlike plain JUnit tests on desktop Java, this runs with Android classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidMusicXmlImportTest {
    private val source = """
        <?xml version="1.0" encoding="utf-8"?>
        <!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 4.0 Partwise//EN"
          "http://www.musicxml.org/dtds/partwise.dtd">
        <score-partwise version="4.0">
          <part-list>
            <score-part id="P1"><part-name>Piano</part-name></score-part>
          </part-list>
          <part id="P1">
            <measure number="1">
              <attributes><divisions>4</divisions><time>
                <beats>4</beats><beat-type>4</beat-type></time></attributes>
              <note><pitch><step>C</step><octave>4</octave></pitch>
                <duration>4</duration><type>quarter</type></note>
              <note><pitch><step>D</step><octave>4</octave></pitch>
                <duration>2</duration><type>eighth</type></note>
              <note><pitch><step>E</step><octave>4</octave></pitch>
                <duration>1</duration><type>16th</type></note>
            </measure>
          </part>
        </score-partwise>
    """.trimIndent()

    @Test fun androidParsesStandardMusicXmlWithPublicDtdSafely() {
        val score = MusicXmlImporter.parseBytes(source.toByteArray())
        assertEquals(listOf(60, 62, 64), score.notes.map { it.midi })
        assertEquals(listOf(1.0, 0.5, 0.25), score.notes.map { it.durationBeats })
        assertTrue(score.title.isNotBlank())
    }

    @Test fun androidParsesCompressedMxlAfterAudiverisConversion() {
        val stream = ByteArrayOutputStream()
        ZipOutputStream(stream).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("""<container/>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("score.musicxml"))
            zip.write(source.toByteArray())
            zip.closeEntry()
        }
        val score = MusicXmlImporter.parseBytes(stream.toByteArray())
        assertEquals(listOf(60, 62, 64), score.notes.map { it.midi })
    }

    @Test fun localEntityDeclarationIsRejectedRatherThanExpanded() {
        val xml = """
            <!DOCTYPE score-partwise [
              <!ENTITY private SYSTEM "file:///sdcard/private.txt">
            ]>
            <score-partwise version="4.0"/>
        """.trimIndent()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            MusicXmlImporter.parseBytes(xml.toByteArray())
        }
        assertTrue(ex.message.orEmpty().contains("entités XML"))
    }

    @Test fun externalDtdIsNeverFetched() {
        val xml = source.replace(
            "http://www.musicxml.org/dtds/partwise.dtd",
            "http://127.0.0.1:9/unreachable-dtd.xml",
        )
        val score = MusicXmlImporter.parseBytes(xml.toByteArray())
        assertEquals(3, score.notes.size)
    }
}
