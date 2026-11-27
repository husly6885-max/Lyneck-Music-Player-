package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Playlist
import com.example.data.model.Song
import com.example.data.storage.FileOpResult
import com.example.ui.LibraryTab
import com.example.ui.MusicViewModel
import com.example.ui.NavigationScreen
import com.example.ui.components.AddToPlaylistSheet
import com.example.ui.components.CarPlayerScreen
import com.example.ui.components.ConfirmPermanentDeleteDialog
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.DuplicateSongsDialog
import com.example.ui.components.EditSongTagsDialog
import com.example.ui.components.FileDetailsDialog
import com.example.ui.components.LyricsSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.components.NowPlayingScreen
import com.example.ui.components.PermissionRequiredCard
import com.example.ui.components.SongActionHandlers
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var musicViewModelRef: MusicViewModel? = null
    private var pendingShortcutAction: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingShortcutAction = intent?.action

        setContent {
            val musicViewModel: MusicViewModel = viewModel()
            musicViewModelRef = musicViewModel
            val settings by musicViewModel.settings.collectAsState()

            LaunchedEffect(pendingShortcutAction) {
                val act = pendingShortcutAction
                if (act != null) {
                    handleShortcutAction(act, musicViewModel)
                    pendingShortcutAction = null
                }
            }

            MyApplicationTheme(
                themeMode = settings.themeMode,
                accentColor = settings.accentColor
            ) {
                MainAppContent(viewModel = musicViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val act = intent.action ?: return
        val vm = musicViewModelRef
        if (vm != null) {
            handleShortcutAction(act, vm)
        } else {
            pendingShortcutAction = act
        }
    }

    private fun handleShortcutAction(action: String, vm: MusicViewModel) {
        when (action) {
            "com.example.ACTION_SHUFFLE_ALL" -> {
                vm.setNavigationScreen(NavigationScreen.LIBRARY)
                val songs = vm.allSongs.value
                if (songs.isNotEmpty()) {
                    vm.shuffleAndPlay(songs)
                    vm.setNowPlayingExpanded(true)
                }
            }
            "com.example.ACTION_OPEN_FAVORITES" -> {
                vm.setNavigationScreen(NavigationScreen.LIBRARY)
                vm.setLibraryTab(LibraryTab.FAVORITES)
            }
            "com.example.ACTION_OPEN_SEARCH" -> {
                vm.setNavigationScreen(NavigationScreen.SEARCH)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val settings by viewModel.settings.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val volumeState by viewModel.volumeState.collectAsState()
    val visualizerBars by viewModel.visualizerBars.collectAsState()
    val sleepTimerState by viewModel.sleepTimerState.collectAsState()
    val savedQueues by viewModel.savedQueues.collectAsState()

    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsState()
    val showEqualizerScreen by viewModel.showEqualizerScreen.collectAsState()
    val showStatsScreen by viewModel.showStatsScreen.collectAsState()
    val isCarMode by viewModel.isCarMode.collectAsState()

    val hasStoragePermission by viewModel.hasStoragePermission.collectAsState()
    val showCreatePlaylistDialog by viewModel.showCreatePlaylistDialog.collectAsState()
    val songsToAddToPlaylist by viewModel.songsToAddToPlaylist.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    val songForTagEditor by viewModel.songForTagEditor.collectAsState()
    val songForFileDetails by viewModel.songForFileDetails.collectAsState()
    val songForArtworkEdit by viewModel.songForArtworkEdit.collectAsState()
    val duplicatesList by viewModel.duplicatesList.collectAsState()

    var lyricsSong by remember { mutableStateOf<Song?>(null) }
    var songsPendingDeleteConfirm by remember { mutableStateOf<List<Song>>(emptyList()) }
    var pendingM3uExport by remember { mutableStateOf<Pair<Playlist, List<Song>>?>(null) }
    var pendingTreeOp by remember { mutableStateOf<Pair<Song, Boolean>?>(null) } // song, move

    val snackbarHostState = remember { SnackbarHostState() }

    // Keep AudioManager volume synced when resuming from background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDeviceVolume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Collect toast/snackbar events & IntentSender requests
    LaunchedEffect(viewModel) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onStorageIntentSenderResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(viewModel) {
        viewModel.intentSenderRequest.collect { sender ->
            intentSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    // Legacy WRITE_EXTERNAL_STORAGE permission for Android 7-9 file deletion/rename
    var pendingLegacyDelete by remember { mutableStateOf<List<Song>?>(null) }
    val legacyWriteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingLegacyDelete
        pendingLegacyDelete = null
        if (granted && pending != null) {
            viewModel.requestDeleteSongsPermanently(pending)
        } else if (!granted) {
            viewModel.emitMessage("Write storage permission is required to delete files.")
        }
    }

    // Zero-permission Photo Picker for Custom Album Artwork
    val pickArtworkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = songForArtworkEdit
        if (target != null && uri != null) {
            viewModel.updateCustomArtwork(target, uri)
        }
    }

    // M3U Export / Import
    val exportM3uLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        val pending = pendingM3uExport
        pendingM3uExport = null
        if (uri != null && pending != null) {
            viewModel.exportPlaylistM3u(pending.first, pending.second, uri)
        }
    }

    val importM3uLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            var displayName = "Imported Playlist.m3u"
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && c.moveToFirst()) {
                    displayName = c.getString(idx) ?: displayName
                }
            }
            viewModel.importPlaylistM3u(uri, displayName)
        }
    }

    // Full JSON Backup / Restore
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackupJson(uri)
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importBackupJson(uri)
    }

    // External .LRC Lyrics Picker
    val importLrcLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val song = lyricsSong
        if (uri != null && song != null) {
            viewModel.importLrcFile(song.id, uri)
        }
    }

    // SAF Folder Tree Picker for Copy / Move
    val folderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val pending = pendingTreeOp
        pendingTreeOp = null
        if (treeUri != null && pending != null) {
            viewModel.copyOrMoveSongToFolder(pending.first, treeUri, move = pending.second)
        }
    }

    // Audio Storage Permissions (READ_MEDIA_AUDIO on API 33+, READ_EXTERNAL_STORAGE on <= 32)
    val requiredAudioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val audioGranted = permissionsMap[requiredAudioPermission] == true ||
            ContextCompat.checkSelfPermission(
                context,
                requiredAudioPermission
            ) == PackageManager.PERMISSION_GRANTED
        if (audioGranted) {
            viewModel.onPermissionGranted(autoScan = settings.autoScanOnStartup)
        } else {
            viewModel.onPermissionDenied()
        }
    }

    LaunchedEffect(Unit) {
        val isAudioGranted = ContextCompat.checkSelfPermission(
            context,
            requiredAudioPermission
        ) == PackageManager.PERMISSION_GRANTED
        val isNotificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (isAudioGranted) {
            viewModel.onPermissionGranted(autoScan = settings.autoScanOnStartup)
        }

        if (!isAudioGranted || !isNotificationGranted) {
            val perms = mutableListOf<String>()
            if (!isAudioGranted) {
                perms.add(requiredAudioPermission)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationGranted) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (perms.isNotEmpty()) {
                permissionsLauncher.launch(perms.toTypedArray())
            }
        }
    }

    val songActions = remember(viewModel) {
        SongActionHandlers(
            onOpenLyrics = { song -> lyricsSong = song },
            onShareSong = { song ->
                runCatching { context.startActivity(viewModel.fileManager.buildShareIntent(song)) }
                    .onFailure { viewModel.emitMessage("Unable to share file") }
            },
            onSetRingtone = { song ->
                when (val res = viewModel.fileManager.setAsRingtone(song)) {
                    is FileOpResult.Success -> viewModel.emitMessage(res.message)
                    is FileOpResult.Error -> {
                        if (res.message == "SYSTEM_WRITE_SETTINGS_REQUIRED") {
                            viewModel.emitMessage("Allow 'Modify system settings' then tap Set as Ringtone again.")
                            runCatching {
                                context.startActivity(viewModel.fileManager.buildManageWriteSettingsIntent())
                            }
                        } else {
                            viewModel.emitMessage(res.message)
                        }
                    }
                    else -> Unit
                }
            },
            onCopyToFolder = { song ->
                pendingTreeOp = song to false
                folderTreeLauncher.launch(null)
            },
            onMoveToFolder = { song ->
                pendingTreeOp = song to true
                folderTreeLauncher.launch(null)
            },
            onRequestDeleteSongs = { songs ->
                songsPendingDeleteConfirm = songs
            }
        )
    }

    // Keep main Scaffold always mounted so lazy list scroll positions are preserved
    // when expanding/collapsing Now Playing or sub-screens.
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column {
                    if (playbackState.currentSong != null) {
                        MiniPlayer(
                            playbackState = playbackState,
                            onPlayPauseClick = { viewModel.togglePlayPause() },
                            onNextClick = { viewModel.playNext() },
                            onExpandClick = { viewModel.setNowPlayingExpanded(true) }
                        )
                    }

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == NavigationScreen.LIBRARY,
                            onClick = { viewModel.setNavigationScreen(NavigationScreen.LIBRARY) },
                            icon = { Icon(Icons.Rounded.LibraryMusic, stringResource(R.string.nav_library)) },
                            label = { Text(stringResource(R.string.nav_library)) },
                            modifier = Modifier.testTag("nav_library_item")
                        )
                        NavigationBarItem(
                            selected = currentScreen == NavigationScreen.SEARCH,
                            onClick = { viewModel.setNavigationScreen(NavigationScreen.SEARCH) },
                            icon = { Icon(Icons.Rounded.Search, stringResource(R.string.nav_search)) },
                            label = { Text(stringResource(R.string.nav_search)) },
                            modifier = Modifier.testTag("nav_search_item")
                        )
                        NavigationBarItem(
                            selected = currentScreen == NavigationScreen.PLAYLISTS,
                            onClick = { viewModel.setNavigationScreen(NavigationScreen.PLAYLISTS) },
                            icon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.nav_playlists)) },
                            label = { Text(stringResource(R.string.nav_playlists)) },
                            modifier = Modifier.testTag("nav_playlists_item")
                        )
                        NavigationBarItem(
                            selected = currentScreen == NavigationScreen.SETTINGS,
                            onClick = { viewModel.setNavigationScreen(NavigationScreen.SETTINGS) },
                            icon = { Icon(Icons.Rounded.Settings, stringResource(R.string.nav_settings)) },
                            label = { Text(stringResource(R.string.nav_settings)) },
                            modifier = Modifier.testTag("nav_settings_item")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!hasStoragePermission) {
                    PermissionRequiredCard(
                        onRequestPermission = {
                            val perms = mutableListOf(requiredAudioPermission)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionsLauncher.launch(perms.toTypedArray())
                        }
                    )
                } else {
                    when (currentScreen) {
                        NavigationScreen.LIBRARY -> LibraryScreen(viewModel = viewModel, songActions = songActions)
                        NavigationScreen.SEARCH -> SearchScreen(viewModel = viewModel, songActions = songActions)
                        NavigationScreen.PLAYLISTS -> PlaylistsScreen(
                            viewModel = viewModel,
                            songActions = songActions,
                            onExportPlaylistM3u = { pl, plSongs ->
                                pendingM3uExport = pl to plSongs
                                exportM3uLauncher.launch("${pl.name}.m3u")
                            },
                            onImportPlaylistM3u = {
                                importM3uLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        NavigationScreen.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            onOpenEqualizer = { viewModel.setShowEqualizerScreen(true) },
                            onOpenStats = { viewModel.setShowStatsScreen(true) },
                            onExportBackupJson = { exportBackupLauncher.launch("lyneck_backup.json") },
                            onImportBackupJson = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                            onImportM3u = { importM3uLauncher.launch(arrayOf("*/*")) }
                        )
                    }
                }
            }
        }

        // Now Playing full-screen overlay
        AnimatedVisibility(
            visible = isNowPlayingExpanded && playbackState.currentSong != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            BackHandler { viewModel.setNowPlayingExpanded(false) }
            NowPlayingScreen(
                playbackState = playbackState,
                volumeState = volumeState,
                visualizerBars = visualizerBars,
                sleepTimerState = sleepTimerState,
                settings = settings,
                savedQueues = savedQueues,
                viewModel = viewModel,
                onCollapse = { viewModel.setNowPlayingExpanded(false) },
                onOpenEqualizer = { viewModel.setShowEqualizerScreen(true) },
                onOpenCarMode = { viewModel.setCarMode(true) },
                onOpenLyrics = { song -> lyricsSong = song }
            )
        }

        // Stats overlay
        AnimatedVisibility(
            visible = showStatsScreen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BackHandler { viewModel.setShowStatsScreen(false) }
            StatsScreen(
                viewModel = viewModel,
                onBack = { viewModel.setShowStatsScreen(false) }
            )
        }

        // Equalizer overlay
        AnimatedVisibility(
            visible = showEqualizerScreen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BackHandler { viewModel.setShowEqualizerScreen(false) }
            EqualizerScreen(
                viewModel = viewModel,
                onBack = { viewModel.setShowEqualizerScreen(false) }
            )
        }

        // Car Mode overlay
        AnimatedVisibility(
            visible = isCarMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BackHandler { viewModel.setCarMode(false) }
            CarPlayerScreen(
                playbackState = playbackState,
                keepScreenOn = settings.keepScreenOnInCarMode,
                onPlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.playNext() },
                onPrevious = { viewModel.playPrevious() },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onExitCarMode = { viewModel.setCarMode(false) }
            )
        }
    }

    // Dialogs & Bottom Sheets rendered above active screen
    if (lyricsSong != null) {
        LyricsSheet(
            song = lyricsSong!!,
            currentPositionMs = playbackState.currentPositionMs,
            viewModel = viewModel,
            onImportLrcClick = { importLrcLauncher.launch(arrayOf("*/*")) },
            onSeekTo = { pos -> viewModel.seekTo(pos) },
            onDismiss = { lyricsSong = null }
        )
    }

    if (songsPendingDeleteConfirm.isNotEmpty()) {
        ConfirmPermanentDeleteDialog(
            songs = songsPendingDeleteConfirm,
            onConfirmDelete = {
                val target = songsPendingDeleteConfirm
                songsPendingDeleteConfirm = emptyList()
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingLegacyDelete = target
                    legacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    viewModel.requestDeleteSongsPermanently(target)
                }
            },
            onDismiss = { songsPendingDeleteConfirm = emptyList() }
        )
    }

    if (songForTagEditor != null) {
        val target = songForTagEditor!!
        EditSongTagsDialog(
            song = target,
            onDismiss = { viewModel.setSongForTagEditor(null) },
            onSave = { title, artist, album, genre, year, track, newFileName ->
                viewModel.requestUpdateSongTags(target, title, artist, album, genre, year, track, newFileName)
            }
        )
    }

    if (songForFileDetails != null) {
        FileDetailsDialog(
            details = songForFileDetails!!,
            onDismiss = { viewModel.clearFileDetails() }
        )
    }

    if (songForArtworkEdit != null) {
        val target = songForArtworkEdit!!
        AlertDialog(
            onDismissRequest = { viewModel.setSongForArtworkEdit(null) },
            title = { Text("Edit Album Artwork") },
            text = { Text("Select a custom cover image for \"${target.title}\" or restore the original embedded artwork.") },
            confirmButton = {
                Button(onClick = {
                    pickArtworkLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Text("Choose Image")
                }
            },
            dismissButton = {
                if (!target.customArtUri.isNullOrBlank()) {
                    OutlinedButton(onClick = { viewModel.updateCustomArtwork(target, null) }) {
                        Text("Restore Original")
                    }
                } else {
                    TextButton(onClick = { viewModel.setSongForArtworkEdit(null) }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (duplicatesList != null) {
        DuplicateSongsDialog(
            groups = duplicatesList!!,
            onDeleteSong = { song ->
                songsPendingDeleteConfirm = listOf(song)
            },
            onDismiss = { viewModel.closeDuplicatesDialog() }
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.showCreatePlaylistDialog(false) },
            onConfirm = { name -> viewModel.createPlaylist(name) }
        )
    }

    if (songsToAddToPlaylist.isNotEmpty()) {
        AddToPlaylistSheet(
            songs = songsToAddToPlaylist,
            playlists = playlists,
            onPlaylistSelected = { playlist ->
                viewModel.addSongsToPlaylist(playlist, songsToAddToPlaylist)
            },
            onCreateNewPlaylistClick = {
                viewModel.showCreatePlaylistDialog(true)
            },
            onDismiss = {
                viewModel.setSongsToAddToPlaylist(emptyList())
            }
        )
    }
}
