package com.romuel.pianosolfege.pdf

import android.graphics.Bitmap
import com.romuel.pianosolfege.music.NoteParser
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lightweight, fully on-device OMR for clean printed piano scores.
 *
 * This is deliberately conservative: it detects five-line staves, searches
 * for note heads on diatonic staff positions, estimates a basic duration, and
 * merges vertically aligned notes into chords. It is not intended to replace
 * a full MusicXML-grade OMR engine, but it gives the app a useful offline path
 * for ordinary printed sheet music without shipping a very large ML model.
 */
internal object SheetMusicOmr {
    data class Result(
        val playableText: String,
        val detectedNotes: Int,
        val detectedEvents: Int,
        val detectedStaffs: Int,
        val confidence: Float,
        val warnings: List<String>,
    )

    private data class Staff(
        val lines: IntArray,
    ) {
        val top: Int get() = lines.first()
        val bottom: Int get() = lines.last()
        val spacing: Float
            get() {
                if (lines.size < 2) return 0f
                var total = 0f
                for (i in 0 until lines.lastIndex) {
                    total += (lines[i + 1] - lines[i]).toFloat()
                }
                return total / (lines.size - 1).toFloat()
            }
    }

    private data class Candidate(
        val x: Float,
        val y: Float,
        val midi: Int,
        val beats: Float,
        val score: Float,
        val staffIndex: Int,
    )

    private data class SystemGroup(
        val staffIndices: List<Int>,
    )

    private enum class Clef { TREBLE, BASS }

    fun analyze(bitmap: Bitmap): Result {
        if (bitmap.width < 160 || bitmap.height < 160) return emptyResult()

        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val ink = ByteArray(argb.size)

        for (i in argb.indices) {
            val color = argb[i]
            val r = color shr 16 and 0xFF
            val g = color shr 8 and 0xFF
            val b = color and 0xFF
            val luminance = (299 * r + 587 * g + 114 * b) / 1000
            if (luminance < INK_THRESHOLD) ink[i] = 1
        }

        val staffs = detectStaffs(ink, width, height)
        if (staffs.isEmpty()) {
            return emptyResult(warnings = listOf("Aucune portée de cinq lignes n'a été détectée."))
        }

        val systems = groupSystems(staffs)
        val output = mutableListOf<String>()
        var noteCount = 0
        var scoreSum = 0f
        var scoreItems = 0
        val warnings = mutableListOf<String>()

        systems.forEach { system ->
            val systemCandidates = mutableListOf<Candidate>()
            val twoStaffPianoSystem = system.staffIndices.size == 2

            system.staffIndices.forEachIndexed { position, staffIndex ->
                val staff = staffs[staffIndex]
                val clef = if (twoStaffPianoSystem && position == 1) Clef.BASS else Clef.TREBLE
                val previousStaff = system.staffIndices.getOrNull(position - 1)?.let { staffs[it] }
                val nextStaff = system.staffIndices.getOrNull(position + 1)?.let { staffs[it] }
                val regionTop = previousStaff?.let { (it.bottom + staff.top) / 2 }
                    ?: max(0, (staff.top - staff.spacing * 7.5f).roundToInt())
                val regionBottom = nextStaff?.let { (staff.bottom + it.top) / 2 }
                    ?: min(height - 1, (staff.bottom + staff.spacing * 4.5f).roundToInt())

                systemCandidates += detectNotes(
                    ink = ink,
                    width = width,
                    height = height,
                    staff = staff,
                    staffIndex = staffIndex,
                    clef = clef,
                    regionTop = regionTop,
                    regionBottom = regionBottom,
                )
            }

            if (systemCandidates.isEmpty()) return@forEach

            val spacing = system.staffIndices
                .map { staffs[it].spacing }
                .average()
                .toFloat()
            val events = clusterByOnset(systemCandidates, max(5f, spacing * 0.95f))

            events.forEach { chord ->
                val midis = chord.map { it.midi }.distinct().sorted()
                if (midis.isEmpty()) return@forEach

                val beats = chord.minOfOrNull { it.beats } ?: 1f
                val labels = midis.map(NoteParser::labelForMidi)
                output += if (labels.size == 1) {
                    "${labels.first()}:${formatBeats(beats)}"
                } else {
                    "[${labels.joinToString(" ")}]:${formatBeats(beats)}"
                }

                noteCount += midis.size
                chord.forEach {
                    scoreSum += it.score
                    scoreItems += 1
                }
            }
        }

        if (output.isEmpty()) {
            warnings += "Des portées ont été trouvées, mais aucune tête de note suffisamment nette n'a été reconnue."
        }
        if (staffs.size % 2 == 1 && staffs.size > 1) {
            warnings += "Une portée isolée a été interprétée en clé de sol."
        }
        warnings += "Les altérations, signatures rythmiques complexes, triolets, liaisons et reprises ne sont pas encore interprétés par l'OMR local."

        val confidence = if (scoreItems == 0) 0f else (scoreSum / scoreItems).coerceIn(0f, 1f)
        return Result(
            playableText = output.joinToString(" "),
            detectedNotes = noteCount,
            detectedEvents = output.size,
            detectedStaffs = staffs.size,
            confidence = confidence,
            warnings = warnings,
        )
    }

