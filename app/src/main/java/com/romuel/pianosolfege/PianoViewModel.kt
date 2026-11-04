package com.romuel.pianosolfege

import android.app.Application
import android.net.Uri
import android.graphics.pdf.PdfRenderer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.romuel.pianosolfege.audio.PianoEngine
import com.romuel.pianosolfege.audio.PolyphonicScorePlayer
import com.romuel.pianosolfege.data.SavedSong
import com.romuel.pianosolfege.data.SongRepository
import com.romuel.pianosolfege.music.NoteParser
import com.romuel.pianosolfege.music.ImportedScore
import com.romuel.pianosolfege.music.ScoreMeter
import com.romuel.pianosolfege.music.MusicXmlImporter
import com.romuel.pianosolfege.music.PianoNote
import com.romuel.pianosolfege.pdf.PdfNoteReader
import com.romuel.pianosolfege.pdf.AdvancedOmrClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class PianoViewModel(application: Application) : AndroidViewModel(application) {
    var input by mutableStateOf("Do4 Ré4 Mi4 Fa4 Sol4 La4 Si4 Do5\n[Do4 Mi4 Sol4]:2 Pause:0.5 [Fa4 La4 Do5]:2")
        private set

    var bpm by mutableIntStateOf(90)
        private set

    var tempoStatus by mutableStateOf("Tempo manuel / par défaut")
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isPaused by mutableStateOf(false)
        private set

    var isPdfImporting by mutableStateOf(false)
        private set

    var pdfStatus by mutableStateOf("PDF : prêt à importer")
        private set

    var advancedServerUrl by mutableStateOf(
        application.getSharedPreferences("omr_settings", 0)
            .getString("endpoint", "").orEmpty(),
    )
        private set

    // The secret is NEVER saved to preferences or included in the repository.
    var advancedToken by mutableStateOf("")
        private set

    var meter by mutableStateOf(ScoreMeter(4, 4))
        private set

    var meterStatus by mutableStateOf("4/4 par défaut, à confirmer")
        private set

    private var meterManuallySelected = false

    var rhythmWarnings by mutableStateOf<List<String>>(emptyList())
        private set

    var approximatePlaybackApproved by mutableStateOf(false)
        private set

    var scoreSource by mutableStateOf<String?>(null)
        private set

    var pdfUri by mutableStateOf<Uri?>(null)
        private set

    var pdfPageCount by mutableIntStateOf(0)
        private set

    var pdfPageIndex by mutableIntStateOf(0)
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
    private val scorePlayer = PolyphonicScorePlayer()
    private var importedScore: ImportedScore? = null
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
        rhythmWarnings = emptyList()
        approximatePlaybackApproved = false
        if (importedScore != null) {
            importedScore = null
            scoreSource = null
            message = "Texte modifié : lecture depuis l'éditeur (sans polyphonie MusicXML)."
        }
    }

    fun appendToken(token: String) {
        updateInput(if (input.isBlank()) token else input.trimEnd() + " " + token)
    }

    fun clearInput() {
        stop()
        updateInput("")
        message = "Éditeur vidé"
    }

    fun loadFrereJacquesExample() {
        stop()
        importedScore = null
        scoreSource = null
        rhythmWarnings = emptyList()
        approximatePlaybackApproved = false
        pdfUri = null
        bpm = 100
        tempoStatus = "100 BPM • exemple Frère Jacques"
        songName = "Frère Jacques - exemple"
        input = """Do4 Ré4 Mi4 Do4  Do4 Ré4 Mi4 Do4
Mi4 Fa4 Sol4:2  Mi4 Fa4 Sol4:2
Sol4:0.5 La4:0.5 Sol4:0.5 Fa4:0.5 Mi4 Do4
Sol4:0.5 La4:0.5 Sol4:0.5 Fa4:0.5 Mi4 Do4
Do4 Sol3 Do4:2  Do4 Sol3 Do4:2"""
        message = "Exemple de la mélodie chargé à 100 BPM (pas une transcription du PDF)."
    }

    fun loadRhythmDemo() {
        stop()
        importedScore = null
        scoreSource = null
        pdfUri = null
        rhythmWarnings = emptyList()
        approximatePlaybackApproved = false
        bpm = 100
        tempoStatus = "100 BPM • démonstration"
        meter = ScoreMeter(4, 4)
        meterStatus = "4/4 • démonstration"
        songName = "Démonstration croches et doubles-croches"
        input = """Do4:1 Ré4:0.5 Mi4:0.5 Fa4:0.25 Sol4:0.25 La4:0.25 Si4:0.25 Do5:1
Do5:1.5 Pause:0.5 Sol4:2"""
        message = "Exemple : noire 1, croche 0,5, double-croche 0,25, noire pointée 1,5."
    }

    fun changePdfPage(value: Int) {
        pdfPageIndex = value.coerceIn(0, (pdfPageCount - 1).coerceAtLeast(0))
    }

    fun updateMeter(value: ScoreMeter) {
        if (isPdfImporting || isPlaying) return
        meter = value
        meterManuallySelected = true
        meterStatus = "${value.display} sélectionné manuellement"
        val existingPdf = pdfUri
        if (existingPdf != null) {
            importPdf(existingPdf)
        }
    }

    fun approveApproximatePlayback() {
        approximatePlaybackApproved = true
        message = "Lecture approximative autorisée : vérifie les temps signalés."
    }

    fun updateBpm(value: Int) {
        bpm = value.coerceIn(40, 220)
        tempoStatus = "${bpm} BPM choisis manuellement"
    }

    fun updateSongName(value: String) {
        songName = value
    }

    fun importPdf(uri: Uri, playAfterImport: Boolean = false) {
        pdfJob?.cancel()
        stop()
        pdfUri = uri
        pdfPageCount = 0
        pdfPageIndex = 0
        importedScore = null
        scoreSource = null
        rhythmWarnings = emptyList()
        approximatePlaybackApproved = false
        isPdfImporting = true
        pdfStatus = "PDF : analyse OCR + OMR local en cours…"
        message = "Lecture de la partition PDF…"

        pdfJob = viewModelScope.launch {
            try {
                val result = pdfReader.read(
                    uri, selectedMeter = meter, forceSelectedMeter = meterManuallySelected,
                ) { page, total ->
                    pdfStatus = "PDF : analyse de la page $page/$total…"
                }

                meter = result.meter
                meterStatus = if (result.meterDetected) {
                    "${meter.display} détecté dans le PDF"
                } else {
                    "${meter.display} choisi manuellement (non détecté avec certitude)"
                }
                rhythmWarnings = result.rhythmIssues.take(8).map { it.message() }
                pdfPageCount = result.pageCount
                pdfPageIndex = if (result.pageCount > 1) 1 else 0

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
                scoreSource = "PDF • transcription optique provisoire, à vérifier"
                if (result.detectedBpm != null) {
                    bpm = result.detectedBpm
                    tempoStatus = "${bpm} BPM détectés dans le PDF"
                } else {
                    tempoStatus = "Tempo non reconnu dans le PDF : règle-le manuellement"
                }
                songName = result.displayName.substringBeforeLast('.').ifBlank { "PDF importé" }

                when (result.mode) {
                    PdfNoteReader.RecognitionMode.SHEET_MUSIC_OMR -> {
                        if (result.timedNotes.isNotEmpty()) {
                            importedScore = ImportedScore(
                                notes = result.timedNotes,
                                bpm = result.detectedBpm ?: bpm,
                                title = songName,
                                editablePreview = result.playableText,
                                warnings = result.warnings,
                            )
                            scoreSource = "PDF • lecture simultanée des mains (OMR à vérifier)"
                        }
                        val confidencePercent = (result.confidence * 100f).roundToInt().coerceIn(0, 100)
                        pdfStatus = "${result.displayName} • OMR : ${result.detectedNotes} note(s), ${result.measures} mesure(s) • ${meter.display} • indice visuel $confidencePercent% (non calibré)"
                        val warning = result.warnings.firstOrNull()
                        message = buildString {
                            append("Partition analysée : ${result.rhythmIssues.size} mesure(s)/portée(s) au rythme incohérent • vérifie avant de jouer")
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

    fun updateAdvancedServerUrl(value: String) {
        advancedServerUrl = value.trim()
        getApplication<Application>().getSharedPreferences("omr_settings", 0)
            .edit().putString("endpoint", advancedServerUrl).apply()
    }

    fun updateAdvancedToken(value: String) {
        advancedToken = value
    }

    fun importAdvancedPdf(uri: Uri) {
        pdfJob?.cancel()
        stop()
        pdfUri = uri
        pdfPageCount = 0
        pdfPageIndex = 0
        importedScore = null
        scoreSource = null
        rhythmWarnings = emptyList()
        approximatePlaybackApproved = false
        isPdfImporting = true
        pdfStatus = "OMR avancée : envoi du PDF à votre serveur privé…"
        message = "Le PDF sera analysé par Audiveris sur le serveur que tu as configuré."

        pdfJob = viewModelScope.launch {
            try {
                pdfPageCount = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")
                        ?.use { descriptor -> PdfRenderer(descriptor).use { it.pageCount } } ?: 0
                }
                pdfPageIndex = if (pdfPageCount > 1) 1 else 0
                val xml = AdvancedOmrClient.recognize(
                    getApplication<Application>(), uri, advancedServerUrl, advancedToken,
                )
                pdfStatus = "OMR avancée : reconstruction de la partition MusicXML…"
                val score = MusicXmlImporter.readBytes(xml)
                importedScore = score
                input = score.editablePreview
                bpm = score.bpm
                tempoStatus = "${bpm} BPM de la partition MusicXML"
                songName = score.title
                scoreSource = "OMR Audiveris → MusicXML • ${score.notes.size} notes • voix simultanées"
                pdfStatus = "Transcription avancée terminée : ${score.notes.size} notes"
                message = "Vérifie les notes, les rythmes et les reprises : même Audiveris peut se tromper."
                score.warnings.firstOrNull()?.let { message += " • $it" }
            } catch (_: CancellationException) {
                pdfStatus = "Import avancé annulé"
                throw CancellationException()
            } catch (error: Exception) {
                pdfStatus = "OMR avancée impossible"
                message = error.message ?: "Vérifie le serveur HTTPS et la partition."
            } finally {
                isPdfImporting = false
            }
        }
    }

    fun importMusicXml(uri: Uri) {
        pdfJob?.cancel()
        stop()
        importedScore = null
        scoreSource = null
        pdfUri = null
        pdfPageCount = 0
        isPdfImporting = true
        pdfStatus = "Import MusicXML : analyse des voix et des mesures…"
        pdfJob = viewModelScope.launch {
            try {
                val score = MusicXmlImporter.read(getApplication<Application>(), uri)
                importedScore = score
                input = score.editablePreview
                bpm = score.bpm
                tempoStatus = "${bpm} BPM lus dans le MusicXML"
                songName = score.title
                scoreSource = "MusicXML • ${score.notes.size} notes • polyphonie conservée"
                pdfStatus = "MusicXML chargé : ${score.notes.size} notes, tempo ${score.bpm} BPM"
                message = "La lecture respecte les durées de chaque main. Le texte est un aperçu simplifié ; sa modification désactive la polyphonie exacte."
                score.warnings.firstOrNull()?.let { message += " • $it" }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Exception) {
                pdfStatus = "Erreur d'import MusicXML"
                message = error.message ?: "MusicXML non pris en charge."
            } finally {
                isPdfImporting = false
            }
        }
    }

    fun play() {
        if (isPlaying || isPdfImporting) return
        previewJob?.cancel()
        previewJob = null
        engine.stop()

        val score = importedScore
        if (score != null && rhythmWarnings.isNotEmpty() && !approximatePlaybackApproved) {
            message = "Lecture bloquée : ${rhythmWarnings.size} anomalie(s) rythmiques. Corrige la saisie ou confirme une écoute approximative."
            return
        }
        if (score != null) {
            playbackJob = viewModelScope.launch {
                isPlaying = true
                isPaused = false
                message = "Lecture polyphonique de ${score.notes.size} notes…"
                try {
                    scorePlayer.play(score.notes, bpm, { isPaused }, score.tempoChanges, score.bpm) { active ->
                        activeMidis = active
                        currentNote = if (active.isEmpty()) null
                            else active.joinToString(" + ") { NoteParser.labelForMidi(it) }
                    }
                    message = "Lecture de partition terminée"
                } catch (_: CancellationException) {
                    message = "Lecture arrêtée"
                    throw CancellationException()
                } catch (error: Exception) {
                    message = error.message ?: "Erreur de lecture polyphonique."
                } finally {
                    currentNote = null
                    activeMidis = emptyList()
                    isPlaying = false
                    isPaused = false
                    scorePlayer.stop()
                }
            }
            return
        }

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
        scorePlayer.stop()
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
        message = if (importedScore != null) "« $finalName » enregistré comme texte simplifié (la polyphonie MusicXML n'est pas sauvegardée)." else "« $finalName » enregistré sur cet appareil"
    }

    fun loadSong(song: SavedSong) {
        stop()
        importedScore = null
        scoreSource = null
        pdfUri = null
        input = song.content
        bpm = song.bpm
        tempoStatus = "${bpm} BPM • morceau enregistré"
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
