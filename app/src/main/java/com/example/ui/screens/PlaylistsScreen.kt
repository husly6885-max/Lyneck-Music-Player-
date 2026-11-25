package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Playlist
import com.example.data.model.SmartPlaylistType
import com.example.data.model.Song
import com.example.ui.MusicViewModel
import com.example.ui.components.MultiSongPickerDialog
import com.example.ui.components.RenamePlaylistDialog
import com.example.ui.components.SongActionHandlers
import com.example.ui.components.SongListItem
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PlaylistsScreen(
    viewModel: MusicViewModel,
    songActions: SongActionHandlers,
    onExportPlaylistM3u: (Playlist, List<Song>) -> Unit,
    onImportPlaylistM3u: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.playlists.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val favorites by viewModel.favoriteSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayedSongs.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedSongs.collectAsState()
    val mostPlayed by viewModel.mostPlayedSongs.collectAsState()

    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val selectedSmart by viewModel.selectedSmartPlaylist.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var showMultiSongPicker by remember { mutableStateOf(false) }

    // Smart Playlist Detail View
    if (selectedSmart != null) {
        val smart = selectedSmart!!
        val smartSongs = when (smart) {
            SmartPlaylistType.RECENTLY_PLAYED -> recentlyPlayed
            SmartPlaylistType.MOST_PLAYED -> mostPlayed
            SmartPlaylistType.RECENTLY_ADDED -> recentlyAdded
            SmartPlaylistType.FAVORITES -> favorites
        }
        BackHandler { viewModel.selectSmartPlaylist(null) }
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.selectSmartPlaylist(null) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(smart.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(smart.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (smartSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No songs in ${smart.title} yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${smartSongs.size} tracks", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.playSong(smartSongs.first(), smartSongs) }, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Text("Play All")
                        }
                        FilledTonalButton(onClick = { viewModel.shuffleAndPlay(smartSongs) }, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Rounded.Shuffle, null)
                        }
                    }
                }
                val smartKey = "smart_${smart.name}"
                val savedSmart = remember(smartKey) { viewModel.getScrollPosition(smartKey) }
                val smartListState = rememberLazyListState(
                    initialFirstVisibleItemIndex = savedSmart.firstVisibleItemIndex,
                    initialFirstVisibleItemScrollOffset = savedSmart.firstVisibleItemScrollOffset
                )
                LaunchedEffect(smartListState, smartKey) {
                    snapshotFlow { smartListState.firstVisibleItemIndex to smartListState.firstVisibleItemScrollOffset }
                        .collectLatest { (idx, off) -> viewModel.saveScrollPosition(smartKey, idx, off) }
                }
                LazyColumn(
                    state = smartListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(smartSongs, key = { it.id }) { song ->
                        SongListItem(
                            song = song,
                            isPlaying = playbackState.isPlaying,
                            isCurrentSong = playbackState.currentSong?.id == song.id,
                            onSongClick = { viewModel.playSong(song, smartSongs) },
                            onPlayClick = { viewModel.playSong(song, smartSongs) },
                            onFavoriteClick = { viewModel.toggleFavorite(song) },
                            onAddToPlaylistClick = { viewModel.setSongToAddToPlaylist(song) },
                            onPlayNextClick = { viewModel.playNextInQueue(song) },
                            onAddToQueueClick = { viewModel.addToQueue(listOf(song)) },
                            onEditTagsClick = { viewModel.setSongForTagEditor(song) },
                            onEditArtworkClick = { viewModel.setSongForArtworkEdit(song) },
                            onLyricsClick = { songActions.onOpenLyrics(song) },
                            onFileInfoClick = { viewModel.loadFileDetails(song) },
                            onShareClick = { songActions.onShareSong(song) },
                            onSetRingtoneClick = { songActions.onSetRingtone(song) },
                            onCopyToFolderClick = { songActions.onCopyToFolder(song) },
                            onMoveToFolderClick = { songActions.onMoveToFolder(song) },
                            onDeletePermanentlyClick = { songActions.onRequestDeleteSongs(listOf(song)) }
                        )
                    }
                }
            }
        }
        return
    }

    // Custom Playlist Detail View
    if (selectedPlaylist != null) {
        val playlist = selectedPlaylist!!
        val playlistSongs by viewModel.repository.getSongsForPlaylist(playlist.id).collectAsState(initial = emptyList())

        BackHandler { viewModel.selectPlaylist(null) }

        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag("playlist_detail_screen")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = { viewModel.selectPlaylist(null) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row {
                    IconButton(onClick = { showMultiSongPicker = true }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add Multiple Songs", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { playlistToRename = playlist }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Rename Playlist")
                    }
                    IconButton(onClick = { onExportPlaylistM3u(playlist, playlistSongs) }) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = "Export M3U")
                    }
                    IconButton(onClick = { playlistToDelete = playlist }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete Playlist", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (playlistSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.empty_playlist),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { showMultiSongPicker = true }, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Rounded.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add Songs")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${playlistSongs.size} tracks", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.playSong(playlistSongs.first(), playlistSongs) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Text("Play All")
                        }
                        FilledTonalButton(
                            onClick = { viewModel.shuffleAndPlay(playlistSongs) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Shuffle, null)
                        }
                    }
                }

                val plKey = "playlist_${playlist.id}"
                val savedPl = remember(plKey) { viewModel.getScrollPosition(plKey) }
                val plListState = rememberLazyListState(
                    initialFirstVisibleItemIndex = savedPl.firstVisibleItemIndex,
                    initialFirstVisibleItemScrollOffset = savedPl.firstVisibleItemScrollOffset
                )
                LaunchedEffect(plListState, plKey) {
                    snapshotFlow { plListState.firstVisibleItemIndex to plListState.firstVisibleItemScrollOffset }
                        .collectLatest { (idx, off) -> viewModel.saveScrollPosition(plKey, idx, off) }
                }

                LazyColumn(
                    state = plListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    itemsIndexed(playlistSongs, key = { idx, s -> "${s.id}_$idx" }) { index, song ->
                        val isCurrent = playbackState.currentSong?.id == song.id
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SongListItem(
                                song = song,
                                isPlaying = playbackState.isPlaying,
                                isCurrentSong = isCurrent,
                                onSongClick = { viewModel.playSong(song, playlistSongs) },
                                onPlayClick = { viewModel.playSong(song, playlistSongs) },
                                onFavoriteClick = { viewModel.toggleFavorite(song) },
                                onAddToPlaylistClick = { viewModel.setSongToAddToPlaylist(song) },
                                onPlayNextClick = { viewModel.playNextInQueue(song) },
                                onAddToQueueClick = { viewModel.addToQueue(listOf(song)) },
                                onEditTagsClick = { viewModel.setSongForTagEditor(song) },
                                onEditArtworkClick = { viewModel.setSongForArtworkEdit(song) },
                                onLyricsClick = { songActions.onOpenLyrics(song) },
                                onFileInfoClick = { viewModel.loadFileDetails(song) },
                                onShareClick = { songActions.onShareSong(song) },
                                onSetRingtoneClick = { songActions.onSetRingtone(song) },
                                onCopyToFolderClick = { songActions.onCopyToFolder(song) },
                                onMoveToFolderClick = { songActions.onMoveToFolder(song) },
                                onDeletePermanentlyClick = { songActions.onRequestDeleteSongs(listOf(song)) },
                                modifier = Modifier.weight(1f)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val ids = playlistSongs.map { it.id }.toMutableList()
                                            val item = ids.removeAt(index)
                                            ids.add(index - 1, item)
                                            viewModel.reorderPlaylistSongs(playlist.id, ids)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (index < playlistSongs.lastIndex) {
                                    IconButton(
                                        onClick = {
                                            val ids = playlistSongs.map { it.id }.toMutableList()
                                            val item = ids.removeAt(index)
                                            ids.add(index + 1, item)
                                            viewModel.reorderPlaylistSongs(playlist.id, ids)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            IconButton(
                                onClick = { viewModel.removeSongFromPlaylist(playlist.id, song) },
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RemoveCircleOutline,
                                    contentDescription = "Remove from Playlist",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showMultiSongPicker) {
            MultiSongPickerDialog(
                playlistName = playlist.name,
                allSongs = allSongs,
                existingSongIds = playlistSongs.map { it.id }.toSet(),
                onDismiss = { showMultiSongPicker = false },
                onConfirm = { chosen ->
                    viewModel.addSongsToPlaylist(playlist, chosen)
                    showMultiSongPicker = false
                }
            )
        }

        if (playlistToRename != null) {
            RenamePlaylistDialog(
                currentName = playlistToRename!!.name,
                onDismiss = { playlistToRename = null },
                onConfirm = { newName ->
                    viewModel.renamePlaylist(playlistToRename!!.id, newName)
                    playlistToRename = null
                }
            )
        }

        if (playlistToDelete != null) {
            DeletePlaylistConfirmDialog(
                playlist = playlistToDelete!!,
                onConfirm = {
                    viewModel.deletePlaylist(playlistToDelete!!)
                    playlistToDelete = null
                },
                onDismiss = { playlistToDelete = null }
            )
        }
        return
    }

    // Main Playlists Hub: Smart Playlists + Custom Playlists + M3U Import
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreatePlaylistDialog(true) },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 60.dp)
                    .testTag("create_playlist_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.create_playlist))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("playlists_screen"),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.nav_playlists),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    OutlinedButton(onClick = onImportPlaylistM3u, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Rounded.FileDownload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import M3U")
                    }
                }
            }

            // Smart Playlists Section
            item {
                Text(
                    text = "Smart Playlists",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(SmartPlaylistType.entries) { smart ->
                        val count = when (smart) {
                            SmartPlaylistType.RECENTLY_PLAYED -> recentlyPlayed.size
                            SmartPlaylistType.MOST_PLAYED -> mostPlayed.size
                            SmartPlaylistType.RECENTLY_ADDED -> recentlyAdded.size
                            SmartPlaylistType.FAVORITES -> favorites.size
                        }
                        Card(
                            modifier = Modifier
                                .width(156.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewModel.selectSmartPlaylist(smart) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.height(8.dp))
                                Text(smart.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$count tracks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f))
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Your Playlists",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            if (playlists.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(10.dp))
                        Text("No playlists created yet", style = MaterialTheme.typography.titleMedium)
                        Text("Tap + to create a playlist or Import M3U", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    val count by viewModel.repository.getPlaylistSongCount(playlist.id).collectAsState(initial = 0)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.selectPlaylist(playlist) }
                            .testTag("playlist_item_${playlist.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("$count tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row {
                                IconButton(onClick = { playlistToRename = playlist }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Rename")
                                }
                                IconButton(onClick = { playlistToDelete = playlist }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (playlistToRename != null) {
        RenamePlaylistDialog(
            currentName = playlistToRename!!.name,
            onDismiss = { playlistToRename = null },
            onConfirm = { newName ->
                viewModel.renamePlaylist(playlistToRename!!.id, newName)
                playlistToRename = null
            }
        )
    }

    if (playlistToDelete != null) {
        DeletePlaylistConfirmDialog(
            playlist = playlistToDelete!!,
            onConfirm = {
                viewModel.deletePlaylist(playlistToDelete!!)
                playlistToDelete = null
            },
            onDismiss = { playlistToDelete = null }
        )
    }
}

@Composable
private fun DeletePlaylistConfirmDialog(
    playlist: Playlist,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Playlist") },
        text = { Text("Are you sure you want to delete \"${playlist.name}\"?") },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
