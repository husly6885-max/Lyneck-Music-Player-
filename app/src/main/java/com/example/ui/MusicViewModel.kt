package com.example.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LyneckContainer
import com.example.data.firebase.AuthManager
import com.example.data.firebase.CloudFavorite
import com.example.data.firebase.CloudPlaylist
import com.example.data.firebase.CloudSyncRepository
import com.example.data.firebase.UserProfile
import com.example.data.lyrics.ParsedLyrics
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.AudioBookmark
import com.example.data.model.AudioFileDetails
import com.example.data.model.DuplicateGroup
import com.example.data.model.Folder
import com.example.data.model.Genre
import com.example.data.model.Playlist
import com.example.data.model.SearchScope
import com.example.data.model.SmartPlaylistType
import com.example.data.model.Song
import com.example.data.model.SortOrder
import com.example.data.preferences.AccentColorOption
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.PlayerScreenStyle
import com.example.data.storage.FileOpResult
import com.example.data.storage.PendingStorageAction
import com.example.playback.DeviceVolumeState
import com.example.playback.EqualizerState
import com.example.playback.MusicPlayerManager
import com.example.playback.PlaybackState
import com.example.playback.SleepTimerState
import com.example.service.MusicService
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab {
    SONGS,
    ALBUMS,
    ARTISTS,
    GENRES,
    FOLDERS,
    RECENTLY_ADDED,
    RECENTLY_PLAYED,
    MOST_PLAYED,
    FAVORITES
}

enum class NavigationScreen {
    LIBRARY,
    SEARCH,
    PLAYLISTS,
    SETTINGS
}

