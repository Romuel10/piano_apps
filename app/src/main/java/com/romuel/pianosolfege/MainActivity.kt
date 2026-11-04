package com.romuel.pianosolfege

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.romuel.pianosolfege.music.NoteParser
import com.romuel.pianosolfege.music.ScoreMeter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val accent = Color(0xFF136A62)
private val inkColor = Color(0xFF182840)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = accent,
                    primaryContainer = Color(0xFFD5F0E8),
                    background = Color(0xFFF5F7FB),
                    surface = Color.White,
                    onSurface = inkColor,
                ),
            ) { PianoApp() }
        }
    }
}

@Composable
private fun PianoApp(vm: PianoViewModel = viewModel()) {
    val context = LocalContext.current
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.importPdf(uri, playAfterImport = false)
        }
    }
    val xmlPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) vm.importMusicXml(uri)
    }
    val validation = remember(vm.input) { NoteParser.parse(vm.input) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "PIANO PARTITION+",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                "Vos partitions prennent vie",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = inkColor,
            )
            Text(
                "Importez une partition, corrigez les notes détectées et écoutez les deux mains synchronisées.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("01  Importer une partition",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "PDF imprimé : reconnaissance optique à vérifier. MusicXML / MXL : notes, voix et rythmes structurés pour les morceaux complexes.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                            enabled = !vm.isPdfImporting && !vm.isPlaying,
                        ) { Text("Importer PDF") }
                        OutlinedButton(
                            onClick = { xmlPicker.launch(arrayOf("*/*")) },
                            enabled = !vm.isPdfImporting && !vm.isPlaying,
                        ) { Text("MusicXML / MXL") }
                    }
                    Text(
                        vm.pdfStatus,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                    )
                    Text(
                        "Mesure et rythme",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        vm.meterStatus,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            ScoreMeter(3, 4),
                            ScoreMeter(4, 4),
                            ScoreMeter(6, 8),
                        ).forEach { selected ->
                            if (vm.meter == selected) {
                                Button(
                                    onClick = { vm.updateMeter(selected) },
                                    enabled = !vm.isPdfImporting && !vm.isPlaying,
                                ) { Text(selected.display) }
                            } else {
                                OutlinedButton(
                                    onClick = { vm.updateMeter(selected) },
                                    enabled = !vm.isPdfImporting && !vm.isPlaying,
                                ) { Text(selected.display) }
                            }
                        }
                    }
                    Text(
                        "Si la mesure n'est pas reconnue, choisis-la ici. Modifier la mesure relance l'analyse du PDF.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (vm.rhythmWarnings.isNotEmpty()) {
                        Text(
                            "Rythme non validé — vérifie les temps de chaque mesure",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        vm.rhythmWarnings.take(5).forEach { warning ->
                            Text(warning, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                        if (!vm.approximatePlaybackApproved) {
                            OutlinedButton(
                                onClick = vm::approveApproximatePlayback,
                                enabled = !vm.isPdfImporting,
                            ) { Text("Écouter malgré les erreurs de rythme") }
                        }
                    }
                    Text(
                        "La lecture du PDF ne démarre pas automatiquement : contrôlez d'abord la transcription.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val uri = vm.pdfUri
                    if (uri != null && vm.pdfPageCount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Aperçu du PDF • page ${vm.pdfPageIndex + 1}/${vm.pdfPageCount}")
                            Row {
                                TextButton(
                                    onClick = { vm.changePdfPage(vm.pdfPageIndex - 1) },
                                    enabled = vm.pdfPageIndex > 0,
                                ) { Text("‹") }
                                TextButton(
                                    onClick = { vm.changePdfPage(vm.pdfPageIndex + 1) },
                                    enabled = vm.pdfPageIndex < vm.pdfPageCount - 1,
                                ) { Text("›") }
                            }
                        }
                        PdfPagePreview(uri = uri, pageIndex = vm.pdfPageIndex)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("02  Corriger ou écrire les notes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = vm::loadFrereJacquesExample,
                            enabled = !vm.isPdfImporting,
                        ) { Text("Exemple Frère Jacques") }
                        OutlinedButton(
                            onClick = vm::loadRhythmDemo,
                            enabled = !vm.isPdfImporting && !vm.isPlaying,
                        ) { Text("Tester les rythmes") }
                        TextButton(onClick = vm::clearInput) { Text("Effacer") }
                    }
                    vm.scoreSource?.let {
                        Text(it, color = accent, style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedTextField(
                        value = vm.input,
                        onValueChange = vm::updateInput,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 14,
                        label = { Text("Éditeur solfège") },
                        placeholder = { Text("Do Ré Mi Fa Sol La Si, ou Do4:0.5 [Do Mi Sol]:2") },
                        supportingText = {
                            Text("Do ou Do4 sont acceptés. Sans octave : octave 4. Durées en temps (:1, :0.5, :2).")
                        },
                    )
                    Text(
                        "Figures rythmiques : ronde 4 • blanche 2 • noire 1 • croche 0,5 • double-croche 0,25 • triple-croche 0,125 temps. Un point ajoute la moitié de la durée.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Ajouter rapidement",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            "Do4", "Ré4", "Mi4", "Fa4", "Sol4", "La4", "Si4",
                            "Do5", "Do4:0.5", "Do4:0.25", "Do4:1.5",
                            "Pause:1", "Pause:0.5", "Pause:0.25",
                            "[Do4 Mi4 Sol4]:2",
                        ).forEach { token ->
                            OutlinedButton(
                                onClick = { vm.appendToken(token) },
                                enabled = !vm.isPdfImporting && !vm.isPlaying,
                            ) { Text(token) }
                        }
                    }
                    Text(
                        when {
                            vm.input.isBlank() -> "Ajoutez une note pour commencer."
                            validation.errors.isEmpty() ->
                                "${validation.notes.size} élément(s) valides • prêt à jouer"
                            else -> "À corriger : ${validation.errors.first()}"
                        },
                        color = if (validation.errors.isEmpty()) accent
                            else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Important : modifier l'aperçu MusicXML crée une séquence texte simplifiée et désactive sa synchronisation polyphonique.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("03  Écouter le morceau",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Tempo", fontWeight = FontWeight.Medium)
                        Text("${vm.bpm} BPM", color = accent, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = vm.bpm.toFloat(),
                        onValueChange = { vm.updateBpm(it.toInt()) },
                        valueRange = 40f..220f,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { vm.updateBpm(vm.bpm - 1) },
                            enabled = !vm.isPlaying,
                        ) { Text("− 1 BPM") }
                        OutlinedButton(
                            onClick = { vm.updateBpm(vm.bpm + 1) },
                            enabled = !vm.isPlaying,
                        ) { Text("+ 1 BPM") }
                    }
                    Text(vm.tempoStatus, style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = vm::play,
                            enabled = !vm.isPlaying && !vm.isPdfImporting && vm.input.isNotBlank(),
                        ) { Text("▶ Lecture") }
                        OutlinedButton(onClick = vm::togglePause, enabled = vm.isPlaying) {
                            Text(if (vm.isPaused) "▶ Reprendre" else "Ⅱ Pause")
                        }
                        OutlinedButton(onClick = vm::stop, enabled = vm.isPlaying) {
                            Text("■ Stop")
                        }
                    }
                    Text(
                        vm.currentNote ?: "Aucune note jouée",
                        style = MaterialTheme.typography.titleLarge,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(vm.message, style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Clavier interactif",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    PianoKeyboard(
                        activeMidis = vm.activeMidis,
                        enabled = !vm.isPlaying && !vm.isPdfImporting,
                        onKeyPress = vm::previewMidi,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Mes morceaux",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = vm.songName,
                        onValueChange = vm::updateSongName,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Nom du morceau") },
                    )
                    Button(onClick = vm::saveSong, enabled = !vm.isPdfImporting) {
                        Text("Enregistrer cette transcription")
                    }
                    if (vm.savedSongs.isEmpty()) {
                        Text("Aucun morceau enregistré.", style = MaterialTheme.typography.bodySmall)
                    } else vm.savedSongs.forEach { song ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.name, fontWeight = FontWeight.Bold)
                                Text("${song.bpm} BPM", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { vm.loadSong(song) }) { Text("Charger") }
                            TextButton(onClick = { vm.deleteSong(song) }) { Text("Suppr.") }
                        }
                    }
                }
            }
            Text(
                vm.audioStatus + "\nPDF : OMR expérimental, altérations et polyrythmies visuelles complexes non garanties. MusicXML/MXL préférable pour une fidélité élevée.",
                style = MaterialTheme.typography.bodySmall,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Mises à jour", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Installez une nouvelle APK de distribution signée par-dessus la précédente sans effacer vos morceaux. Les APK debug des anciens tests peuvent nécessiter une transition unique.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/Romuel10/piano_apps/actions/workflows/android.yml"),
                            ),
                        )
                    }) {
                        Text("Consulter les nouvelles versions")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PdfPagePreview(uri: Uri, pageIndex: Int) {
    val context = LocalContext.current
    val image by produceState<Bitmap?>(initialValue = null, uri, pageIndex) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        renderer.openPage(pageIndex.coerceIn(0, renderer.pageCount - 1)).use { page ->
                            val width = 720
                            val scale = width.toFloat() / page.width.coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                                val matrix = Matrix().apply { setScale(scale, scale) }
                                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }.getOrNull()
        }
    }
    if (image == null) {
        Text("Chargement de l'aperçu…", style = MaterialTheme.typography.bodySmall)
    } else {
        Image(
            bitmap = image!!.asImageBitmap(),
            contentDescription = "Aperçu de la partition PDF",
            modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun PianoKeyboard(
    activeMidis: List<Int>,
    enabled: Boolean,
    onKeyPress: (Int) -> Unit,
) {
    val whiteKeys = listOf(
        60 to "Do",
        62 to "Ré",
        64 to "Mi",
        65 to "Fa",
        67 to "Sol",
        69 to "La",
        71 to "Si",
        72 to "Do",
    )
    val blackKeys = listOf(
        61 to 1f,
        63 to 2f,
        66 to 4f,
        68 to 5f,
        70 to 6f,
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .padding(8.dp),
        ) {
            val whiteWidth = maxWidth / whiteKeys.size.toFloat()
            val blackWidth = whiteWidth * 0.62f

            Row(modifier = Modifier.fillMaxSize()) {
                whiteKeys.forEach { (midi, label) ->
                    val active = midi in activeMidis
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.White)
                            .border(1.dp, Color.Gray)
                            .clickable(enabled = enabled) { onKeyPress(midi) },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(bottom = 10.dp),
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            blackKeys.forEach { (midi, position) ->
                val active = midi in activeMidis
                Box(
                    modifier = Modifier
                        .offset(x = whiteWidth * position - blackWidth / 2f)
                        .width(blackWidth)
                        .height(112.dp)
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Black)
                        .clickable(enabled = enabled) { onKeyPress(midi) },
                )
            }
        }
    }
}
