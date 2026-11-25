package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SearchScope
import com.example.ui.MusicViewModel
import com.example.ui.components.SongActionHandlers
import com.example.ui.components.SongListItem
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    songActions: SongActionHandlers,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchScope by viewModel.searchScope.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    val savedPos = remember { viewModel.getScrollPosition("search_results") }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedPos.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = savedPos.firstVisibleItemScrollOffset
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (idx, off) ->
                viewModel.saveScrollPosition("search_results", idx, off)
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_screen")
    ) {
        Text(
            text = stringResource(R.string.nav_search),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("search_input_field")
        )

        // Advanced search scope filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchScope.entries.forEach { scope ->
                FilterChip(
                    selected = searchScope == scope,
                    onClick = { viewModel.setSearchScope(scope) },
                    label = { Text(scope.label) }
                )
            }
        }

        if (searchQuery.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Search by title, artist, album, genre, or folder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (searchResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_search_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${searchResults.size} matches in ${searchScope.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp)
            ) {
                items(searchResults, key = { it.id }) { song ->
                    val isCurrent = playbackState.currentSong?.id == song.id
                    SongListItem(
                        song = song,
                        isPlaying = playbackState.isPlaying,
                        isCurrentSong = isCurrent,
                        onSongClick = { viewModel.playSong(song, searchResults) },
                        onPlayClick = { viewModel.playSong(song, searchResults) },
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
}