data class ScrollPositionState(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val container = LyneckContainer.get(application)
    val settingsManager = container.settingsManager
    val repository = container.repository
    val fileManager = container.fileManager
    val lyricsManager = container.lyricsManager
    val m3uManager = container.m3uManager

    val settings = settingsManager.settings

    val authManager = AuthManager(application)
    val cloudSyncRepository = CloudSyncRepository(application)

    private val _currentUser = MutableStateFlow<FirebaseUser?>(authManager.currentUser)
    val currentUser = _currentUser.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing = _isCloudSyncing.asStateFlow()

    private var boundService: MusicService? = null
    private var isServiceBound = false
    private var serviceObserverJobs = mutableListOf<Job>()
    private var pendingPlayerAction: ((MusicPlayerManager) -> Unit)? = null
    private var hasRestoredInitialQueue = false

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState = _playbackState.asStateFlow()

    private val _volumeState = MutableStateFlow(DeviceVolumeState())
    val volumeState = _volumeState.asStateFlow()

    private val _eqState = MutableStateFlow(EqualizerState())
    val eqState = _eqState.asStateFlow()

    private val _visualizerBars = MutableStateFlow(List(24) { 0.05f })
    val visualizerBars = _visualizerBars.asStateFlow()

    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState = _sleepTimerState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val userMessage = _userMessage.asSharedFlow()

    private val _intentSenderRequest = MutableSharedFlow<IntentSender>(extraBufferCapacity = 2)
    val intentSenderRequest = _intentSenderRequest.asSharedFlow()
    private var pendingStorageAction: PendingStorageAction? = null

    // Navigation & Screens
    private val _currentScreen = MutableStateFlow(NavigationScreen.LIBRARY)
    val currentScreen = _currentScreen.asStateFlow()

    private val _selectedLibraryTab = MutableStateFlow(LibraryTab.SONGS)
    val selectedLibraryTab = _selectedLibraryTab.asStateFlow()

    private val _showEqualizerScreen = MutableStateFlow(false)
    val showEqualizerScreen = _showEqualizerScreen.asStateFlow()

    private val _showStatsScreen = MutableStateFlow(false)
    val showStatsScreen = _showStatsScreen.asStateFlow()

    private val _isCarMode = MutableStateFlow(false)
    val isCarMode = _isCarMode.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchScope = MutableStateFlow(SearchScope.ALL)
    val searchScope = _searchScope.asStateFlow()

    // Drill-downs
    private val _selectedAlbum = MutableStateFlow<Album?>(null)
    val selectedAlbum = _selectedAlbum.asStateFlow()

    private val _selectedArtist = MutableStateFlow<Artist?>(null)
    val selectedArtist = _selectedArtist.asStateFlow()

    private val _selectedGenre = MutableStateFlow<Genre?>(null)
    val selectedGenre = _selectedGenre.asStateFlow()

    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder = _selectedFolder.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist = _selectedPlaylist.asStateFlow()

    private val _selectedSmartPlaylist = MutableStateFlow<SmartPlaylistType?>(null)
    val selectedSmartPlaylist = _selectedSmartPlaylist.asStateFlow()

    // Multi-select
    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongIds = _selectedSongIds.asStateFlow()

    // Dialogs & Sheets
    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    val showCreatePlaylistDialog = _showCreatePlaylistDialog.asStateFlow()

    private val _songsToAddToPlaylist = MutableStateFlow<List<Song>>(emptyList())
    val songsToAddToPlaylist = _songsToAddToPlaylist.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded = _isNowPlayingExpanded.asStateFlow()

    private val _songForTagEditor = MutableStateFlow<Song?>(null)
    val songForTagEditor = _songForTagEditor.asStateFlow()

    private val _songForFileDetails = MutableStateFlow<AudioFileDetails?>(null)
    val songForFileDetails = _songForFileDetails.asStateFlow()

    private val _songForArtworkEdit = MutableStateFlow<Song?>(null)
    val songForArtworkEdit = _songForArtworkEdit.asStateFlow()

    private val _duplicatesList = MutableStateFlow<List<DuplicateGroup>?>(null)
    val duplicatesList = _duplicatesList.asStateFlow()

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission = _hasStoragePermission.asStateFlow()

    // Library Streams
    val allSongs = repository.allSongs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val rawScannedSongs = repository.rawScannedSongs
    val isScanning = repository.isScanning
    val favoriteSongs = repository.favoriteSongs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentlyPlayedSongs = repository.recentlyPlayedSongs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentlyAddedSongs = repository.recentlyAddedSongs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mostPlayedSongs = repository.mostPlayedSongs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val excludedFolders = repository.excludedFolders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val customEqPresets = repository.customEqualizerPresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val listeningStats = repository.listeningStats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.model.ListeningStatsSummary())
    val listeningHistory = repository.listeningHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val savedQueues = repository.savedQueues.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResults = combine(allSongs, _searchQuery, _searchScope) { songs, query, scope ->
        if (query.isBlank()) {
            emptyList()
        } else {
            val q = query.trim().lowercase()
            songs.filter { s ->
                when (scope) {
                    SearchScope.ALL -> s.title.lowercase().contains(q) ||
                        s.artist.lowercase().contains(q) ||
                        s.album.lowercase().contains(q) ||
                        s.genre.lowercase().contains(q) ||
                        s.folderPath.lowercase().contains(q)
                    SearchScope.TITLE -> s.title.lowercase().contains(q)
                    SearchScope.ARTIST -> s.artist.lowercase().contains(q)
                    SearchScope.ALBUM -> s.album.lowercase().contains(q)
                    SearchScope.GENRE -> s.genre.lowercase().contains(q)
                    SearchScope.FOLDER -> s.folderPath.lowercase().contains(q)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicService.MusicBinder) {
                val svc = service.getService()
                boundService = svc
                isServiceBound = true
                attachToPlayerManager(svc.playerManager)
                pendingPlayerAction?.let { action ->
                    action(svc.playerManager)
                    pendingPlayerAction = null
                }
                restoreQueueIfReady()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            isServiceBound = false
            clearServiceObserverJobs()
        }
    }

    init {
        bindMusicService()
        observeAuthState()
        viewModelScope.launch {
            allSongs.collect { songs ->
                if (songs.isNotEmpty() && !hasRestoredInitialQueue) {
                    restoreQueueIfReady()
                }
            }
        }
    }

    private fun bindMusicService() {
        if (isServiceBound) return
        val existing = MusicService.instance
        if (existing != null) {
            boundService = existing
            attachToPlayerManager(existing.playerManager)
            restoreQueueIfReady()
        }
        val intent = Intent(getApplication(), MusicService::class.java)
        try {
            if (existing == null) {
                getApplication<Application>().startService(intent)
            }
            getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun clearServiceObserverJobs() {
        serviceObserverJobs.forEach { it.cancel() }
        serviceObserverJobs.clear()
    }

    private fun attachToPlayerManager(manager: MusicPlayerManager) {
        clearServiceObserverJobs()
        serviceObserverJobs += viewModelScope.launch {
            manager.playbackState.collect { _playbackState.value = it }
        }
        serviceObserverJobs += viewModelScope.launch {
            manager.volumeObserver.volumeState.collect { _volumeState.value = it }
        }
        serviceObserverJobs += viewModelScope.launch {
            manager.effectsManager.eqState.collect { _eqState.value = it }
        }
        serviceObserverJobs += viewModelScope.launch {
            manager.audioProcessor.visualizerBars.collect { _visualizerBars.value = it }
        }
        serviceObserverJobs += viewModelScope.launch {
            manager.sleepTimerState.collect { _sleepTimerState.value = it }
        }
        serviceObserverJobs += viewModelScope.launch {
            manager.errorEvents.collect { errorMsg -> _userMessage.emit(errorMsg) }
        }
    }

    private fun withPlayer(action: (MusicPlayerManager) -> Unit) {
        val manager = boundService?.playerManager ?: MusicService.instance?.playerManager
        if (manager != null) {
            action(manager)
        } else {
            pendingPlayerAction = action
        }
    }

    fun onPermissionGranted(autoScan: Boolean = true) {
        _hasStoragePermission.value = true
        if (autoScan && allSongs.value.isEmpty()) {
            rescanMusic(silent = true)
        }
    }

    fun onPermissionDenied() {
        _hasStoragePermission.value = false
    }

    fun rescanMusic(silent: Boolean = false) {
        viewModelScope.launch {
            val songs = repository.scanSongs()
            restoreQueueIfReady()
            if (!silent) {
                _userMessage.emit(
                    if (songs.isNotEmpty()) "Found ${songs.size} music tracks" else "No music files found"
                )
            }
        }
    }

    private fun restoreQueueIfReady() {
        if (hasRestoredInitialQueue) return
        val manager = boundService?.playerManager ?: MusicService.instance?.playerManager ?: return
        val currentSongs = allSongs.value
        if (currentSongs.isEmpty()) return
        if (manager.playbackState.value.queue.isNotEmpty()) {
            hasRestoredInitialQueue = true
            return
        }
        hasRestoredInitialQueue = true

        viewModelScope.launch {
            val activeQ = repository.ensureDefaultQueueExists()
            val savedIds = repository.getQueueSongIds(activeQ.id)
            val songMap = currentSongs.associateBy { it.id }
            val restoredList = savedIds.mapNotNull { songMap[it] }

            if (restoredList.isNotEmpty()) {
                val idx = activeQ.currentIndex.coerceIn(0, restoredList.lastIndex)
                val pos = if (settings.value.rememberPlaybackPosition) activeQ.positionMs else 0L
                manager.restoreQueueWithoutAutoPlay(activeQ.id, activeQ.name, restoredList, idx, pos)
            } else {
                val lastId = settings.value.lastSongId
                val lastSong = songMap[lastId]
                if (lastSong != null) {
                    val pos = if (settings.value.rememberPlaybackPosition) settings.value.lastPositionMs else 0L
                    manager.restoreQueueWithoutAutoPlay(activeQ.id, activeQ.name, listOf(lastSong), 0, pos)
                } else {
                    manager.setActiveQueueInfo(activeQ.id, activeQ.name)
                }
            }
        }
    }

    // Scroll position persistence across tabs, detail views, and Now Playing transitions
    private val scrollPositionStore = mutableMapOf<String, ScrollPositionState>()

    fun getScrollPosition(key: String): ScrollPositionState =
        scrollPositionStore[key] ?: ScrollPositionState()

    fun saveScrollPosition(key: String, index: Int, offset: Int) {
        if (index >= 0 && offset >= 0) {
            scrollPositionStore[key] = ScrollPositionState(index, offset)
        }
    }

    // Navigation & UI States
    fun setNavigationScreen(screen: NavigationScreen) {
        _currentScreen.value = screen
        clearSongSelection()
    }

    fun setLibraryTab(tab: LibraryTab) {
        _selectedLibraryTab.value = tab
        clearSongSelection()
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSearchScope(scope: SearchScope) { _searchScope.value = scope }
    fun selectAlbum(album: Album?) { _selectedAlbum.value = album }
    fun selectArtist(artist: Artist?) { _selectedArtist.value = artist }
    fun selectGenre(genre: Genre?) { _selectedGenre.value = genre }
    fun selectFolder(folder: Folder?) { _selectedFolder.value = folder }
    fun selectPlaylist(playlist: Playlist?) { _selectedPlaylist.value = playlist }
    fun selectSmartPlaylist(smart: SmartPlaylistType?) { _selectedSmartPlaylist.value = smart }
    fun setShowEqualizerScreen(show: Boolean) { _showEqualizerScreen.value = show }
    fun setShowStatsScreen(show: Boolean) { _showStatsScreen.value = show }
    fun setCarMode(enabled: Boolean) { _isCarMode.value = enabled }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _isNowPlayingExpanded.value = expanded
        updateVisualizerActiveState()
    }

    fun updateVisualizerActiveState() {
        val style = settings.value.playerStyle
        val active = _isNowPlayingExpanded.value && style == PlayerScreenStyle.VISUALIZER
        withPlayer { it.setVisualizerActive(active) }
    }

    fun showCreatePlaylistDialog(show: Boolean) { _showCreatePlaylistDialog.value = show }
    fun setSongToAddToPlaylist(song: Song?) { _songsToAddToPlaylist.value = if (song != null) listOf(song) else emptyList() }
    fun setSongsToAddToPlaylist(songs: List<Song>) { _songsToAddToPlaylist.value = songs }
    fun setSongForTagEditor(song: Song?) { _songForTagEditor.value = song }
    fun setSongForArtworkEdit(song: Song?) { _songForArtworkEdit.value = song }
    fun clearFileDetails() { _songForFileDetails.value = null }
    fun closeDuplicatesDialog() { _duplicatesList.value = null }

    // Multi-Select
    fun toggleSongSelection(songId: Long) {
        val current = _selectedSongIds.value.toMutableSet()
        if (!current.add(songId)) current.remove(songId)
        _selectedSongIds.value = current
    }

    fun selectAllSongs(songs: List<Song>) {
        _selectedSongIds.value = songs.map { it.id }.toSet()
    }

    fun clearSongSelection() {
        _selectedSongIds.value = emptySet()
    }

    // Playback Actions
    fun playSong(song: Song, contextList: List<Song> = emptyList()) {
        val list = if (contextList.isNotEmpty()) contextList else listOf(song)
        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        withPlayer { it.playSongList(list, index) }
    }

    fun shuffleAndPlay(songs: List<Song>) {
        if (songs.isEmpty()) return
        withPlayer { manager ->
            if (!manager.playbackState.value.isShuffle) {
                manager.toggleShuffle()
            }
            manager.playSongList(songs, (0 until songs.size).random())
        }
    }

    fun playNextInQueue(song: Song) {
        withPlayer { it.playNextInQueue(song) }
        viewModelScope.launch { _userMessage.emit("Playing \"${song.title}\" next") }
    }

    fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        withPlayer { it.addToQueue(songs) }
        viewModelScope.launch { _userMessage.emit("Added ${songs.size} track(s) to queue") }
    }

    fun removeFromQueue(index: Int) = withPlayer { it.removeFromQueue(index) }
    fun moveQueueItem(from: Int, to: Int) = withPlayer { it.moveQueueItem(from, to) }
    fun clearQueue() = withPlayer { it.clearQueue() }
    fun playQueueIndex(index: Int) = withPlayer { it.playAtIndex(index) }
    fun togglePlayPause() = withPlayer { it.togglePlayPause() }
    fun playNext() = withPlayer { it.playNext() }
    fun playPrevious() = withPlayer { it.playPrevious() }
    fun seekTo(positionMs: Long) = withPlayer { it.seekTo(positionMs) }
    fun toggleShuffle() = withPlayer { it.toggleShuffle() }
    fun toggleRepeat() = withPlayer { it.toggleRepeatMode() }
    fun toggleStopAfterCurrent() = withPlayer { it.toggleStopAfterCurrentTrack() }

    fun startSleepTimer(minutes: Int, fadeOut: Boolean) {
        withPlayer { it.startSleepTimer(minutes, fadeOut) }
        viewModelScope.launch { _userMessage.emit("Sleep timer set for $minutes min") }
    }

    fun cancelSleepTimer() {
        withPlayer { it.cancelSleepTimer() }
        viewModelScope.launch { _userMessage.emit("Sleep timer cancelled") }
    }

    // Multiple Queues
    fun createNewQueue(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val q = repository.createSavedQueue(name)
            switchActiveQueue(q.id)
            _userMessage.emit("Created queue \"${q.name}\"")
        }
    }

    fun switchActiveQueue(queueId: Long) {
        viewModelScope.launch {
            withPlayer { it.persistCurrentPlaybackPosition() }
            val (queue, songIds) = repository.switchActiveQueue(queueId)
            val songMap = allSongs.value.associateBy { it.id }
            val songs = songIds.mapNotNull { songMap[it] }
            if (queue != null) {
                withPlayer { manager ->
                    if (songs.isNotEmpty()) {
                        manager.restoreQueueWithoutAutoPlay(queue.id, queue.name, songs, queue.currentIndex, queue.positionMs)
                    } else {
                        manager.clearQueue()
                        manager.setActiveQueueInfo(queue.id, queue.name)
                    }
                }
            }
        }
    }

    fun deleteSavedQueue(queueId: Long) {
        viewModelScope.launch {
            val all = savedQueues.value
            if (all.size <= 1) {
                _userMessage.emit("Cannot delete the only playback queue")
                return@launch
            }
            val wasActive = playbackState.value.activeQueueId == queueId
            repository.deleteSavedQueue(queueId)
            if (wasActive) {
                val fallback = repository.ensureDefaultQueueExists()
                switchActiveQueue(fallback.id)
            }
        }
    }

    // Real Volume & Audio Settings
    fun refreshDeviceVolume() = withPlayer { it.volumeObserver.refresh() }
    fun setDeviceVolumeRatio(ratio: Float) = withPlayer { it.volumeObserver.setVolumeRatio(ratio) }

    fun setPlaybackSpeed(speed: Float) {
        settingsManager.update { it.copy(playbackSpeed = speed.coerceIn(0.5f, 2.0f)) }
        withPlayer { it.applyPlaybackSettings(settingsManager.settings.value) }
    }

    fun setGaplessPlayback(enabled: Boolean) {
        settingsManager.update { it.copy(gaplessPlayback = enabled) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        settingsManager.update { it.copy(crossfadeSeconds = seconds.coerceIn(0, 12)) }
    }

    fun setFadeInOut(enabled: Boolean) {
        settingsManager.update { it.copy(fadeInOut = enabled) }
    }

    fun setSkipSilence(enabled: Boolean) {
        settingsManager.update { it.copy(skipSilence = enabled) }
        withPlayer { it.applyPlaybackSettings(settingsManager.settings.value) }
    }

    fun setRememberPlaybackPosition(enabled: Boolean) {
        settingsManager.update { it.copy(rememberPlaybackPosition = enabled) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        settingsManager.update { it.copy(equalizerEnabled = enabled) }
        withPlayer { it.applyAudioSettings(settingsManager.settings.value) }
    }

    fun setEqBandLevel(bandIndex: Int, levelMb: Int) {
        val current = settings.value
        val isHeadphones = _eqState.value.isHeadphonesConnected
        val activeCsv = if (current.separateHeadphoneProfile) {
            if (isHeadphones) current.headphoneBandLevelsCsv else current.speakerBandLevelsCsv
        } else current.eqBandLevelsCsv

        val list = activeCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableList()
        while (list.size <= bandIndex) list.add(0)
        list[bandIndex] = levelMb
        val newCsv = list.joinToString(",")

        settingsManager.update { s ->
            if (s.separateHeadphoneProfile) {
                if (isHeadphones) s.copy(headphoneBandLevelsCsv = newCsv, activeEqPresetName = "Custom")
                else s.copy(speakerBandLevelsCsv = newCsv, activeEqPresetName = "Custom")
            } else {
                s.copy(eqBandLevelsCsv = newCsv, activeEqPresetName = "Custom")
            }
        }
        withPlayer { it.applyAudioSettings(settingsManager.settings.value) }
    }

    fun applyBuiltInEqPreset(presetName: String) {
        val bandCount = _eqState.value.bands.size.coerceAtLeast(5)
        withPlayer { manager ->
            val levels = manager.effectsManager.getPresetBandLevels(presetName, bandCount)
            val csv = levels.joinToString(",")
            val isHeadphones = _eqState.value.isHeadphonesConnected
            settingsManager.update { s ->
                if (s.separateHeadphoneProfile) {
                    if (isHeadphones) s.copy(headphoneBandLevelsCsv = csv, activeEqPresetName = presetName, equalizerEnabled = true)
                    else s.copy(speakerBandLevelsCsv = csv, activeEqPresetName = presetName, equalizerEnabled = true)
                } else {
                    s.copy(eqBandLevelsCsv = csv, activeEqPresetName = presetName, equalizerEnabled = true)
                }
            }
            manager.applyAudioSettings(settingsManager.settings.value)
        }
    }

    fun applyCustomEqPreset(preset: com.example.data.local.entity.EqualizerPresetEntity) {
        settingsManager.update {
            it.copy(
                equalizerEnabled = true,
                activeEqPresetName = preset.name,
                eqBandLevelsCsv = preset.bandLevelsCsv,
                bassBoostEnabled = preset.bassBoost > 0,
                bassBoostStrength = preset.bassBoost,
                virtualizerEnabled = preset.virtualizer > 0,
                virtualizerStrength = preset.virtualizer,
                trebleBoost = preset.trebleBoost
            )
        }
        withPlayer { it.applyAudioSettings(settingsManager.settings.value) }
    }

    fun saveCurrentAsCustomEqPreset(name: String) {
        if (name.isBlank()) return
        val s = settings.value
        viewModelScope.launch {
            repository.saveEqualizerPreset(
                name = name,
                bandLevelsCsv = s.eqBandLevelsCsv,
                bassBoost = if (s.bassBoostEnabled) s.bassBoostStrength else 0,
                virtualizer = if (s.virtualizerEnabled) s.virtualizerStrength else 0,
                trebleBoost = s.trebleBoost
            )
            settingsManager.update { it.copy(activeEqPresetName = name.trim()) }
            _userMessage.emit("Saved EQ preset \"$name\"")
        }
    }

    fun deleteCustomEqPreset(id: Long) {
        viewModelScope.launch { repository.deleteEqualizerPreset(id) }
    }

    fun setBassBoost(enabled: Boolean, strength: Int) {
        settingsManager.update { it.copy(bassBoostEnabled = enabled, bassBoostStrength = strength.coerceIn(0, 1000)) }
        withPlayer { it.applyAudioSettings(settingsManager.settings.value) }
    }

    fun setVirtualizer(enabled: Boolean, strength: Int) {
        settingsManager.update { it.copy(virtualizerEnabled = enabled, virtualizerStrength = strength.coerceIn(0, 1000)) }
        withPlayer { it.applyAudioSettings(settingsManager.settings.value) }
    }

    fun setTrebleBoost(strength: Int) {
        settingsManager.update { it.copy(trebleBoost = strength.coerceIn(0, 1000)) }
        withPlayer { it.applyAudioSettings(settingsManager.settings.value) }
    }

    fun setAudioBalance(balance: Float) {
        settingsManager.update { it.copy(audioBalance = balance.coerceIn(-1f, 1f)) }
        withPlayer { it.applyAudioSettings(settingsManager.settings.value) }
    }

    fun setVolumeNormalization(enabled: Boolean) {
        settingsManager.update { it.copy(volumeNormalization = enabled) }
        withPlayer { it.applyAudioSettings(settingsManager.settings.value) }
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        settingsManager.update { it.copy(replayGainEnabled = enabled) }
        withPlayer { it.applyAudioSettings(settingsManager.settings.value) }
    }

    fun setSeparateHeadphoneProfile(enabled: Boolean) {
        settingsManager.update { it.copy(separateHeadphoneProfile = enabled) }
        withPlayer { it.applyAudioSettings(settingsManager.settings.value) }
    }

    // Lyrics
    fun observeLyrics(songId: Long) = repository.observeLyrics(songId)

    suspend fun loadLyricsIfMissing(song: Song): ParsedLyrics {
        val dbLyrics = repository.getLyrics(song.id)
        if (dbLyrics != null) {
            return lyricsManager.parse(dbLyrics.lyricsText)
        }
        val discovered = lyricsManager.findLyricsForSong(song, settings.value.autoLoadExternalLrc)
        if (discovered != null) {
            repository.saveLyrics(song.id, discovered.rawText, discovered.isSynced, settings.value.defaultLyricsOffsetMs)
            return discovered
        }
        return ParsedLyrics(emptyList(), isSynced = false, rawText = "")
    }

    fun saveManualLyrics(songId: Long, rawText: String, offsetMs: Long) {
        viewModelScope.launch {
            val parsed = lyricsManager.parse(rawText)
            repository.saveLyrics(songId, rawText, parsed.isSynced, offsetMs)
            _userMessage.emit("Lyrics saved")
        }
    }

    fun updateLyricsOffset(songId: Long, newOffsetMs: Long) {
        viewModelScope.launch {
            val existing = repository.getLyrics(songId)
            if (existing != null) {
                repository.saveLyrics(songId, existing.lyricsText, existing.isSynced, newOffsetMs, existing.sourcePath)
            }
        }
    }

    fun importLrcFile(songId: Long, uri: Uri) {
        viewModelScope.launch {
            val text = lyricsManager.readLrcFromUri(uri)
            if (text.isNullOrBlank()) {
                _userMessage.emit("Could not read LRC file")
            } else {
                val parsed = lyricsManager.parse(text)
                repository.saveLyrics(songId, text, parsed.isSynced, settings.value.defaultLyricsOffsetMs, uri.toString())
                _userMessage.emit("Imported LRC lyrics")
            }
        }
    }

    // Bookmarks
    fun observeBookmarksForSong(songId: Long): Flow<List<AudioBookmark>> =
        repository.getBookmarksForSong(songId)

    fun addBookmarkAtCurrentPosition(song: Song, label: String) {
        val pos = _playbackState.value.currentPositionMs
        viewModelScope.launch {
            repository.addBookmark(song.id, pos, label.ifBlank { "Bookmark" })
            _userMessage.emit("Bookmark saved at ${formatTime(pos)}")
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { repository.deleteBookmark(id) }
    }

    // Favorites & Playlists
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song.id)
            _userMessage.emit(if (song.isFavorite) "Removed from Favorites" else "Added to Favorites")
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name)
            _showCreatePlaylistDialog.value = false
            _userMessage.emit("Created playlist \"$name\"")
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, newName)
            _selectedPlaylist.value = _selectedPlaylist.value?.let {
                if (it.id == playlistId) it.copy(name = newName.trim()) else it
            }
            _userMessage.emit("Renamed playlist to \"$newName\"")
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist.id)
            if (_selectedPlaylist.value?.id == playlist.id) _selectedPlaylist.value = null
            _userMessage.emit("Deleted playlist \"${playlist.name}\"")
        }
    }

    fun addSongsToPlaylist(playlist: Playlist, songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            repository.addSongsToPlaylist(playlist.id, songs.map { it.id })
            _songsToAddToPlaylist.value = emptyList()
            clearSongSelection()
            _userMessage.emit("Added ${songs.size} song(s) to \"${playlist.name}\"")
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, song.id)
            _userMessage.emit("Removed from playlist")
        }
    }

    fun reorderPlaylistSongs(playlistId: Long, orderedSongIds: List<Long>) {
        viewModelScope.launch {
            repository.reorderPlaylistSongs(playlistId, orderedSongIds)
        }
    }

    fun exportPlaylistM3u(playlist: Playlist, songs: List<Song>, destUri: Uri) {
        viewModelScope.launch {
            m3uManager.exportPlaylistToM3u(playlist.name, songs, destUri)
                .onSuccess { _userMessage.emit(it) }
                .onFailure { _userMessage.emit("Export failed: ${it.localizedMessage}") }
        }
    }

    fun importPlaylistM3u(uri: Uri, fileName: String) {
        viewModelScope.launch {
            m3uManager.importPlaylistFromM3u(uri, fileName, allSongs.value)
                .onSuccess { (name, count) -> _userMessage.emit("Imported \"$name\" ($count tracks)") }
                .onFailure { _userMessage.emit("Import failed: ${it.localizedMessage}") }
        }
    }

    fun exportBackupJson(uri: Uri) {
        viewModelScope.launch {
            m3uManager.exportFullBackupJson(uri, allSongs.value)
                .onSuccess { _userMessage.emit(it) }
                .onFailure { _userMessage.emit("Backup failed: ${it.localizedMessage}") }
        }
    }

    fun importBackupJson(uri: Uri) {
        viewModelScope.launch {
            m3uManager.importFullBackupJson(uri, allSongs.value)
                .onSuccess {
                    withPlayer { p ->
                        p.applyPlaybackSettings(settingsManager.settings.value)
                        p.applyAudioSettings(settingsManager.settings.value)
                    }
                    _userMessage.emit(it)
                }
                .onFailure { _userMessage.emit("Restore failed: ${it.localizedMessage}") }
        }
    }

    // Real File Operations & Permanent Deletion
    fun requestDeleteSongsPermanently(songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            when (val res = fileManager.deleteSongsPermanently(songs)) {
                is FileOpResult.Success -> finalizeSongDeletion(songs, res.message)
                is FileOpResult.RequiresIntentSender -> {
                    pendingStorageAction = res.pendingAction
                    _intentSenderRequest.emit(res.intentSender)
                }
                is FileOpResult.Error -> _userMessage.emit(res.message)
            }
        }
    }

    fun requestUpdateSongTags(
        song: Song,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int,
        trackNumber: Int,
        newFileName: String?
    ) {
        viewModelScope.launch {
            repository.saveCustomSongMetadata(song.id, title, artist, album, genre, year, trackNumber)
            when (val res = fileManager.updateSongMetadataInStore(song, title, artist, album, genre, year, trackNumber, newFileName)) {
                is FileOpResult.Success -> {
                    _songForTagEditor.value = null
                    rescanMusic(silent = true)
                    _userMessage.emit(res.message)
                }
                is FileOpResult.RequiresIntentSender -> {
                    pendingStorageAction = res.pendingAction
                    _intentSenderRequest.emit(res.intentSender)
                }
                is FileOpResult.Error -> _userMessage.emit(res.message)
            }
        }
    }

    fun onStorageIntentSenderResult(approved: Boolean) {
        val action = pendingStorageAction ?: return
        pendingStorageAction = null
        viewModelScope.launch {
            when (action) {
                is PendingStorageAction.DeleteSongs -> {
                    if (!approved) {
                        _userMessage.emit("Delete cancelled — original file(s) kept.")
                        return@launch
                    }
                    // Verify on Android 10 / 11+ that files are truly deleted
                    val deleted = fileManager.verifySongsDeleted(action.songs)
                    if (deleted) {
                        finalizeSongDeletion(action.songs, "Permanently deleted ${action.songs.size} track(s) from storage.")
                    } else {
                        // Retry direct delete if Android 10 RecoverableSecurityException just granted access
                        when (val retry = fileManager.deleteSongsPermanently(action.songs)) {
                            is FileOpResult.Success -> finalizeSongDeletion(action.songs, retry.message)
                            else -> _userMessage.emit("Could not delete file(s) from storage.")
                        }
                    }
                }
                is PendingStorageAction.UpdateSongTags -> {
                    if (approved) {
                        fileManager.updateSongMetadataInStore(
                            action.song, action.title, action.artist, action.album,
                            action.genre, action.year, action.trackNumber, action.newFileName
                        )
                    }
                    _songForTagEditor.value = null
                    rescanMusic(silent = true)
                    _userMessage.emit("Updated track info")
                }
            }
        }
    }

    private suspend fun finalizeSongDeletion(songs: List<Song>, successMessage: String) {
        val ids = songs.map { it.id }.toSet()
        withPlayer { it.removeDeletedSongsFromQueue(ids) }
        repository.purgeDeletedSongs(ids.toList())
        clearSongSelection()
        rescanMusic(silent = true)
        _userMessage.emit(successMessage)
    }

    fun updateCustomArtwork(song: Song, imageUri: Uri?) {
        viewModelScope.launch {
            if (imageUri == null) {
                repository.saveCustomArtwork(song.id, null)
                _userMessage.emit("Restored original album artwork")
            } else {
                val localUri = fileManager.saveCustomArtworkLocally(song.id, imageUri)
                if (localUri != null) {
                    repository.saveCustomArtwork(song.id, localUri)
                    _userMessage.emit("Updated album artwork")
                } else {
                    _userMessage.emit("Failed to save custom artwork")
                }
            }
            _songForArtworkEdit.value = null
        }
    }

    fun copyOrMoveSongToFolder(song: Song, treeUri: Uri, move: Boolean) {
        viewModelScope.launch {
            when (val res = fileManager.copyOrMoveSongToTree(song, treeUri, move)) {
                is FileOpResult.Success -> {
                    if (move) {
                        finalizeSongDeletion(listOf(song), "Moved \"${song.title}\"")
                    } else {
                        rescanMusic(silent = true)
                        _userMessage.emit(res.message)
                    }
                }
                is FileOpResult.RequiresIntentSender -> {
                    pendingStorageAction = res.pendingAction
                    _intentSenderRequest.emit(res.intentSender)
                }
                is FileOpResult.Error -> _userMessage.emit(res.message)
            }
        }
    }

    fun loadFileDetails(song: Song) {
        viewModelScope.launch {
            _songForFileDetails.value = repository.getAudioFileDetails(song)
        }
    }

    fun findDuplicateSongs() {
        _duplicatesList.value = repository.findDuplicates(allSongs.value)
    }

    fun setSortOrder(order: SortOrder) = settingsManager.update { it.copy(sortOrder = order) }
    fun excludeFolder(path: String) { viewModelScope.launch { repository.excludeFolder(path) } }
    fun includeFolder(path: String) { viewModelScope.launch { repository.includeFolder(path) } }

    // Settings updates
    fun setThemeMode(mode: AppThemeMode) = settingsManager.update { it.copy(themeMode = mode) }
    fun setAccentColor(color: AccentColorOption) = settingsManager.update { it.copy(accentColor = color) }
    fun setPlayerStyle(style: PlayerScreenStyle) {
        settingsManager.update { it.copy(playerStyle = style) }
        updateVisualizerActiveState()
    }
    fun setAutoLoadExternalLrc(enabled: Boolean) = settingsManager.update { it.copy(autoLoadExternalLrc = enabled) }
    fun setKaraokeHighlight(enabled: Boolean) = settingsManager.update { it.copy(karaokeHighlight = enabled) }
    fun setDefaultLyricsOffset(offsetMs: Long) = settingsManager.update { it.copy(defaultLyricsOffsetMs = offsetMs) }
    fun setAutoScanOnStartup(enabled: Boolean) = settingsManager.update { it.copy(autoScanOnStartup = enabled) }
    fun setShowArtworkInNotification(enabled: Boolean) = settingsManager.update { it.copy(showArtworkInNotification = enabled) }
    fun setKeepPlayingOnTaskRemove(enabled: Boolean) = settingsManager.update { it.copy(keepPlayingOnTaskRemove = enabled) }

    // Firebase Auth & Cloud Sync (Preserved)
    private fun observeAuthState() {
        viewModelScope.launch {
            authManager.authStateFlow().collect { user ->
                _currentUser.value = user
                if (user != null) {
                    cloudSyncRepository.syncUserProfile(
                        user.uid,
                        UserProfile(
                            displayName = user.displayName ?: "User",
                            email = user.email ?: "",
                            photoUrl = user.photoUrl?.toString() ?: ""
                        )
                    )
                }
            }
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            authManager.signInWithGoogle()
                .onSuccess { user -> _userMessage.emit("Signed in as ${user.displayName ?: user.email}") }
                .onFailure { e ->
                    if (e !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                        _userMessage.emit("Sign in failed: ${e.localizedMessage}")
                    }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            _userMessage.emit("Signed out")
        }
    }

    fun backupToCloud() {
        val user = _currentUser.value
        if (user == null) {
            viewModelScope.launch { _userMessage.emit("Please sign in to back up to the cloud.") }
            return
        }
        viewModelScope.launch {
            _isCloudSyncing.value = true
            for (pl in playlists.value) {
                val count = repository.getPlaylistSongCount(pl.id).firstOrNull() ?: 0
                cloudSyncRepository.saveCloudPlaylist(
                    user.uid,
                    CloudPlaylist(id = pl.id.toString(), name = pl.name, songCount = count.toLong())
                )
            }
            for (fav in favoriteSongs.value) {
                cloudSyncRepository.saveCloudFavorite(
                    user.uid,
                    CloudFavorite(
                        id = fav.id.toString(),
                        songTitle = fav.title,
                        artist = fav.artist,
                        album = fav.album,
                        duration = fav.duration
                    )
                )
            }
            _isCloudSyncing.value = false
            _userMessage.emit("Cloud backup completed successfully!")
        }
    }

    fun emitMessage(msg: String) {
        viewModelScope.launch { _userMessage.emit(msg) }
    }

    private fun formatTime(ms: Long): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return String.format("%d:%02d", sec / 60, sec % 60)
    }

    override fun onCleared() {
        clearServiceObserverJobs()
        if (isServiceBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: Exception) {
            }
            isServiceBound = false
        }
        super.onCleared()
    }
}
