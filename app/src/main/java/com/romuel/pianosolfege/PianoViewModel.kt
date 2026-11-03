package com.romuel.pianosolfege

import android.app.Application
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PianoViewModel(application: Application) : AndroidViewModel(application) {
    var input by mutableStateOf("Do4 Ré4 Mi4 Fa4 Sol4 La4 Si4 Do5\n[Do4 Mi4 Sol4]:2 Pause:0.5 [Fa4 La4 Do5]:2")
        private set

    var bpm by mutableIntStateOf(90)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isPaused by mutableStateOf(false)
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
    private var playbackJob: Job? = null
    private var previewJob: Job? = null

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
        stop()
        engine.close()
        super.onCleared()
    }
}
