package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MusicViewModel
import java.util.Locale

@Composable
fun EqualizerScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val eqState by viewModel.eqState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val customPresets by viewModel.customEqPresets.collectAsState()

    var showSavePresetDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Equalizer & Sound Effects",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = settings.equalizerEnabled,
                    onCheckedChange = { viewModel.setEqualizerEnabled(it) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Active output profile banner
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (eqState.isHeadphonesConnected) Icons.Rounded.Headphones else Icons.Rounded.Speaker,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (eqState.isHeadphonesConnected) "Output: Headphones / Bluetooth" else "Output: Device Speaker",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Auto-switches EQ curve when headphones connect",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = settings.separateHeadphoneProfile,
                                onCheckedChange = { viewModel.setSeparateHeadphoneProfile(it) }
                            )
                        }
                    }
                }

                // Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Presets (${eqState.activePresetName})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(onClick = { showSavePresetDialog = true }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Preset")
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(eqState.builtInPresets) { presetName ->
                        FilterChip(
                            selected = eqState.activePresetName.equals(presetName, ignoreCase = true),
                            onClick = { viewModel.applyBuiltInEqPreset(presetName) },
                            label = { Text(presetName) }
                        )
                    }
                    items(customPresets, key = { it.id }) { custom ->
                        FilterChip(
                            selected = eqState.activePresetName == custom.name,
                            onClick = { viewModel.applyCustomEqPreset(custom) },
                            label = { Text(custom.name) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Delete Preset",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.deleteCustomEqPreset(custom.id) }
                                )
                            }
                        )
                    }
                }

                // Multi-Band Equalizer Sliders
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Equalizer Bands",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val minDb = eqState.minLevelMb.toFloat()
                        val maxDb = eqState.maxLevelMb.toFloat()
                        eqState.bands.forEach { band ->
                            val dbVal = band.levelMb / 100f
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = band.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%+.1f dB", dbVal),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = band.levelMb.toFloat().coerceIn(minDb, maxDb),
                                    onValueChange = { viewModel.setEqBandLevel(band.index, it.toInt()) },
                                    valueRange = minDb..maxDb,
                                    enabled = settings.equalizerEnabled
                                )
                            }
                        }
                    }
                }

                // Bass Boost, Virtualizer, Treble
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Bass Boost (${settings.bassBoostStrength / 10}%)", fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = settings.bassBoostEnabled,
                                onCheckedChange = { viewModel.setBassBoost(it, settings.bassBoostStrength) }
                            )
                        }
                        Slider(
                            value = settings.bassBoostStrength.toFloat(),
                            onValueChange = { viewModel.setBassBoost(settings.bassBoostEnabled, it.toInt()) },
                            valueRange = 0f..1000f,
                            enabled = settings.bassBoostEnabled
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Virtualizer / Surround (${settings.virtualizerStrength / 10}%)", fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = settings.virtualizerEnabled,
                                onCheckedChange = { viewModel.setVirtualizer(it, settings.virtualizerStrength) }
                            )
                        }
                        Slider(
                            value = settings.virtualizerStrength.toFloat(),
                            onValueChange = { viewModel.setVirtualizer(settings.virtualizerEnabled, it.toInt()) },
                            valueRange = 0f..1000f,
                            enabled = settings.virtualizerEnabled
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Treble Clarity Boost", fontWeight = FontWeight.SemiBold)
                            Text("${settings.trebleBoost / 10}%", color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = settings.trebleBoost.toFloat(),
                            onValueChange = { viewModel.setTrebleBoost(it.toInt()) },
                            valueRange = 0f..1000f
                        )
                    }
                }

                // Stereo Balance, Volume Normalization, ReplayGain
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val balText = when {
                                settings.audioBalance < -0.05f -> "Left ${((-settings.audioBalance) * 100).toInt()}%"
                                settings.audioBalance > 0.05f -> "Right ${(settings.audioBalance * 100).toInt()}%"
                                else -> "Center"
                            }
                            Text("L / R Audio Balance ($balText)", fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { viewModel.setAudioBalance(0f) }) { Text("Reset") }
                        }
                        Slider(
                            value = settings.audioBalance,
                            onValueChange = { viewModel.setAudioBalance(it) },
                            valueRange = -1f..1f
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Volume Normalization", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Smooth loudness differences between songs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.volumeNormalization,
                                onCheckedChange = { viewModel.setVolumeNormalization(it) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ReplayGain Support", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Apply track gain compensation when available",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.replayGainEnabled,
                                onCheckedChange = { viewModel.setReplayGainEnabled(it) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showSavePresetDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Custom EQ Preset", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCurrentAsCustomEqPreset(name)
                        showSavePresetDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel") }
            }
        )
    }
}
