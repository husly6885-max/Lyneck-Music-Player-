package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AudioFileDetails
import com.example.data.model.DuplicateGroup
import com.example.data.model.Song
import java.io.File

@Composable
fun EditSongTagsDialog(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int,
        trackNumber: Int,
        newFileName: String?
    ) -> Unit
) {
    val initialFileName = remember(song) {
        if (song.dataPath.isNotBlank()) File(song.dataPath).nameWithoutExtension else song.title
    }
    var title by remember(song) { mutableStateOf(song.title) }
    var artist by remember(song) { mutableStateOf(song.artist) }
    var album by remember(song) { mutableStateOf(song.album) }
    var genre by remember(song) { mutableStateOf(song.genre) }
    var yearStr by remember(song) { mutableStateOf(if (song.year > 0) song.year.toString() else "") }
    var trackStr by remember(song) { mutableStateOf(if (song.trackNumber > 0) song.trackNumber.toString() else "") }
    var fileName by remember(song) { mutableStateOf(initialFileName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Track Info & Rename", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = yearStr,
                        onValueChange = { yearStr = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Year") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = trackStr,
                        onValueChange = { trackStr = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Track #") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name (without extension)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val renamed = if (fileName.isNotBlank() && fileName.trim() != initialFileName) {
                        fileName.trim()
                    } else null
                    onSave(
                        title.ifBlank { song.title },
                        artist.ifBlank { song.artist },
                        album.ifBlank { song.album },
                        genre.ifBlank { "Unknown Genre" },
                        yearStr.toIntOrNull() ?: 0,
                        trackStr.toIntOrNull() ?: 0,
                        renamed
                    )
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FileDetailsDialog(
    details: AudioFileDetails,
    onDismiss: () -> Unit
) {
    val song = details.song
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio File Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow("Title", song.title)
                DetailRow("Artist", song.artist)
                DetailRow("Album", song.album)
                DetailRow("Genre", song.genre)
                if (song.year > 0) DetailRow("Year", song.year.toString())
                if (song.trackNumber > 0) DetailRow("Track #", song.trackNumber.toString())
                DetailRow("Duration", song.formattedDuration)
                DetailRow("File Size", song.formattedSize)
                DetailRow("Format", song.mimeType)
                if (details.bitrateKbps > 0) DetailRow("Bitrate", "${details.bitrateKbps} kbps")
                if (details.sampleRateHz > 0) DetailRow("Sample Rate", "${details.sampleRateHz} Hz")
                if (details.channels > 0) DetailRow("Channels", if (details.channels >= 2) "Stereo (${details.channels})" else "Mono (1)")
                DetailRow("Play Count", song.playCount.toString())
                DetailRow("Storage Path", song.dataPath.ifBlank { song.contentUri.toString() })
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
        )
    }
}

@Composable
fun DuplicateSongsDialog(
    groups: List<DuplicateGroup>,
    onDeleteSong: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicate Tracks (${groups.size} groups)", fontWeight = FontWeight.Bold) },
        text = {
            if (groups.isEmpty()) {
                Text("Great! No duplicate songs were detected in your library.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(groups, key = { it.key }) { group ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = group.reason,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                group.songs.forEach { song ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${song.artist} • ${song.formattedSize} • ${song.folderPath.substringAfterLast('/')}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(onClick = { onDeleteSong(song) }) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteForever,
                                                contentDescription = "Delete Duplicate",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun ConfirmPermanentDeleteDialog(
    songs: List<Song>,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    if (songs.isEmpty()) return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = if (songs.size == 1) "Delete Song Permanently?" else "Delete ${songs.size} Songs Permanently?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = if (songs.size == 1) {
                        "\"${songs.first().title}\" will be permanently erased from your phone's storage and removed from all playlists and queues. This cannot be undone."
                    } else {
                        "${songs.size} audio files will be permanently erased from your phone's storage and removed from all playlists and queues. This cannot be undone."
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete Permanently")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