    private fun detectStaffs(ink: ByteArray, width: Int, height: Int): List<Staff> {
        val xStart = (width * 0.04f).roundToInt().coerceIn(0, width - 1)
        val xEnd = (width * 0.96f).roundToInt().coerceIn(xStart + 1, width)
        val xStep = if (width > 1400) 2 else 1
        val samplesPerRow = max(1, (xEnd - xStart) / xStep)
        val minimum = max(10, (samplesPerRow * STAFF_LINE_COVERAGE).roundToInt())

        val centers = mutableListOf<Int>()
        var runStart = -1
        var bestRow = -1
        var bestCount = 0

        fun finishRun() {
            if (runStart >= 0 && bestRow >= 0) centers += bestRow
            runStart = -1
            bestRow = -1
            bestCount = 0
        }

        for (y in 0 until height) {
            var count = 0
            var x = xStart
            val row = y * width
            while (x < xEnd) {
                if (ink[row + x].toInt() != 0) count += 1
                x += xStep
            }

            if (count >= minimum) {
                if (runStart < 0) runStart = y
                if (count > bestCount) {
                    bestCount = count
                    bestRow = y
                }
            } else {
                finishRun()
            }
        }
        finishRun()

        if (centers.size < 5) return emptyList()

        val result = mutableListOf<Staff>()
        var i = 0
        while (i <= centers.size - 5) {
            val lines = IntArray(5) { centers[i + it] }
            val gaps = IntArray(4) { lines[it + 1] - lines[it] }
            val average = gaps.average().toFloat()
            val tolerance = max(2f, average * 0.28f)
            val consistent = average in MIN_STAFF_SPACING..MAX_STAFF_SPACING &&
                gaps.all { abs(it - average) <= tolerance }

            if (consistent) {
                val overlapsPrevious = result.lastOrNull()?.let { previous ->
                    abs(previous.top - lines.first()) < average * 2f
                } ?: false
                if (!overlapsPrevious) result += Staff(lines)
                i += 5
            } else {
                i += 1
            }
        }
        return result
    }

    private fun groupSystems(staffs: List<Staff>): List<SystemGroup> {
        val result = mutableListOf<SystemGroup>()
        var index = 0
        while (index < staffs.size) {
            if (index + 1 < staffs.size) {
                val upper = staffs[index]
                val lower = staffs[index + 1]
                val spacing = (upper.spacing + lower.spacing) / 2f
                val gap = lower.top - upper.bottom
                if (gap > spacing * 1.2f && gap < spacing * 6.5f) {
                    result += SystemGroup(listOf(index, index + 1))
                    index += 2
                    continue
                }
            }
            result += SystemGroup(listOf(index))
            index += 1
        }
        return result
    }

