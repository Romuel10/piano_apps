package com.romuel.pianosolfege.pdf

import android.content.Context
import com.romuel.pianosolfege.music.TimedNote
import com.romuel.pianosolfege.music.MusicXmlImporter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

class PdfNoteReader(private val context: Context) {
    enum class RecognitionMode {
        SOLFEGE_TEXT,
        SHEET_MUSIC_OMR,
        NONE,
    }

    data class Result(
        val displayName: String,
        val rawText: String,
        val playableText: String,
        val detectedNotes: Int,
        val pageCount: Int,
        val detectedBpm: Int?,
        val mode: RecognitionMode,
        val detectedStaffs: Int = 0,
        val confidence: Float = 0f,
        val warnings: List<String> = emptyList(),
        val timedNotes: List<TimedNote> = emptyList(),
    )

    suspend fun read(
        uri: Uri,
        onProgress: (page: Int, total: Int) -> Unit = { _, _ -> },
    ): Result = withContext(Dispatchers.IO) {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Impossible d'ouvrir le PDF sélectionné.")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            PdfRenderer(descriptor).use { renderer ->
                val total = renderer.pageCount
                require(total > 0) { "Le PDF ne contient aucune page." }

                val raw = StringBuilder()
                val omrPlayable = StringBuilder()
                val omrWarnings = linkedSetOf<String>()
                var omrNotes = 0
                var omrEvents = 0
                var omrStaffs = 0
                var omrConfidenceWeighted = 0f
                val omrTimed = mutableListOf<TimedNote>()
                var pageStartBeats = 0.0
                val pagesToRead = min(total, MAX_PAGES)

                for (index in 0 until pagesToRead) {
                    onProgress(index + 1, pagesToRead)
                    renderer.openPage(index).use { page ->
                        val bitmap = createReadableBitmap(page.width, page.height)
                        try {
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            val scale = bitmap.width.toFloat() / page.width.toFloat()
                            val matrix = Matrix().apply { setScale(scale, scale) }
                            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            // OCR text remains useful for PDFs that literally contain
                            // Do/Ré/Mi or C/D/E note names and for tempo annotations.
                            val text = recognize(recognizer, bitmap)
                            if (text.isNotBlank()) {
                                if (raw.isNotEmpty()) raw.append('\n')
                                raw.append(text)
                            }

                            // In parallel, inspect the rendered score itself. If real
                            // five-line staves are found, this result takes priority.
                            val omr = SheetMusicOmr.analyze(bitmap)
                            if (omr.playableText.isNotBlank()) {
                                if (omrPlayable.isNotEmpty()) omrPlayable.append(' ')
                                omrPlayable.append(omr.playableText)
                            }
                            if (omr.timedNotes.isNotEmpty()) {
                                omrTimed += omr.timedNotes.map {
                                    it.copy(startBeats = it.startBeats + pageStartBeats)
                                }
                                pageStartBeats = omrTimed.maxOf {
                                    it.startBeats + it.durationBeats
                                }
                            }
                            omrNotes += omr.detectedNotes
                            omrEvents += omr.detectedEvents
                            omrStaffs += omr.detectedStaffs
                            if (omr.detectedEvents > 0) {
                                omrConfidenceWeighted += omr.confidence * omr.detectedEvents
                            }
                            omrWarnings += omr.warnings
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }

                val textResult = SolfegeOcrNormalizer.convert(raw.toString())
                val omrConfidence = if (omrEvents == 0) 0f else omrConfidenceWeighted / omrEvents
                val useSheetOmr = omrStaffs > 0 && omrNotes >= MIN_OMR_NOTES && omrPlayable.isNotBlank()
                val useText = textResult.detectedNotes > 0 && textResult.playableText.isNotBlank()

                when {
                    useSheetOmr -> Result(
                        displayName = displayName(uri),
                        rawText = raw.toString(),
                        playableText = if (omrTimed.isNotEmpty()) {
                            MusicXmlImporter.preview(omrTimed)
                        } else omrPlayable.toString(),
                        detectedNotes = omrNotes,
                        pageCount = pagesToRead,
                        detectedBpm = textResult.bpm,
                        mode = RecognitionMode.SHEET_MUSIC_OMR,
                        detectedStaffs = omrStaffs,
                        confidence = omrConfidence,
                        warnings = omrWarnings.toList(),
                        timedNotes = omrTimed,
                    )

                    useText -> Result(
                        displayName = displayName(uri),
                        rawText = raw.toString(),
                        playableText = textResult.playableText,
                        detectedNotes = textResult.detectedNotes,
                        pageCount = pagesToRead,
                        detectedBpm = textResult.bpm,
                        mode = RecognitionMode.SOLFEGE_TEXT,
                    )

                    else -> Result(
                        displayName = displayName(uri),
                        rawText = raw.toString(),
                        playableText = "",
                        detectedNotes = 0,
                        pageCount = pagesToRead,
                        detectedBpm = textResult.bpm,
                        mode = RecognitionMode.NONE,
                        detectedStaffs = omrStaffs,
                        confidence = omrConfidence,
                        warnings = omrWarnings.toList(),
                    )
                }
            }
        } finally {
            recognizer.close()
            descriptor.close()
        }
    }

