package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.Folder
import com.example.data.model.Genre
import com.example.data.model.Song
import com.example.data.model.SortOrder
import com.example.ui.LibraryTab
import com.example.ui.MusicViewModel
import com.example.ui.NavigationScreen
import com.example.ui.components.AlphabetFastScroller
import com.example.ui.components.EmptyMusicState
import com.example.ui.components.SongActionHandlers
import com.example.ui.components.SongListItem
import kotlinx.coroutines.flow.collectLatest

@Composable
private fun rememberPersistentLazyListState(
    scrollKey: String,
    viewModel: MusicViewModel
): LazyListState {
    val saved = remember(scrollKey) { viewModel.getScrollPosition(scrollKey) }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = saved.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = saved.firstVisibleItemScrollOffset
    )
    LaunchedEffect(state, scrollKey) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collectLatest { (idx, offset) ->
                viewModel.saveScrollPosition(scrollKey, idx, offset)
            }
    }
    return state
}

@Composable
private fun rememberPersistentLazyGridState(
    scrollKey: String,
    viewModel: MusicViewModel
): LazyGridState {
    val saved = remember(scrollKey) { viewModel.getScrollPosition(scrollKey) }
    val state = rememberLazyGridState(
        initialFirstVisibleItemIndex = saved.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = saved.firstVisibleItemScrollOffset
    )
    LaunchedEffect(state, scrollKey) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collectLatest { (idx, offset) ->
                viewModel.saveScrollPosition(scrollKey, idx, offset)
            }
    }
    return state
}

