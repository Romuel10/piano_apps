package com.romuel.pianosolfege

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.romuel.pianosolfege.audio.PianoEngine
import com.romuel.pianosolfege.music.NoteParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PianoViewModel : ViewModel() {
    var input by mutableStateOf("Do4 Ré4 Mi4 Fa4 Sol4 La4 Si4 Do5")
        private set

    var bpm by mutableIntStateOf(90)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var currentNote by mutableStateOf<String?>(null)
        private set

    var message by mutableStateOf("Prêt")
        private set

    private val engine = PianoEngine()
    private var playbackJob: Job? = null

    fun updateInput(value: String) {
        input = value
    }

    fun updateBpm(value: Int) {
        bpm = value.coerceIn(40, 220)
    }

    fun play() {
        stop()
        val parsed = NoteParser.parse(input)
        if (parsed.errors.isNotEmpty()) {
            message = parsed.errors.joinToString("\n")
            return
        }

        playbackJob = viewModelScope.launch {
            isPlaying = true
            message = "Lecture de ${parsed.notes.size} élément(s)…"
            try {
                parsed.notes.forEach { note ->
                    currentNote = note.label
                    engine.play(note, bpm)
                }
                message = "Lecture terminée"
            } catch (_: CancellationException) {
                message = "Lecture arrêtée"
                throw CancellationException()
            } finally {
                currentNote = null
                isPlaying = false
                engine.stop()
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        engine.stop()
        currentNote = null
        isPlaying = false
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
