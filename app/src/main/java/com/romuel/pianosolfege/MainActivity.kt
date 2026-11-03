package com.romuel.pianosolfege

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
                text = "Écris des notes en français puis appuie sur Lecture. L'application fonctionne hors ligne.",
                style = MaterialTheme.typography.bodyLarge,
            )

            OutlinedTextField(
                value = vm.input,
                onValueChange = vm::updateInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
                minLines = 4,
                supportingText = {
                    Text("Ex. Do4 Ré4 Mi4:0.5 Fa#4 Sib4 Pause:1 Sol4:2")
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = vm::play, enabled = !vm.isPlaying) {
                    Text("▶ Lecture")
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
                    Text("Note actuelle", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = vm.currentNote ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(vm.message, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(4.dp))
            Text("Syntaxe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("• Notes : Do, Ré, Mi, Fa, Sol, La, Si\n• Octaves : 0 à 8 (plage piano A0–C8)\n• Altérations : # ou b, par ex. Fa#4, Sib4\n• Durée en temps : :0.5, :1, :2, :4\n• Silence : Pause:1")
        }
    }
}
