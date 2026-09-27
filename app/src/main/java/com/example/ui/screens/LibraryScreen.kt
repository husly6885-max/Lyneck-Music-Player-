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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.model.Song
import com.example.ui.LibraryTab
import com.example.ui.MusicViewModel
import com.example.ui.components.EmptyMusicState
import com.example.ui.components.SongListItem

@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val songs by viewModel.allSongs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val selectedTab by viewModel.selectedLibraryTab.collectAsState()

    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()

    val favorites by viewModel.favoriteSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayedSongs.collectAsState()

    // Drill down views: Album
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
            onBack = { viewModel.selectAlbum(null) },
            onSongClick = { song -> viewModel.playSong(song, albumSongs) },
            onPlayAll = { if (albumSongs.isNotEmpty()) viewModel.playSong(albumSongs.first(), albumSongs) },
            onFavoriteClick = { viewModel.toggleFavorite(it) },
            onAddToPlaylist = { viewModel.setSongToAddToPlaylist(it) }
        )
        return
    }

    // Drill down views: Artist
    if (selectedArtist != null) {
        val artist = selectedArtist!!
        val artistSongs = remember(songs, artist) {
            songs.filter { it.artist.equals(artist.name, ignoreCase = true) }
        }
        BackHandler { viewModel.selectArtist(null) }
        ArtistDetailView(
            artist = artist,
            songs = artistSongs,
            currentSong = playbackState.currentSong,
            isPlaying = playbackState.isPlaying,
            onBack = { viewModel.selectArtist(null) },
            onSongClick = { song -> viewModel.playSong(song, artistSongs) },
            onPlayAll = { if (artistSongs.isNotEmpty()) viewModel.playSong(artistSongs.first(), artistSongs) },
            onFavoriteClick = { viewModel.toggleFavorite(it) },
            onAddToPlaylist = { viewModel.setSongToAddToPlaylist(it) }
        )
        return
    }

    // Drill down views: Folder
    if (selectedFolder != null) {
        val folder = selectedFolder!!
        val folderSongs = remember(songs, folder) {
            songs.filter { it.folderPath == folder.path }
        }
        BackHandler { viewModel.selectFolder(null) }
        FolderDetailView(
            folder = folder,
            songs = folderSongs,
            currentSong = playbackState.currentSong,
            isPlaying = playbackState.isPlaying,
            onBack = { viewModel.selectFolder(null) },
            onSongClick = { song -> viewModel.playSong(song, folderSongs) },
            onPlayAll = { if (folderSongs.isNotEmpty()) viewModel.playSong(folderSongs.first(), folderSongs) },
            onFavoriteClick = { viewModel.toggleFavorite(it) },
            onAddToPlaylist = { viewModel.setSongToAddToPlaylist(it) }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        // App header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${songs.size} offline tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { viewModel.rescanMusic() },
                enabled = !isScanning,
                modifier = Modifier.testTag("rescan_header_button")
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Rescan Library",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Tabs: Songs, Artists, Albums, Folders, Favorites, Recently Played
        val tabs = listOf(
            LibraryTab.SONGS to stringResource(R.string.tab_songs),
            LibraryTab.ARTISTS to stringResource(R.string.tab_artists),
            LibraryTab.ALBUMS to stringResource(R.string.tab_albums),
            LibraryTab.FOLDERS to stringResource(R.string.folders),
            LibraryTab.FAVORITES to stringResource(R.string.tab_favorites),
            LibraryTab.RECENTLY_PLAYED to stringResource(R.string.recently_played)
        )

        ScrollableTabRow(
            selectedTabIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0),
            edgePadding = 16.dp,
            divider = {},
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEach { (tabKey, title) ->
                Tab(
                    selected = selectedTab == tabKey,
                    onClick = { viewModel.setLibraryTab(tabKey) },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selectedTab == tabKey) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (songs.isEmpty() && !isScanning) {
            EmptyMusicState(
                isScanning = isScanning,
                onRescanClick = { viewModel.rescanMusic() }
            )
            return
        }

        // Tab Content
        when (selectedTab) {
            LibraryTab.SONGS -> {
                SongsListTab(
                    songs = songs,
                    currentSong = playbackState.currentSong,
                    isPlaying = playbackState.isPlaying,
                    onSongClick = { song -> viewModel.playSong(song, songs) },
                    onShuffleClick = {
                        if (songs.isNotEmpty()) {
                            viewModel.toggleShuffle()
                            viewModel.playSong(songs.shuffled().first(), songs)
                        }
                    },
                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                    onAddToPlaylist = { viewModel.setSongToAddToPlaylist(it) }
                )
            }
            LibraryTab.ARTISTS -> {
                val artists = remember(songs) { viewModel.repository.getArtists() }
                ArtistsGridTab(
                    artists = artists,
                    onArtistClick = { viewModel.selectArtist(it) }
                )
            }
            LibraryTab.ALBUMS -> {
                val albums = remember(songs) { viewModel.repository.getAlbums() }
                AlbumsGridTab(
                    albums = albums,
                    onAlbumClick = { viewModel.selectAlbum(it) }
                )
            }
            LibraryTab.FOLDERS -> {
                val folders = remember(songs) { viewModel.repository.getFolders() }
                FoldersListTab(
                    folders = folders,
                    onFolderClick = { viewModel.selectFolder(it) }
                )
            }
            LibraryTab.FAVORITES -> {
                if (favorites.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.empty_favorites),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    SongsListTab(
                        songs = favorites,
                        currentSong = playbackState.currentSong,
                        isPlaying = playbackState.isPlaying,
                        onSongClick = { song -> viewModel.playSong(song, favorites) },
                        onShuffleClick = {
                            if (favorites.isNotEmpty()) {
                                viewModel.toggleShuffle()
                                viewModel.playSong(favorites.shuffled().first(), favorites)
                            }
                        },
                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                        onAddToPlaylist = { viewModel.setSongToAddToPlaylist(it) }
                    )
                }
            }
            LibraryTab.RECENTLY_PLAYED -> {
                if (recentlyPlayed.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recently played songs yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    SongsListTab(
                        songs = recentlyPlayed,
                        currentSong = playbackState.currentSong,
                        isPlaying = playbackState.isPlaying,
                        onSongClick = { song -> viewModel.playSong(song, recentlyPlayed) },
                        onShuffleClick = {
                            if (recentlyPlayed.isNotEmpty()) {
                                viewModel.toggleShuffle()
                                viewModel.playSong(recentlyPlayed.shuffled().first(), recentlyPlayed)
                            }
                        },
                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                        onAddToPlaylist = { viewModel.setSongToAddToPlaylist(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SongsListTab(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onShuffleClick: () -> Unit,
    onFavoriteClick: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${songs.size} tracks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FilledTonalButton(
                    onClick = onShuffleClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle All")
                }
            }
        }

        items(songs, key = { it.id }) { song ->
            val isCurrent = currentSong?.id == song.id
            SongListItem(
                song = song,
                isPlaying = isPlaying,
                isCurrentSong = isCurrent,
                onSongClick = { onSongClick(song) },
                onFavoriteClick = { onFavoriteClick(song) },
                onAddToPlaylistClick = { onAddToPlaylist(song) }
            )
        }
    }
}

@Composable
private fun AlbumsGridTab(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit
) {
    LazyVerticalGrid(
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
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onAlbumClick(album) }
                    .testTag("album_card_${album.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = album.albumArtUri,
                            contentDescription = album.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }

                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${album.artist} • ${album.songCount} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistsGridTab(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
    ) {
        items(artists, key = { it.name }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onArtistClick(artist) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${artist.songCount} songs • ${artist.albumCount} albums",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FoldersListTab(
    folders: List<Folder>,
    onFolderClick: (Folder) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
    ) {
        items(folders, key = { it.path }) { folder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onFolderClick(folder) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${folder.songCount} songs • ${folder.path}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// Drill-Down Views
@Composable
private fun AlbumDetailView(
    album: Album,
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onFavoriteClick: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyColumn(
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
                            .size(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = album.albumArtUri,
                            contentDescription = album.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = album.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onPlayAll,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play All (${songs.size})")
                        }
                    }
                }
            }

            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isPlaying = isPlaying,
                    isCurrentSong = currentSong?.id == song.id,
                    onSongClick = { onSongClick(song) },
                    onFavoriteClick = { onFavoriteClick(song) },
                    onAddToPlaylistClick = { onAddToPlaylist(song) }
                )
            }
        }
    }
}

@Composable
private fun ArtistDetailView(
    artist: Artist,
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onFavoriteClick: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyColumn(
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
                        text = "${songs.size} tracks by ${artist.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = onPlayAll,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play All")
                    }
                }
            }

            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isPlaying = isPlaying,
                    isCurrentSong = currentSong?.id == song.id,
                    onSongClick = { onSongClick(song) },
                    onFavoriteClick = { onFavoriteClick(song) },
                    onAddToPlaylistClick = { onAddToPlaylist(song) }
                )
            }
        }
    }
}

@Composable
private fun FolderDetailView(
    folder: Folder,
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onFavoriteClick: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyColumn(
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${songs.size} audio files",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = onPlayAll,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play All")
                    }
                }
            }

            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isPlaying = isPlaying,
                    isCurrentSong = currentSong?.id == song.id,
                    onSongClick = { onSongClick(song) },
                    onFavoriteClick = { onFavoriteClick(song) },
                    onAddToPlaylistClick = { onAddToPlaylist(song) }
                )
            }
        }
    }
}