    private fun detectNotes(
        ink: ByteArray,
        width: Int,
        height: Int,
        staff: Staff,
        staffIndex: Int,
        clef: Clef,
        regionTop: Int,
        regionBottom: Int,
    ): List<Candidate> {
        val spacing = staff.spacing
        if (spacing < MIN_STAFF_SPACING || spacing > MAX_STAFF_SPACING) return emptyList()

        val bounds = staffHorizontalBounds(ink, width, height, staff)
        val startX = max(
            bounds.first + (spacing * 4.8f).roundToInt(),
            (width * 0.10f).roundToInt(),
        ).coerceAtMost(width - 2)
        val endX = min(bounds.second - spacing.roundToInt(), (width * 0.98f).roundToInt())
            .coerceAtLeast(startX + 1)
        if (endX <= startX + spacing) return emptyList()

        val xStep = max(2, (spacing * 0.22f).roundToInt())
        val rx = max(3, (spacing * 0.72f).roundToInt())
        val ry = max(2, (spacing * 0.47f).roundToInt())
        val raw = mutableListOf<Candidate>()

        // Bottom line = staff step 0. Positive steps go upward in diatonic half-spaces.
        for (staffStep in MIN_STAFF_STEP..MAX_STAFF_STEP) {
            val targetY = staff.bottom - staffStep * spacing / 2f
            if (targetY < regionTop || targetY > regionBottom || targetY < 2 || targetY >= height - 2) continue

            var x0 = startX
            var previousPrevious = 0f
            var previous = headDensity(ink, width, height, staff, x0, targetY.roundToInt(), rx, ry)
            var previousX = x0
            x0 += xStep

            while (x0 <= endX) {
                val current = headDensity(ink, width, height, staff, x0, targetY.roundToInt(), rx, ry)
                val localPeak = previous >= HEAD_DENSITY_THRESHOLD &&
                    previous >= previousPrevious && previous > current

                if (localPeak) {
                    val y = targetY.roundToInt()
                    val inner = headDensity(
                        ink,
                        width,
                        height,
                        staff,
                        previousX,
                        y,
                        max(2, (spacing * 0.34f).roundToInt()),
                        max(1, (spacing * 0.20f).roundToInt()),
                    )
                    val stem = hasStem(ink, width, height, previousX, y, spacing)
                    val beam = stem && hasBeam(ink, width, height, staff, previousX, y, spacing)
                    val filled = inner >= FILLED_HEAD_THRESHOLD
                    val beats = when {
                        filled && beam -> 0.5f
                        filled -> 1f
                        stem -> 2f
                        else -> 4f
                    }
                    val midi = midiForStaffStep(clef, staffStep)
                    if (midi in 21..108) {
                        raw += Candidate(
                            x = previousX.toFloat(),
                            y = targetY,
                            midi = midi,
                            beats = beats,
                            score = previous.coerceIn(0f, 1f),
                            staffIndex = staffIndex,
                        )
                    }
                }

                previousPrevious = previous
                previous = current
                previousX = x0
                x0 += xStep
            }
        }

        if (raw.isEmpty()) return raw

        // The same head can score on two neighbouring pitch positions. Keep the
        // stronger one unless they are spatially separated enough to be a true second.
        val accepted = mutableListOf<Candidate>()
        raw.sortedByDescending { it.score }.forEach { candidate ->
            val duplicate = accepted.any { other ->
                abs(candidate.x - other.x) < spacing * 0.38f &&
                    abs(candidate.y - other.y) < spacing * 0.56f
            }
            if (!duplicate) accepted += candidate
        }
        return accepted.sortedBy { it.x }
    }

    private fun staffHorizontalBounds(
        ink: ByteArray,
        width: Int,
        height: Int,
        staff: Staff,
    ): Pair<Int, Int> {
        var first = 0
        var last = width - 1
        var foundFirst = false
        val verticalTolerance = max(1, (staff.spacing * 0.08f).roundToInt())

        for (x in 0 until width) {
            var matchingLines = 0
            for (lineY in staff.lines) {
                var found = false
                for (dy in -verticalTolerance..verticalTolerance) {
                    val y = lineY + dy
                    if (y in 0 until height && ink[y * width + x].toInt() != 0) {
                        found = true
                        break
                    }
                }
                if (found) matchingLines += 1
            }
            if (matchingLines >= 3) {
                if (!foundFirst) {
                    first = x
                    foundFirst = true
                }
                last = x
            }
        }
        return first to last
    }

    private fun headDensity(
        ink: ByteArray,
        width: Int,
        height: Int,
        staff: Staff,
        centerX: Int,
        centerY: Int,
        radiusX: Int,
        radiusY: Int,
    ): Float {
        val left = max(0, centerX - radiusX)
        val right = min(width - 1, centerX + radiusX)
        val top = max(0, centerY - radiusY)
        val bottom = min(height - 1, centerY + radiusY)
        val lineMask = max(1, (staff.spacing * 0.08f).roundToInt())

        var dark = 0
        var eligible = 0
        for (y in top..bottom) {
            if (staff.lines.any { abs(y - it) <= lineMask }) continue
            val row = y * width
            for (x in left..right) {
                val nx = (x - centerX).toFloat() / max(1, radiusX)
                val ny = (y - centerY).toFloat() / max(1, radiusY)
                if (nx * nx + ny * ny > 1.08f) continue
                eligible += 1
                if (ink[row + x].toInt() != 0) dark += 1
            }
        }
        return if (eligible == 0) 0f else dark.toFloat() / eligible.toFloat()
    }

