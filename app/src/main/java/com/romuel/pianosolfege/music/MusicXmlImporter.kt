package com.romuel.pianosolfege.music

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Notes with independent start/duration: unlike a linear solfège string, voices overlap. */
data class TimedNote(val midi: Int, val startBeats: Double, val durationBeats: Double)

data class ImportedScore(
    val notes: List<TimedNote>,
    val bpm: Int,
    val title: String,
    val editablePreview: String,
    val warnings: List<String>,
)

/**
 * Imports standard MusicXML and compressed MXL. This is structured music data,
 * not OCR: pitch, accidentals, voices, tuplets and ties are preserved.
 */
object MusicXmlImporter {
    private data class RawNote(
        val midi: Int,
        val start: Double,
        var duration: Double,
        val voiceKey: String,
        val tieStart: Boolean,
        val tieStop: Boolean,
    )

    suspend fun read(context: Context, uri: Uri): ImportedScore = withContext(Dispatchers.IO) {
        val data = context.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_XML_BYTES) }
            ?: error("Impossible d'ouvrir le fichier MusicXML.")
        parseBytes(data)
    }

    suspend fun readBytes(data: ByteArray): ImportedScore =
        withContext(Dispatchers.Default) { parseBytes(data) }

    /** Also used by JVM tests with in-memory MusicXML fixtures. */
    internal fun parseBytes(data: ByteArray): ImportedScore {
        val xmlData = if (data.size >= 2 && data[0] == 0x50.toByte() && data[1] == 0x4B.toByte()) {
            extractMxl(data)
        } else data

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            setExpandEntityReferences(false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xmlData))
        val root = document.documentElement
        require(root.tagName.substringAfter(':') == "score-partwise") {
            "Ce fichier n'est pas une partition MusicXML score-partwise."
        }

        val title = root.child("work")?.childText("work-title")
            ?: root.childText("movement-title")
            ?: "Partition importée"
        val parts = root.children("part")
        require(parts.isNotEmpty()) { "Aucune partie musicale n'a été trouvée." }

        val raw = mutableListOf<RawNote>()
        val warnings = linkedSetOf<String>()
        var bpm = 100

        parts.forEachIndexed { partIndex, part ->
            var measureStart = 0.0
            var divisions = 1.0
            var expectedMeasureBeats = 4.0
            val ties = mutableMapOf<String, RawNote>()

            part.children("measure").forEachIndexed { measureIndex, measure ->
                var cursor = 0.0
                var lastChordStart = 0.0
                var furthest = 0.0

                measure.children().forEach { element ->
                    when (element.tagName.substringAfter(':')) {
                        "attributes" -> {
                            element.childText("divisions")?.toDoubleOrNull()?.takeIf { it > 0 }
                                ?.let { divisions = it }
                            element.child("time")?.let { time ->
                                val beats = time.childText("beats")?.toDoubleOrNull()
                                val beatType = time.childText("beat-type")?.toDoubleOrNull()
                                if (beats != null && beatType != null && beatType > 0) {
                                    expectedMeasureBeats = beats * 4.0 / beatType
                                }
                            }
                        }
                        "direction" -> {
                            val fromSound = element.child("sound")?.getAttribute("tempo")
                                ?.toDoubleOrNull()
                            val fromMetronome = element.child("direction-type")
                                ?.child("metronome")?.childText("per-minute")?.toDoubleOrNull()
                            (fromSound ?: fromMetronome)?.takeIf { it in 30.0..300.0 }?.let {
                                bpm = it.roundToInt().coerceIn(40, 220)
                            }
                        }
                        "backup" -> {
                            val ticks = element.childText("duration")?.toDoubleOrNull() ?: 0.0
                            cursor = (cursor - ticks / divisions).coerceAtLeast(0.0)
                        }
                        "forward" -> {
                            val ticks = element.childText("duration")?.toDoubleOrNull() ?: 0.0
                            cursor += ticks / divisions
                            furthest = max(furthest, cursor)
                        }
                        "note" -> {
                            val chord = element.child("chord") != null
                            val grace = element.child("grace") != null
                            val units = element.childText("duration")?.toDoubleOrNull()
                            val length = if (grace) 0.25 else {
                                (units?.div(divisions) ?: lengthFromType(element)).coerceIn(0.0625, 32.0)
                            }
                            val at = if (chord) lastChordStart else cursor
                            if (!chord) {
                                lastChordStart = at
                                cursor += length
                            }
                            furthest = max(furthest, at + length)

                            if (element.child("rest") == null) {
                                val pitch = element.child("pitch")
                                val midi = pitch?.let(::pitchMidi)
                                if (midi != null && midi in 21..108) {
                                    val voice = element.childText("voice") ?: "1"
                                    val key = "$partIndex:$voice:$midi"
                                    val tiesHere = element.children("tie").map { it.getAttribute("type") }
                                    val startTie = "start" in tiesHere
                                    val stopTie = "stop" in tiesHere
                                    val position = measureStart + at
                                    val previous = if (stopTie) ties[key] else null

                                    if (previous != null && abs(previous.start + previous.duration - position) < 0.08) {
                                        previous.duration = position + length - previous.start
                                        if (!startTie) ties.remove(key)
                                    } else {
                                        val note = RawNote(midi, position, length, key, startTie, stopTie)
                                        raw.add(note)
                                        if (startTie) ties[key] = note
                                    }
                                }
                            }
                        }
                        "barline" -> {
                            if (element.child("repeat") != null) warnings +=
                                "Les reprises MusicXML ne sont pas encore déroulées automatiquement."
                        }
                    }
                }
                // Keep both hands on the same 4/4 (or other) measure grid.
                val pickup = measureIndex == 0 && measure.getAttribute("implicit") == "yes"
                measureStart += if (pickup) furthest else max(expectedMeasureBeats, furthest)
            }
        }

        val notes = raw.map { TimedNote(it.midi, it.start, it.duration) }
            .filter { it.durationBeats > 0 && it.startBeats >= 0 }
            .sortedWith(compareBy<TimedNote> { it.startBeats }.thenBy { it.midi })
        require(notes.isNotEmpty()) { "Ce MusicXML ne contient aucune note de piano exploitable." }
        require(notes.size <= 15_000) { "Partition trop volumineuse pour le lecteur mobile." }
        return ImportedScore(
            notes = notes,
            bpm = bpm,
            title = title,
            editablePreview = preview(notes),
            warnings = warnings.toList(),
        )
    }

    private fun pitchMidi(pitch: Element): Int? {
        val index = when (pitch.childText("step")?.uppercase()) {
            "C" -> 0; "D" -> 2; "E" -> 4; "F" -> 5
            "G" -> 7; "A" -> 9; "B" -> 11; else -> return null
        }
        val octave = pitch.childText("octave")?.toIntOrNull() ?: return null
        val alter = pitch.childText("alter")?.toDoubleOrNull()?.roundToInt() ?: 0
        return (octave + 1) * 12 + index + alter
    }

    private fun lengthFromType(note: Element): Double {
        var duration = when (note.childText("type")) {
            "maxima" -> 32.0; "long" -> 16.0; "breve" -> 8.0
            "whole" -> 4.0; "half" -> 2.0; "quarter" -> 1.0
            "eighth" -> 0.5; "16th" -> 0.25; "32nd" -> 0.125
            else -> 1.0
        }
        var extra = duration / 2.0
        repeat(note.children("dot").size) {
            duration += extra
            extra /= 2.0
        }
        val time = note.child("time-modification")
        val actual = time?.childText("actual-notes")?.toDoubleOrNull()
        val normal = time?.childText("normal-notes")?.toDoubleOrNull()
        if (actual != null && normal != null && actual > 0) duration *= normal / actual
        return duration
    }

    /**
     * An editable approximation: the retained TimedNote score, NOT this text,
     * is used for exact polyphonic playback until the editor is changed.
     */
    internal fun preview(notes: List<TimedNote>): String {
        val groups = notes.groupBy { (it.startBeats * 1000).roundToInt() }.toSortedMap()
        val keys = groups.keys.toList()
        val out = mutableListOf<String>()
        var lastEnd = 0.0
        keys.forEachIndexed { index, key ->
            val start = key / 1000.0
            if (start > lastEnd + 0.03) out += "Pause:${format(start - lastEnd)}"
            val chord = groups.getValue(key).map { it.midi }.distinct()
            val untilNext = keys.getOrNull(index + 1)?.let { it / 1000.0 - start }
            val duration = minOf(
                groups.getValue(key).minOf { it.durationBeats },
                untilNext ?: 32.0,
            ).coerceAtLeast(0.0625)
            out += if (chord.size == 1) {
                "${NoteParser.labelForMidi(chord.first())}:${format(duration)}"
            } else {
                "[${chord.joinToString(" ") { NoteParser.labelForMidi(it) }}]:${format(duration)}"
            }
            lastEnd = start + duration
        }
        return out.chunked(9).joinToString("\n") { it.joinToString(" ") }
    }

    private fun format(value: Double): String =
        "%.3f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')

    private fun extractMxl(compressed: ByteArray): ByteArray {
        val candidates = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(compressed)).use { zip ->
            var entry = zip.nextEntry
            var count = 0
            while (entry != null && count++ < 100) {
                val name = entry.name.replace('\\', '/')
                if (!entry.isDirectory && !name.startsWith("META-INF/", ignoreCase = true) &&
                    (name.endsWith(".musicxml", true) || name.endsWith(".xml", true))) {
                    candidates += name to readLimited(zip, MAX_XML_BYTES)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return candidates.firstOrNull { it.first.endsWith(".musicxml", true) }?.second
            ?: candidates.firstOrNull()?.second
            ?: error("Archive MXL sans partition MusicXML.")
    }

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            require(output.size() + size <= maxBytes) { "Fichier trop grand (limite 12 Mo)." }
            output.write(buffer, 0, size)
        }
        return output.toByteArray()
    }

    private fun Element.child(tag: String): Element? =
        children().firstOrNull { it.tagName.substringAfter(':') == tag }

    private fun Element.children(tag: String? = null): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val child = nodes.item(index)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (tag == null || element.tagName.substringAfter(':') == tag) result += element
            }
        }
        return result
    }

    private fun Element.childText(tag: String): String? =
        child(tag)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private const val MAX_XML_BYTES = 12 * 1024 * 1024
}