@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    songActions: SongActionHandlers,
    modifier: Modifier = Modifier
) {
    val songs by viewModel.allSongs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val selectedTab by viewModel.selectedLibraryTab.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val selectedSongIds by viewModel.selectedSongIds.collectAsState()

    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()

    val favorites by viewModel.favoriteSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayedSongs.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedSongs.collectAsState()
    val mostPlayed by viewModel.mostPlayedSongs.collectAsState()

    val isSelectionMode = selectedSongIds.isNotEmpty()
    var showSortMenu by remember { mutableStateOf(false) }

    if (isSelectionMode) {
        BackHandler { viewModel.clearSongSelection() }
    }

    // Drill-down: Album (opens song list — never auto-plays)
    if (selectedAlbum != null) {
        val album = selectedAlbum!!
        val albumSongs = remember(songs, album) {
            songs.filter { it.albumId == album.id || it.album.equals(album.name, ignoreCase = true) }
        }
        BackHandler { viewModel.selectAlbum(null) }
        AlbumDetailView(
            album = album,
            songs = albumSongs,
            currentSong = playbackState.currentSong,
            isPlaying = playbackState.isPlaying,
            viewModel = viewModel,
            songActions = songActions,
            onBack = { viewModel.selectAlbum(null) }
        )
        return
    }

    // Drill-down: Artist (opens song list — never auto-plays)
    if (selectedArtist != null) {
        val artist = selectedArtist!!
        val artistSongs = remember(songs, artist) {
            songs.filter { it.artist.equals(artist.name, ignoreCase = true) }
        }
        BackHandler { viewModel.selectArtist(null) }
        SimpleGroupDetailView(
            scrollKey = "detail_artist_${artist.name}",
            title = artist.name,
            subtitle = "${artistSongs.size} tracks",
            songs = artistSongs,
            currentSong = playbackState.currentSong,
            isPlaying = playbackState.isPlaying,
            viewModel = viewModel,
            songActions = songActions,
            onBack = { viewModel.selectArtist(null) }
        )
        return
    }

    // Drill-down: Genre
    if (selectedGenre != null) {
        val genre = selectedGenre!!
        val genreSongs = remember(songs, genre) {
            songs.filter { it.genre.equals(genre.name, ignoreCase = true) }
        }
        BackHandler { viewModel.selectGenre(null) }
        SimpleGroupDetailView(
            scrollKey = "detail_genre_${genre.name}",
            title = genre.name,
            subtitle = "${genreSongs.size} tracks in genre",
            songs = genreSongs,
            currentSong = playbackState.currentSong,
            isPlaying = playbackState.isPlaying,
            viewModel = viewModel,
            songActions = songActions,
            onBack = { viewModel.selectGenre(null) }
        )
        return
    }

    // Drill-down: Folder
    if (selectedFolder != null) {
        val folder = selectedFolder!!
        val folderSongs = remember(songs, folder) {
            songs.filter { it.folderPath == folder.path }
        }
        BackHandler { viewModel.selectFolder(null) }
        SimpleGroupDetailView(
            scrollKey = "detail_folder_${folder.path}",
            title = folder.name,
            subtitle = "${folderSongs.size} tracks • ${folder.path}",
            songs = folderSongs,
            currentSong = playbackState.currentSong,
            isPlaying = playbackState.isPlaying,
            viewModel = viewModel,
            songActions = songActions,
            onBack = { viewModel.selectFolder(null) }
        )
        return
    }

    val activeSongsForTab = when (selectedTab) {
        LibraryTab.FAVORITES -> favorites
        LibraryTab.RECENTLY_PLAYED -> recentlyPlayed
        LibraryTab.RECENTLY_ADDED -> recentlyAdded
        LibraryTab.MOST_PLAYED -> mostPlayed
        else -> songs
    }

    val headerTitle = when (selectedTab) {
        LibraryTab.SONGS -> stringResource(R.string.all_songs)
        LibraryTab.ARTISTS -> stringResource(R.string.tab_artists)
        LibraryTab.ALBUMS -> stringResource(R.string.tab_albums)
        LibraryTab.FOLDERS -> stringResource(R.string.folders)
        LibraryTab.GENRES -> stringResource(R.string.tab_genres)
        LibraryTab.RECENTLY_ADDED -> stringResource(R.string.recently_added)
        LibraryTab.RECENTLY_PLAYED -> stringResource(R.string.recently_played)
        LibraryTab.MOST_PLAYED -> stringResource(R.string.most_played)
        LibraryTab.FAVORITES -> stringResource(R.string.tab_favorites)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("library_screen")
    ) {
        // Multi-Select Contextual Action Bar OR Screenshot-Aligned Header
        if (isSelectionMode) {
            val selectedSongs = remember(songs, selectedSongIds) {
                songs.filter { it.id in selectedSongIds }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.clearSongSelection() },
                            modifier = Modifier.testTag("exit_selection_button")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Unselect all")
                        }
                        Text(
                            text = "${selectedSongIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.selectAllSongs(activeSongsForTab) },
                            modifier = Modifier.testTag("select_all_button")
                        ) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = "Select All")
                        }
                        TextButton(
                            onClick = { viewModel.clearSongSelection() },
                            modifier = Modifier.testTag("unselect_all_button")
                        ) {
                            Text("Unselect")
                        }
                        IconButton(onClick = {
                            viewModel.addToQueue(selectedSongs)
                            viewModel.clearSongSelection()
                        }) {
                            Icon(Icons.Rounded.AddToQueue, contentDescription = "Add Selected to Queue")
                        }
                        IconButton(onClick = {
                            viewModel.setSongsToAddToPlaylist(selectedSongs)
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add Selected to Playlist")
                        }
                        IconButton(onClick = {
                            songActions.onRequestDeleteSongs(selectedSongs)
                        }) {
                            Icon(
                                Icons.Rounded.DeleteForever,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        } else {
            // Top bar matching screenshot: Back/Library icon + "All songs" + Refresh + Search
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedTab != LibraryTab.SONGS) {
                                    viewModel.setLibraryTab(LibraryTab.SONGS)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selectedTab != LibraryTab.SONGS) {
                                    Icons.AutoMirrored.Rounded.ArrowBack
                                } else {
                                    Icons.Rounded.LibraryMusic
                                },
                                contentDescription = if (selectedTab != LibraryTab.SONGS) "Back to All Songs" else "Library",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.rescanMusic() },
                            enabled = !isScanning,
                            modifier = Modifier.testTag("rescan_header_button")
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Rescan Library",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.setNavigationScreen(NavigationScreen.SEARCH) },
                            modifier = Modifier.testTag("header_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search Music",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Screenshot Segmented Pill Switcher: Songs | Artists | Albums | Folders
        val primaryTabs = listOf(
            LibraryTab.SONGS to stringResource(R.string.tab_songs),
            LibraryTab.ARTISTS to stringResource(R.string.tab_artists),
            LibraryTab.ALBUMS to stringResource(R.string.tab_albums),
            LibraryTab.FOLDERS to stringResource(R.string.folders)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.85f)),
                        RoundedCornerShape(10.dp)
                    )
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                primaryTabs.forEachIndexed { index, (tabKey, title) ->
                    val isSelected = selectedTab == tabKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { viewModel.setLibraryTab(tabKey) }
                            .testTag("segmented_tab_${tabKey.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index < primaryTabs.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                        )
                    }
                }
            }
        }

        // Compact secondary pill bar for Favorites, Smart Collections & Genres
        val secondaryTabs = listOf(
            LibraryTab.FAVORITES to stringResource(R.string.tab_favorites),
            LibraryTab.RECENTLY_PLAYED to stringResource(R.string.recently_played),
            LibraryTab.RECENTLY_ADDED to stringResource(R.string.recently_added),
            LibraryTab.MOST_PLAYED to stringResource(R.string.most_played),
            LibraryTab.GENRES to stringResource(R.string.tab_genres)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            secondaryTabs.forEach { (tabKey, title) ->
                val isSelected = selectedTab == tabKey
                Surface(
                    onClick = { viewModel.setLibraryTab(tabKey) },
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (songs.isEmpty() && !isScanning) {
            EmptyMusicState(
                isScanning = isScanning,
                onRescanClick = { viewModel.rescanMusic() }
            )
            return
        }

        val sortDropdown: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (activeSongsForTab.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            if (isSelectionMode) viewModel.clearSongSelection()
                            else viewModel.toggleSongSelection(activeSongsForTab.first().id)
                        },
                        modifier = Modifier.testTag("toggle_multiselect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Checklist,
                            contentDescription = "Select Multiple",
                            tint = if (isSelectionMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.testTag("sort_songs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapVert,
                            contentDescription = "Sort Songs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = order.label,
                                        fontWeight = if (order == settings.sortOrder) FontWeight.Bold else FontWeight.Normal,
                                        color = if (order == settings.sortOrder) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        when (selectedTab) {
            LibraryTab.SONGS -> SongsListTab(
                scrollKey = "lib_songs",
                songs = songs,
                currentSong = playbackState.currentSong,
                isPlaying = playbackState.isPlaying,
                selectedSongIds = selectedSongIds,
                isSelectionMode = isSelectionMode,
                showAlphaIndex = true,
                viewModel = viewModel,
                songActions = songActions,
                trailingControls = sortDropdown,
                onShuffleClick = { viewModel.shuffleAndPlay(songs) }
            )

            LibraryTab.ALBUMS -> {
                val albums = remember(songs) { viewModel.repository.getAlbums(songs) }
                AlbumsGridTab(
                    scrollKey = "lib_albums",
                    albums = albums,
                    viewModel = viewModel,
                    onAlbumClick = { viewModel.selectAlbum(it) }
                )
            }

            LibraryTab.ARTISTS -> {
                val artists = remember(songs) { viewModel.repository.getArtists(songs) }
                ArtistsListTab(
                    scrollKey = "lib_artists",
                    artists = artists,
                    viewModel = viewModel,
                    onArtistClick = { viewModel.selectArtist(it) }
                )
            }

            LibraryTab.GENRES -> {
                val genres = remember(songs) { viewModel.repository.getGenres(songs) }
                GenresListTab(
                    scrollKey = "lib_genres",
                    genres = genres,
                    viewModel = viewModel,
                    onGenreClick = { viewModel.selectGenre(it) }
                )
            }

            LibraryTab.FOLDERS -> {
                val folders = remember(songs) { viewModel.repository.getFolders(songs) }
                FoldersListTab(
                    scrollKey = "lib_folders",
                    folders = folders,
                    viewModel = viewModel,
                    onFolderClick = { viewModel.selectFolder(it) }
                )
            }

            LibraryTab.RECENTLY_ADDED -> SongsListTab(
                scrollKey = "lib_rec_added",
                songs = recentlyAdded,
                currentSong = playbackState.currentSong,
                isPlaying = playbackState.isPlaying,
                selectedSongIds = selectedSongIds,
                isSelectionMode = isSelectionMode,
                showAlphaIndex = false,
                viewModel = viewModel,
                songActions = songActions,
                trailingControls = sortDropdown,
                onShuffleClick = { viewModel.shuffleAndPlay(recentlyAdded) }
            )

            LibraryTab.RECENTLY_PLAYED -> {
                if (recentlyPlayed.isEmpty()) {
                    CenteredEmptyMessage("No recently played songs yet")
                } else {
                    SongsListTab(
                        scrollKey = "lib_rec_played",
                        songs = recentlyPlayed,
                        currentSong = playbackState.currentSong,
                        isPlaying = playbackState.isPlaying,
                        selectedSongIds = selectedSongIds,
                        isSelectionMode = isSelectionMode,
                        showAlphaIndex = false,
                        viewModel = viewModel,
                        songActions = songActions,
                        trailingControls = sortDropdown,
                        onShuffleClick = { viewModel.shuffleAndPlay(recentlyPlayed) }
                    )
                }
            }

            LibraryTab.MOST_PLAYED -> {
                if (mostPlayed.isEmpty()) {
                    CenteredEmptyMessage("Play tracks to build your Most Played list")
                } else {
                    SongsListTab(
                        scrollKey = "lib_most_played",
                        songs = mostPlayed,
                        currentSong = playbackState.currentSong,
                        isPlaying = playbackState.isPlaying,
                        selectedSongIds = selectedSongIds,
                        isSelectionMode = isSelectionMode,
                        showAlphaIndex = false,
                        viewModel = viewModel,
                        songActions = songActions,
                        trailingControls = sortDropdown,
                        onShuffleClick = { viewModel.shuffleAndPlay(mostPlayed) }
                    )
                }
            }

            LibraryTab.FAVORITES -> {
                if (favorites.isEmpty()) {
                    CenteredEmptyMessage(stringResource(R.string.empty_favorites))
                } else {
                    SongsListTab(
                        scrollKey = "lib_favorites",
                        songs = favorites,
                        currentSong = playbackState.currentSong,
                        isPlaying = playbackState.isPlaying,
                        selectedSongIds = selectedSongIds,
                        isSelectionMode = isSelectionMode,
                        showAlphaIndex = false,
                        viewModel = viewModel,
                        songActions = songActions,
                        trailingControls = sortDropdown,
                        onShuffleClick = { viewModel.shuffleAndPlay(favorites) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredEmptyMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SongsListTab(
    scrollKey: String,
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    selectedSongIds: Set<Long>,
    isSelectionMode: Boolean,
    showAlphaIndex: Boolean,
    viewModel: MusicViewModel,
    songActions: SongActionHandlers,
    trailingControls: @Composable () -> Unit,
    onShuffleClick: () -> Unit
) {
    val listState = rememberPersistentLazyListState(scrollKey = scrollKey, viewModel = viewModel)
    val showScroller = showAlphaIndex && songs.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = if (showScroller) 26.dp else 0.dp,
                bottom = 120.dp,
                top = 2.dp
            )
        ) {
            // Shuffle All row matching screenshot ("(▶) Shuffle all" + Sort/Select controls)
            item(key = "shuffle_all_header") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onShuffleClick)
                                .padding(vertical = 4.dp)
                                .testTag("shuffle_all_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Shuffle all",
                                    tint = MaterialTheme.colorScheme.background,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Shuffle all (${songs.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        trailingControls()
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            items(songs, key = { it.id }) { song ->
                val isCurrent = currentSong?.id == song.id
                SongListItem(
                    song = song,
                    isPlaying = isPlaying,
                    isCurrentSong = isCurrent,
                    isSelectionMode = isSelectionMode,
                    isSelected = song.id in selectedSongIds,
                    onSongClick = {
                        if (isSelectionMode) viewModel.toggleSongSelection(song.id)
                        else viewModel.playSong(song, songs)
                    },
                    onPlayClick = { viewModel.playSong(song, songs) },
                    onLongClick = { viewModel.toggleSongSelection(song.id) },
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

        if (showScroller) {
            AlphabetFastScroller(
                songs = songs,
                listState = listState,
                headerCount = 1,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = 12.dp, bottom = 96.dp, end = 2.dp)
            )
        }
    }
}

@Composable
private fun AlbumsGridTab(
    scrollKey: String,
    albums: List<Album>,
    viewModel: MusicViewModel,
    onAlbumClick: (Album) -> Unit
) {
    val gridState = rememberPersistentLazyGridState(scrollKey = scrollKey, viewModel = viewModel)
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onAlbumClick(album) }
                    .testTag("album_card_${album.id}"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        AsyncImage(
                            model = album.albumArtUri,
                            contentDescription = album.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(album.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${album.artist} • ${album.songCount} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistsListTab(
    scrollKey: String,
    artists: List<Artist>,
    viewModel: MusicViewModel,
    onArtistClick: (Artist) -> Unit
) {
    val listState = rememberPersistentLazyListState(scrollKey = scrollKey, viewModel = viewModel)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp)
    ) {
        items(artists, key = { it.name }) { artist ->
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArtistClick(artist) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("artist_row_${artist.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(artist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${artist.songCount} songs • ${artist.albumCount} albums", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 74.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun GenresListTab(
    scrollKey: String,
    genres: List<Genre>,
    viewModel: MusicViewModel,
    onGenreClick: (Genre) -> Unit
) {
    val listState = rememberPersistentLazyListState(scrollKey = scrollKey, viewModel = viewModel)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp)
    ) {
        items(genres, key = { it.name }) { genre ->
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGenreClick(genre) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Category, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(genre.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("${genre.songCount} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 74.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun FoldersListTab(
    scrollKey: String,
    folders: List<Folder>,
    viewModel: MusicViewModel,
    onFolderClick: (Folder) -> Unit
) {
    val listState = rememberPersistentLazyListState(scrollKey = scrollKey, viewModel = viewModel)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp)
    ) {
        items(folders, key = { it.path }) { folder ->
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFolderClick(folder) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${folder.songCount} songs • ${folder.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 74.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun AlbumDetailView(
    album: Album,
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    viewModel: MusicViewModel,
    songActions: SongActionHandlers,
    onBack: () -> Unit
) {
    val listState = rememberPersistentLazyListState(scrollKey = "detail_album_${album.id}", viewModel = viewModel)
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(album.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Album, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        AsyncImage(model = album.albumArtUri, contentDescription = album.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(album.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(album.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { if (songs.isNotEmpty()) viewModel.playSong(songs.first(), songs) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null)
                                Text("Play (${songs.size})")
                            }
                            FilledTonalButton(
                                onClick = { viewModel.shuffleAndPlay(songs) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Rounded.Shuffle, null)
                            }
                        }
                    }
                }
            }

            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isPlaying = isPlaying,
                    isCurrentSong = currentSong?.id == song.id,
                    onSongClick = { viewModel.playSong(song, songs) },
                    onPlayClick = { viewModel.playSong(song, songs) },
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

@Composable
private fun SimpleGroupDetailView(
    scrollKey: String,
    title: String,
    subtitle: String,
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    viewModel: MusicViewModel,
    songActions: SongActionHandlers,
    onBack: () -> Unit
) {
    val listState = rememberPersistentLazyListState(scrollKey = scrollKey, viewModel = viewModel)
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { if (songs.isNotEmpty()) viewModel.playSong(songs.first(), songs) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Text("Play")
                        }
                        FilledTonalButton(
                            onClick = { viewModel.shuffleAndPlay(songs) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.Shuffle, null)
                        }
                    }
                }
            }

            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isPlaying = isPlaying,
                    isCurrentSong = currentSong?.id == song.id,
                    onSongClick = { viewModel.playSong(song, songs) },
                    onPlayClick = { viewModel.playSong(song, songs) },
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