    private fun hasStem(
        ink: ByteArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        spacing: Float,
    ): Boolean {
        val halfHead = max(2, (spacing * 0.70f).roundToInt())
        val search = max(4, (spacing * 3.7f).roundToInt())
        val minimumRun = max(4, (spacing * 1.45f).roundToInt())

        val xMin = max(0, centerX - halfHead)
        val xMax = min(width - 1, centerX + halfHead)
        for (x in xMin..xMax) {
            var run = 0
            var best = 0
            val yMin = max(0, centerY - search)
            val yMax = min(height - 1, centerY + search)
            for (y in yMin..yMax) {
                val nearHead = abs(y - centerY) <= spacing * 0.38f
                val dark = !nearHead && ink[y * width + x].toInt() != 0
                if (dark) {
                    run += 1
                    best = max(best, run)
                } else {
                    run = 0
                }
            }
            if (best >= minimumRun) return true
        }
        return false
    }

    private fun hasBeam(
        ink: ByteArray,
        width: Int,
        height: Int,
        staff: Staff,
        centerX: Int,
        centerY: Int,
        spacing: Float,
    ): Boolean {
        val xRadius = max(4, (spacing * 2.4f).roundToInt())
        val yRadius = max(5, (spacing * 3.6f).roundToInt())
        val minimumRun = max(5, (spacing * 1.15f).roundToInt())
        val maximumRun = max(minimumRun + 1, (spacing * 3.4f).roundToInt())
        val lineMask = max(1, (spacing * 0.10f).roundToInt())
        val xMin = max(0, centerX - xRadius)
        val xMax = min(width - 1, centerX + xRadius)
        val yMin = max(0, centerY - yRadius)
        val yMax = min(height - 1, centerY + yRadius)

        for (y in yMin..yMax) {
            if (abs(y - centerY) < spacing * 0.75f) continue
            if (staff.lines.any { abs(y - it) <= lineMask }) continue
            var run = 0
            var best = 0
            val row = y * width
            for (x in xMin..xMax) {
                if (ink[row + x].toInt() != 0) {
                    run += 1
                    best = max(best, run)
                } else {
                    run = 0
                }
            }
            // Very long horizontal runs are much more likely to be a neighbouring
            // staff line than a beam. This avoids turning quarter notes into eighths.
            if (best in minimumRun..maximumRun) return true
        }
        return false
    }

    private fun clusterByOnset(notes: List<Candidate>, tolerance: Float): List<List<Candidate>> {
        if (notes.isEmpty()) return emptyList()
        val sorted = notes.sortedBy { it.x }
        val clusters = mutableListOf<MutableList<Candidate>>()

        sorted.forEach { candidate ->
            val current = clusters.lastOrNull()
            if (current == null) {
                clusters += mutableListOf(candidate)
            } else {
                val meanX = current.map { it.x }.average().toFloat()
                if (abs(candidate.x - meanX) <= tolerance) {
                    current += candidate
                } else {
                    clusters += mutableListOf(candidate)
                }
            }
        }
        return clusters
    }

    private fun midiForStaffStep(clef: Clef, step: Int): Int {
        // C=0, D=1 ... B=6. Treble bottom line = E4; bass bottom line = G2.
        val baseLetter = if (clef == Clef.TREBLE) 2 else 4
        val baseOctave = if (clef == Clef.TREBLE) 4 else 2
        val absoluteDiatonic = baseOctave * 7 + baseLetter + step
        val octave = Math.floorDiv(absoluteDiatonic, 7)
        val letter = Math.floorMod(absoluteDiatonic, 7)
        val semitone = NATURAL_SEMITONES[letter]
        return (octave + 1) * 12 + semitone
    }

    private fun formatBeats(value: Float): String = when {
        abs(value - 0.5f) < 0.01f -> "0.5"
        abs(value - 1f) < 0.01f -> "1"
        abs(value - 2f) < 0.01f -> "2"
        abs(value - 4f) < 0.01f -> "4"
        else -> value.toString()
    }

    private fun emptyResult(warnings: List<String> = emptyList()) = Result(
        playableText = "",
        detectedNotes = 0,
        detectedEvents = 0,
        detectedStaffs = 0,
        confidence = 0f,
        warnings = warnings,
    )

    private val NATURAL_SEMITONES = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    private const val INK_THRESHOLD = 188
    private const val STAFF_LINE_COVERAGE = 0.23f
    private const val MIN_STAFF_SPACING = 5f
    private const val MAX_STAFF_SPACING = 90f
    private const val HEAD_DENSITY_THRESHOLD = 0.17f
    private const val FILLED_HEAD_THRESHOLD = 0.38f
    private const val MIN_STAFF_STEP = -6
    private const val MAX_STAFF_STEP = 14
}