    private fun createReadableBitmap(pageWidth: Int, pageHeight: Int): Bitmap {
        val desiredScale = 2.2f
        val maxDimension = maxOf(pageWidth, pageHeight).coerceAtLeast(1)
        val cappedScale = min(desiredScale, MAX_RENDER_DIMENSION.toFloat() / maxDimension.toFloat())
            .coerceAtLeast(1f)
        val width = (pageWidth * cappedScale).toInt().coerceAtLeast(1)
        val height = (pageHeight * cappedScale).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    private suspend fun recognize(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap,
    ): String = suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result -> continuation.resume(result.text) }
            .addOnFailureListener { error -> continuation.resumeWithException(error) }
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        return "partition.pdf"
    }

    private object SolfegeOcrNormalizer {
        data class Conversion(
            val playableText: String,
            val detectedNotes: Int,
            val bpm: Int?,
        )

        private val noteRegex = Regex(
            pattern = "(?i)(?<![\\p{L}\\p{N}])(do|ré|re|mi|fa|sol|la|si|c|d|e|f|g|a|b)\\s*([#♯b♭])?\\s*([0-8])?(?:\\s*[:=]\\s*([0-9]+(?:[.,][0-9]+)?))?(?![\\p{L}])"
        )
        private val restRegex = Regex(
            pattern = "(?i)(?<![\\p{L}])(pause|silence|rest)(?:\\s*[:=]\\s*([0-9]+(?:[.,][0-9]+)?))?(?![\\p{L}])"
        )
        private val bpmRegex = Regex("(?i)(?:tempo\\s*[:=]?\\s*)?(\\d{2,3})\\s*bpm")
        private val tempoRegex = Regex("(?i)tempo\\s*[:=]\\s*(\\d{2,3})")
        private val metronomeRegex = Regex("(?im)^\\s*(?:q|j|♩|♪|♬)\\s*=\\s*(\\d{2,3})\\s*$")

        private val canonical = mapOf(
            "do" to "Do", "re" to "Ré", "mi" to "Mi", "fa" to "Fa",
            "sol" to "Sol", "la" to "La", "si" to "Si",
            "c" to "Do", "d" to "Ré", "e" to "Mi", "f" to "Fa",
            "g" to "Sol", "a" to "La", "b" to "Si",
        )

        fun convert(text: String): Conversion {
            if (text.isBlank()) return Conversion("", 0, null)

            val events = mutableListOf<Pair<Int, String>>()
            var noteCount = 0

            noteRegex.findAll(text).forEach { match ->
                val rawName = stripAccents(match.groupValues[1]).lowercase(Locale.ROOT)
                val name = canonical[rawName] ?: return@forEach
                val accidental = when (match.groupValues[2]) {
                    "#", "♯" -> "#"
                    "b", "♭" -> "b"
                    else -> ""
                }
                val octave = match.groupValues[3].ifBlank { DEFAULT_OCTAVE.toString() }
                val duration = match.groupValues[4]
                    .replace(',', '.')
                    .takeIf { it.isNotBlank() }
                    ?.let { ":$it" }
                    .orEmpty()

                events += match.range.first to "$name$accidental$octave$duration"
                noteCount += 1
            }

            restRegex.findAll(text).forEach { match ->
                val duration = match.groupValues[2]
                    .replace(',', '.')
                    .takeIf { it.isNotBlank() }
                    ?.let { ":$it" }
                    .orEmpty()
                events += match.range.first to "Pause$duration"
            }

            val bpm = (bpmRegex.find(text)?.groupValues?.getOrNull(1)
                ?: tempoRegex.find(text)?.groupValues?.getOrNull(1)
                ?: metronomeRegex.find(text)?.groupValues?.getOrNull(1))
                ?.toIntOrNull()
                ?.coerceIn(40, 220)

            return Conversion(
                playableText = events.sortedBy { it.first }.joinToString(" ") { it.second },
                detectedNotes = noteCount,
                bpm = bpm,
            )
        }

        private fun stripAccents(value: String): String = Normalizer
            .normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        private const val DEFAULT_OCTAVE = 4
    }

    companion object {
        private const val MAX_PAGES = 30
        private const val MAX_RENDER_DIMENSION = 2400
        private const val MIN_OMR_NOTES = 2
    }
}
