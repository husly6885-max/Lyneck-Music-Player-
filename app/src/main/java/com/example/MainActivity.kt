package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppThemeMode
import com.example.ui.MusicViewModel
import com.example.ui.NavigationScreen
import com.example.ui.components.AddToPlaylistSheet
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.MiniPlayer
import com.example.ui.components.NowPlayingScreen
import com.example.ui.components.PermissionRequiredCard
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val musicViewModel: MusicViewModel = viewModel()
            val themeMode by musicViewModel.themeMode.collectAsState()

            val isDark = when (themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark) {
                MainAppContent(viewModel = musicViewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsState()
    val hasStoragePermission by viewModel.hasStoragePermission.collectAsState()
    val showCreatePlaylistDialog by viewModel.showCreatePlaylistDialog.collectAsState()
    val songToAddToPlaylist by viewModel.songToAddToPlaylist.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Listen to user notifications/snackbars
    LaunchedEffect(viewModel) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Determine audio permission depending on Android version
    val requiredAudioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val audioGranted = permissionsMap[requiredAudioPermission] == true
        if (audioGranted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    // Check permission on startup
    LaunchedEffect(Unit) {
        val isAudioGranted = ContextCompat.checkSelfPermission(
            context,
            requiredAudioPermission
        ) == PackageManager.PERMISSION_GRANTED

        if (isAudioGranted) {
            viewModel.onPermissionGranted()
        } else {
            val permissionsToRequest = mutableListOf(requiredAudioPermission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    if (isNowPlayingExpanded && playbackState.currentSong != null) {
        BackHandler {
            viewModel.setNowPlayingExpanded(false)
        }
        NowPlayingScreen(
            playbackState = playbackState,
            onCollapse = { viewModel.setNowPlayingExpanded(false) },
            onPlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.playNext() },
            onPrevious = { viewModel.playPrevious() },
            onSeek = { viewModel.seekTo(it) },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleRepeat = { viewModel.toggleRepeat() },
            onVolumeChange = { viewModel.setVolume(it) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onQueueItemClick = { viewModel.playQueueIndex(it) }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                // Mini Player floats above bottom bar
                if (playbackState.currentSong != null) {
                    MiniPlayer(
                        playbackState = playbackState,
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onNextClick = { viewModel.playNext() },
                        onExpandClick = { viewModel.setNowPlayingExpanded(true) }
                    )
                }

                // Bottom Navigation
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = currentScreen == NavigationScreen.LIBRARY,
                        onClick = { viewModel.setNavigationScreen(NavigationScreen.LIBRARY) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.LibraryMusic,
                                contentDescription = stringResource(R.string.nav_library)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_library)) },
                        modifier = Modifier.testTag("nav_library_item")
                    )

                    NavigationBarItem(
                        selected = currentScreen == NavigationScreen.SEARCH,
                        onClick = { viewModel.setNavigationScreen(NavigationScreen.SEARCH) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.nav_search)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_search)) },
                        modifier = Modifier.testTag("nav_search_item")
                    )

                    NavigationBarItem(
                        selected = currentScreen == NavigationScreen.PLAYLISTS,
                        onClick = { viewModel.setNavigationScreen(NavigationScreen.PLAYLISTS) },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = stringResource(R.string.nav_playlists)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_playlists)) },
                        modifier = Modifier.testTag("nav_playlists_item")
                    )

                    NavigationBarItem(
                        selected = currentScreen == NavigationScreen.SETTINGS,
                        onClick = { viewModel.setNavigationScreen(NavigationScreen.SETTINGS) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.nav_settings)
                            )
                        },
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
                    NavigationScreen.LIBRARY -> LibraryScreen(viewModel = viewModel)
                    NavigationScreen.SEARCH -> SearchScreen(viewModel = viewModel)
                    NavigationScreen.PLAYLISTS -> PlaylistsScreen(viewModel = viewModel)
                    NavigationScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.showCreatePlaylistDialog(false) },
            onConfirm = { name -> viewModel.createPlaylist(name) }
        )
    }

    // Add to Playlist Bottom Sheet
    if (songToAddToPlaylist != null) {
        AddToPlaylistSheet(
            song = songToAddToPlaylist!!,
            playlists = playlists,
            onPlaylistSelected = { playlist ->
                viewModel.addSongToPlaylist(playlist, songToAddToPlaylist!!)
            },
            onCreateNewPlaylistClick = {
                viewModel.showCreatePlaylistDialog(true)
            },
            onDismiss = {
                viewModel.setSongToAddToPlaylist(null)
            }
        )
    }
}
