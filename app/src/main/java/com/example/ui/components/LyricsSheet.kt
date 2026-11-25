package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.lyrics.ParsedLyrics
import com.example.data.model.Song
import com.example.ui.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    song: Song,
    currentPositionMs: Long,
    viewModel: MusicViewModel,
    onImportLrcClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val dbEntity by viewModel.observeLyrics(song.id).collectAsState(initial = null)
    val settings by viewModel.settings.collectAsState()
    var discovered by remember(song.id) { mutableStateOf<ParsedLyrics?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var editorText by remember(song.id) { mutableStateOf("") }

    LaunchedEffect(song.id) {
        discovered = viewModel.loadLyricsIfMissing(song)
    }

    val parsed = remember(dbEntity, discovered) {
        val raw = dbEntity?.lyricsText ?: discovered?.rawText ?: ""
        viewModel.lyricsManager.parse(raw)
    }
    val offsetMs = dbEntity?.offsetMs ?: settings.defaultLyricsOffsetMs

    LaunchedEffect(parsed.rawText, isEditing) {
        if (isEditing && editorText.isEmpty() && parsed.rawText.isNotEmpty()) {
            editorText = parsed.rawText
        }
    }

    val effectivePosMs = (currentPositionMs + offsetMs).coerceAtLeast(0L)
    val activeLineIndex = remember(parsed, effectivePosMs) {
        if (!parsed.isSynced || parsed.lines.isEmpty()) -1
        else parsed.lines.indexOfLast { it.timestampMs <= effectivePosMs }.coerceAtLeast(0)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0 && !isEditing) {
            listState.animateScrollToItem((activeLineIndex - 2).coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lyrics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${song.title} • ${song.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onImportLrcClick) {
                        Icon(Icons.Rounded.FileOpen, contentDescription = "Import .lrc file")
                    }
                    IconButton(
                        onClick = {
                            if (!isEditing) editorText = parsed.rawText
                            isEditing = !isEditing
                        }
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Rounded.Save else Icons.Rounded.Edit,
                            contentDescription = "Edit Lyrics",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Sync offset controls when synced
            if (parsed.isSynced && !isEditing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sync Offset: ${if (offsetMs >= 0) "+" else ""}${offsetMs}ms",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.updateLyricsOffset(song.id, offsetMs - 250L) }
                        ) { Text("-250ms") }
                        OutlinedButton(
                            onClick = { viewModel.updateLyricsOffset(song.id, offsetMs + 250L) }
                        ) { Text("+250ms") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = editorText,
                    onValueChange = { editorText = it },
                    label = { Text("Paste plain text or LRC [mm:ss.xx] lyrics") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 340.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = { isEditing = false }) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.saveManualLyrics(song.id, editorText, offsetMs)
                            isEditing = false
                        }
                    ) { Text("Save Lyrics") }
                }
            } else if (parsed.lines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No embedded or .lrc lyrics found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onImportLrcClick) {
                                Text("Import .LRC")
                            }
                            Button(
                                onClick = {
                                    editorText = ""
                                    isEditing = true
                                }
                            ) {
                                Text("Add Manually")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(parsed.lines) { idx, line ->
                        val isActive = parsed.isSynced && idx == activeLineIndex
                        val lineAlpha = if (!parsed.isSynced || isActive) 1f else 0.5f
                        val bg = if (isActive && settings.karaokeHighlight) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else MaterialTheme.colorScheme.surface.copy(alpha = 0f)

                        Text(
                            text = line.text,
                            style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = lineAlpha),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(bg)
                                .clickable(enabled = parsed.isSynced) {
                                    onSeekTo((line.timestampMs - offsetMs).coerceAtLeast(0L))
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
