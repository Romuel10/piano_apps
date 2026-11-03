package com.romuel.pianosolfege

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.romuel.pianosolfege.audio.PianoEngine
import com.romuel.pianosolfege.data.SavedSong
import com.romuel.pianosolfege.data.SongRepository
import com.romuel.pianosolfege.music.NoteParser
import com.romuel.pianosolfege.music.PianoNote
import com.romuel.pianosolfege.pdf.PdfNoteReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class PianoViewModel(application: Application) : AndroidViewModel(application) {
    var input by mutableStateOf("Do4 Ré4 Mi4 Fa4 Sol4 La4 Si4 Do5\n[Do4 Mi4 Sol4]:2 Pause:0.5 [Fa4 La4 Do5]:2")
        private set

    var bpm by mutableIntStateOf(90)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isPaused by mutableStateOf(false)
        private set

    var isPdfImporting by mutableStateOf(false)
        private set

    var pdfStatus by mutableStateOf("PDF : prêt à importer")
        private set

    var currentNote by mutableStateOf<String?>(null)
        private set

    var activeMidis by mutableStateOf<List<Int>>(emptyList())
        private set

    var message by mutableStateOf("Prêt")
        private set

    var audioStatus by mutableStateOf("Préparation des sons de piano…")
        private set

    var songName by mutableStateOf("Mon morceau")
        private set

    var savedSongs by mutableStateOf<List<SavedSong>>(emptyList())
        private set

    private val engine = PianoEngine(application.applicationContext)
    private val repository = SongRepository(application.applicationContext)
    private val pdfReader = PdfNoteReader(application.applicationContext)
    private var playbackJob: Job? = null
    private var previewJob: Job? = null
    private var pdfJob: Job? = null

    init {
        savedSongs = repository.load()
        viewModelScope.launch {
            val loaded = engine.warmUp { done, total ->
                audioStatus = "Préparation du piano réel : $done/$total"
            }
            audioStatus = if (loaded == 8) {
                "Piano réel prêt • sons mis en cache pour les prochaines utilisations"
            } else {
                "Mode synthèse disponible • piano réel chargé partiellement ($loaded/8)"
            }
        }
    }

    fun updateInput(value: String) {
        input = value
    }

    fun updateBpm(value: Int) {
        bpm = value.coerceIn(40, 220)
    }

    fun updateSongName(value: String) {
        songName = value
    }

    fun importPdf(uri: Uri, playAfterImport: Boolean = true) {
        pdfJob?.cancel()
        stop()
        isPdfImporting = true
        pdfStatus = "PDF : analyse OCR + OMR local en cours…"
        message = "Lecture de la partition PDF…"

        pdfJob = viewModelScope.launch {
            try {
                val result = pdfReader.read(uri) { page, total ->
                    pdfStatus = "PDF : analyse de la page $page/$total…"
                }

                if (result.playableText.isBlank()) {
                    pdfStatus = if (result.detectedStaffs > 0) {
                        "${result.displayName} • ${result.detectedStaffs} portée(s) trouvée(s), aucune note suffisamment nette"
                    } else {
                        "${result.displayName} • aucune portée ou note exploitable détectée"
                    }
                    message = result.warnings.firstOrNull()
                        ?: "Essaie un PDF imprimé plus net, bien droit et avec une portée classique."
                    return@launch
                }

                input = result.playableText
                result.detectedBpm?.let { bpm = it }
                songName = result.displayName.substringBeforeLast('.').ifBlank { "PDF importé" }

                when (result.mode) {
                    PdfNoteReader.RecognitionMode.SHEET_MUSIC_OMR -> {
                        val confidencePercent = (result.confidence * 100f).roundToInt().coerceIn(0, 100)
                        pdfStatus = "${result.displayName} • OMR : ${result.detectedNotes} note(s), ${result.detectedStaffs} portée(s), confiance $confidencePercent%"
                        val warning = result.warnings.firstOrNull()
                        message = buildString {
                            append("Partition convertie en notes")
                            result.detectedBpm?.let { append(" • tempo $it BPM") }
                            if (warning != null) append(" • $warning")
                        }
                    }

                    PdfNoteReader.RecognitionMode.SOLFEGE_TEXT -> {
                        pdfStatus = "${result.displayName} • OCR texte : ${result.detectedNotes} note(s) sur ${result.pageCount} page(s)"
                        message = "PDF converti depuis les noms de notes${result.detectedBpm?.let { " • tempo $it BPM" } ?: ""}"
                    }

                    PdfNoteReader.RecognitionMode.NONE -> {
                        pdfStatus = "PDF analysé sans résultat jouable"
                        message = "Aucune note exploitable détectée."
                    }
                }

                if (playAfterImport) play()
            } catch (_: CancellationException) {
                pdfStatus = "Import PDF annulé"
                throw CancellationException()
            } catch (error: Exception) {
                pdfStatus = "Échec de lecture du PDF"
                message = error.message ?: "Impossible d'analyser ce PDF."
            } finally {
                isPdfImporting = false
            }
        }
    }

    fun play() {
        if (isPlaying) return
        previewJob?.cancel()
        previewJob = null
        engine.stop()

        val parsed = NoteParser.parse(input)
        if (parsed.errors.isNotEmpty()) {
            message = parsed.errors.joinToString("\n")
            return
        }

        playbackJob = viewModelScope.launch {
            isPlaying = true
            isPaused = false
            message = "Lecture de ${parsed.notes.size} élément(s)…"
            try {
                parsed.notes.forEach { note ->
                    currentNote = note.label
                    activeMidis = note.midis
                    engine.play(note, bpm) { isPaused }
                }
                message = "Lecture terminée"
            } catch (_: CancellationException) {
                message = "Lecture arrêtée"
                throw CancellationException()
            } finally {
                currentNote = null
                activeMidis = emptyList()
                isPlaying = false
                isPaused = false
                engine.stop()
            }
        }
    }

    fun togglePause() {
        if (!isPlaying) return
        isPaused = !isPaused
        message = if (isPaused) "Lecture en pause" else "Lecture reprise"
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        previewJob?.cancel()
        previewJob = null
        engine.stop()
        currentNote = null
        activeMidis = emptyList()
        isPlaying = false
        isPaused = false
    }

    fun previewMidi(midi: Int) {
        if (isPlaying || midi !in 21..108) return
        previewJob?.cancel()
        engine.stop()
        previewJob = viewModelScope.launch {
            val label = NoteParser.labelForMidi(midi)
            currentNote = label
            activeMidis = listOf(midi)
            try {
                engine.play(
                    PianoNote(label = label, midis = listOf(midi), beats = 0.65f),
                    bpm = 110,
                    isPaused = { false },
                )
            } finally {
                currentNote = null
                activeMidis = emptyList()
            }
        }
    }

    fun saveSong() {
        val parsed = NoteParser.parse(input)
        if (parsed.errors.isNotEmpty()) {
            message = "Impossible d'enregistrer : corrige d'abord les notes invalides."
            return
        }

        val finalName = songName.trim().ifBlank { "Morceau ${savedSongs.size + 1}" }
        savedSongs = repository.save(finalName, input.trim(), bpm)
        songName = finalName
        message = "« $finalName » enregistré sur cet appareil"
    }

    fun loadSong(song: SavedSong) {
        stop()
        input = song.content
        bpm = song.bpm
        songName = song.name
        message = "« ${song.name} » chargé"
    }

    fun deleteSong(song: SavedSong) {
        savedSongs = repository.delete(song.id)
        message = "« ${song.name} » supprimé"
    }

    override fun onCleared() {
        pdfJob?.cancel()
        stop()
        engine.close()
        super.onCleared()
    }
}
