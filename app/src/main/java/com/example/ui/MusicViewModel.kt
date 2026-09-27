package com.example.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LyneckDatabase
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.Folder
import com.example.data.model.Playlist
import com.example.data.model.Song
import com.example.data.repository.MusicRepository
import com.example.data.scanner.MediaScanner
import com.example.playback.MusicPlayerManager
import com.example.playback.PlaybackState
import com.example.service.MusicService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class LibraryTab {
    SONGS,
    ARTISTS,
    ALBUMS,
    FOLDERS,
    RECENTLY_PLAYED,
    FAVORITES
}

enum class NavigationScreen {
    LIBRARY,
    SEARCH,
    PLAYLISTS,
    SETTINGS
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("lyneck_prefs", Context.MODE_PRIVATE)
    private val database = LyneckDatabase.getDatabase(application)
    private val mediaScanner = MediaScanner(application)
    val repository = MusicRepository(mediaScanner, database.musicDao())

    // Player manager reference (either from bound service or standalone fallback)
    private val fallbackPlayerManager = MusicPlayerManager(application)
    private var boundService: MusicService? = null
    private var isServiceBound = false

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState = _playbackState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    // Navigation and Tab States
    private val _currentScreen = MutableStateFlow(NavigationScreen.LIBRARY)
    val currentScreen = _currentScreen.asStateFlow()

    private val _selectedLibraryTab = MutableStateFlow(LibraryTab.SONGS)
    val selectedLibraryTab = _selectedLibraryTab.asStateFlow()

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Selected items for detail drill-down
    private val _selectedAlbum = MutableStateFlow<Album?>(null)
    val selectedAlbum = _selectedAlbum.asStateFlow()

    private val _selectedArtist = MutableStateFlow<Artist?>(null)
    val selectedArtist = _selectedArtist.asStateFlow()

    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder = _selectedFolder.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist = _selectedPlaylist.asStateFlow()

    // Dialog & Sheet States
    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    val showCreatePlaylistDialog = _showCreatePlaylistDialog.asStateFlow()

    private val _songToAddToPlaylist = MutableStateFlow<Song?>(null)
    val songToAddToPlaylist = _songToAddToPlaylist.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded = _isNowPlayingExpanded.asStateFlow()

    // Permission state
    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission = _hasStoragePermission.asStateFlow()

    // Theme mode
    private val _themeMode = MutableStateFlow(
        AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name)
    )
    val themeMode = _themeMode.asStateFlow()

    // Data streams
    val allSongs = repository.allSongs
    val isScanning = repository.isScanning
    val favoriteSongs = repository.favoriteSongs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentlyPlayedSongs = repository.recentlyPlayedSongs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered search results
    val searchResults = combine(allSongs, _searchQuery) { songs, query ->
        if (query.isBlank()) {
            emptyList()
        } else {
            val q = query.trim().lowercase()
            songs.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicService.MusicBinder) {
                boundService = service.getService()
                isServiceBound = true
                observePlayerManager(boundService!!.playerManager)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            isServiceBound = false
            observePlayerManager(fallbackPlayerManager)
        }
    }

    init {
        bindMusicService()
        observePlayerManager(fallbackPlayerManager)
    }

    private fun bindMusicService() {
        val intent = Intent(getApplication(), MusicService::class.java)
        try {
            getApplication<Application>().startService(intent)
            getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun activePlayerManager(): MusicPlayerManager {
        return boundService?.playerManager ?: fallbackPlayerManager
    }

    private fun observePlayerManager(manager: MusicPlayerManager) {
        viewModelScope.launch {
            manager.playbackState.collect { state ->
                _playbackState.value = state
            }
        }
        viewModelScope.launch {
            manager.errorEvents.collect { errorMsg ->
                _userMessage.emit("Playback error: $errorMsg")
            }
        }
    }

    fun onPermissionGranted() {
        _hasStoragePermission.value = true
        rescanMusic()
    }

    fun onPermissionDenied() {
        _hasStoragePermission.value = false
    }

    fun rescanMusic() {
        viewModelScope.launch {
            val songs = repository.scanSongs()
            _userMessage.emit(
                if (songs.isNotEmpty()) "Found ${songs.size} music tracks" else "No music files found"
            )
        }
    }

    fun setNavigationScreen(screen: NavigationScreen) {
        _currentScreen.value = screen
    }

    fun setLibraryTab(tab: LibraryTab) {
        _selectedLibraryTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectAlbum(album: Album?) {
        _selectedAlbum.value = album
    }

    fun selectArtist(artist: Artist?) {
        _selectedArtist.value = artist
    }

    fun selectFolder(folder: Folder?) {
        _selectedFolder.value = folder
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _isNowPlayingExpanded.value = expanded
    }

    fun showCreatePlaylistDialog(show: Boolean) {
        _showCreatePlaylistDialog.value = show
    }

    fun setSongToAddToPlaylist(song: Song?) {
        _songToAddToPlaylist.value = song
    }

    // Playback Controls
    fun playSong(song: Song, contextList: List<Song> = emptyList()) {
        val list = if (contextList.isNotEmpty()) contextList else listOf(song)
        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        activePlayerManager().playSongList(list, index)
        viewModelScope.launch {
            repository.markRecentlyPlayed(song.id)
        }
    }

    fun playQueueIndex(index: Int) {
        activePlayerManager().playAtIndex(index)
    }

    fun togglePlayPause() {
        activePlayerManager().togglePlayPause()
    }

    fun playNext() {
        activePlayerManager().playNext()
    }

    fun playPrevious() {
        activePlayerManager().playPrevious()
    }

    fun seekTo(positionMs: Long) {
        activePlayerManager().seekTo(positionMs)
    }

    fun toggleShuffle() {
        activePlayerManager().toggleShuffle()
    }

    fun toggleRepeat() {
        activePlayerManager().toggleRepeatMode()
    }

    fun setVolume(volume: Float) {
        activePlayerManager().setVolume(volume)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song.id)
            val msg = if (song.isFavorite) "Removed from Favorites" else "Added to Favorites"
            _userMessage.emit(msg)
        }
    }

    // Playlists
    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name)
            _showCreatePlaylistDialog.value = false
            _userMessage.emit("Created playlist \"$name\"")
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist.id)
            if (_selectedPlaylist.value?.id == playlist.id) {
                _selectedPlaylist.value = null
            }
            _userMessage.emit("Deleted playlist \"${playlist.name}\"")
        }
    }

    fun addSongToPlaylist(playlist: Playlist, song: Song) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlist.id, song.id)
            _songToAddToPlaylist.value = null
            _userMessage.emit("Added to \"${playlist.name}\"")
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, song.id)
            _userMessage.emit("Removed from playlist")
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    override fun onCleared() {
        if (isServiceBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isServiceBound = false
        }
        fallbackPlayerManager.release()
        super.onCleared()
    }
}
