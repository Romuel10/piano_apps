package com.romuel.pianosolfege

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PianoApp()
            }
        }
    }
}

@Composable
private fun PianoApp(vm: PianoViewModel = viewModel()) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Piano Solfège",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Écris une mélodie ou des accords en solfège, choisis le tempo et laisse l'application les jouer.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = vm.audioStatus,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            OutlinedTextField(
                value = vm.input,
                onValueChange = vm::updateInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes et accords") },
                minLines = 5,
                supportingText = {
                    Text("Ex. Do4 Ré4:0.5 [Do4 Mi4 Sol4]:2 Pause:1 [Fa4 La4 Do5]")
                },
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Tempo", fontWeight = FontWeight.SemiBold)
                    Text("${vm.bpm} BPM")
                }
                Slider(
                    value = vm.bpm.toFloat(),
                    onValueChange = { vm.updateBpm(it.toInt()) },
                    valueRange = 40f..220f,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = vm::play, enabled = !vm.isPlaying) {
                    Text("▶ Lecture")
                }
                OutlinedButton(onClick = vm::togglePause, enabled = vm.isPlaying) {
                    Text(if (vm.isPaused) "▶ Reprendre" else "⏸ Pause")
                }
                OutlinedButton(onClick = vm::stop, enabled = vm.isPlaying) {
                    Text("■ Stop")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Élément actuel", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = vm.currentNote ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(vm.message, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("Clavier", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Touchez une touche pour l'écouter. Les touches jouées par la séquence s'allument.",
                style = MaterialTheme.typography.bodyMedium,
            )
            PianoKeyboard(
                activeMidis = vm.activeMidis,
                enabled = !vm.isPlaying,
                onKeyPress = vm::previewMidi,
            )

            Text("Mes morceaux", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = vm.songName,
                onValueChange = vm::updateSongName,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Nom du morceau") },
            )
            Button(onClick = vm::saveSong) {
                Text("Enregistrer sur l'appareil")
            }

            if (vm.savedSongs.isEmpty()) {
                Text("Aucun morceau enregistré pour le moment.")
            } else {
                vm.savedSongs.forEach { song ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(song.name, fontWeight = FontWeight.Bold)
                                    Text("${song.bpm} BPM", style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(onClick = { vm.loadSong(song) }) { Text("Charger") }
                                TextButton(onClick = { vm.deleteSong(song) }) { Text("Supprimer") }
                            }
                            Text(
                                text = song.content.replace('\n', ' ').take(120),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("Syntaxe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "• Notes : Do, Ré, Mi, Fa, Sol, La, Si\n" +
                    "• Octaves : A0 à C8\n" +
                    "• Altérations : # ou b, ex. Fa#4, Sib4\n" +
                    "• Durée en temps : :0.5, :1, :2, :4\n" +
                    "• Accord : [Do4 Mi4 Sol4]:2\n" +
                    "• Silence : Pause:1"
            )

            Text(
                text = "Piano réel : Salamander Grand Piano V3, Alexander Holm — CC BY 3.0. Les échantillons sont téléchargés puis mis en cache localement ; la synthèse intégrée sert de secours hors connexion avant le premier téléchargement.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
