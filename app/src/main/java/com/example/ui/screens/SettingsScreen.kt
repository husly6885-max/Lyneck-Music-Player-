package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.AccentColorOption
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.PlayerScreenStyle
import com.example.ui.MusicViewModel
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onOpenEqualizer: () -> Unit,
    onOpenStats: () -> Unit,
    onExportBackupJson: () -> Unit,
    onImportBackupJson: () -> Unit,
    onImportM3u: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val volumeState by viewModel.volumeState.collectAsState()
    val eqState by viewModel.eqState.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val songs by viewModel.allSongs.collectAsState()
    val rawSongs by viewModel.rawScannedSongs.collectAsState()
    val excludedFolders by viewModel.excludedFolders.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isCloudSyncing by viewModel.isCloudSyncing.collectAsState()

    val albumsCount = remember(songs) { viewModel.repository.getAlbums(songs).size }
    val artistsCount = remember(songs) { viewModel.repository.getArtists(songs).size }
    val allFolders = remember(rawSongs) { viewModel.repository.getFolders(rawSongs) }
    var showFolderManager by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp)
            .testTag("settings_screen")
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        // 1. PLAYBACK
        SectionHeader("1. Playback")
        SettingsCard {
            SwitchRow(
                title = "Gapless Playback",
                subtitle = "Seamless track transitions with no pause",
                checked = settings.gaplessPlayback,
                onCheckedChange = { viewModel.setGaplessPlayback(it) }
            )
            SettingsDivider()
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Crossfade Duration", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (settings.crossfadeSeconds == 0) "Off" else "${settings.crossfadeSeconds}s",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = settings.crossfadeSeconds.toFloat(),
                    onValueChange = { viewModel.setCrossfadeSeconds(it.toInt()) },
                    valueRange = 0f..12f,
                    steps = 11
                )
            }
            SettingsDivider()
            SwitchRow(
                title = "Smooth Fade In & Fade Out",
                subtitle = "Gentle volume ramp on play & pause",
                checked = settings.fadeInOut,
                onCheckedChange = { viewModel.setFadeInOut(it) }
            )
            SettingsDivider()
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Playback Speed", fontWeight = FontWeight.SemiBold)
                    Text(String.format(Locale.US, "%.2fx", settings.playbackSpeed), color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = settings.playbackSpeed,
                    onValueChange = { viewModel.setPlaybackSpeed(it) },
                    valueRange = 0.5f..2.0f
                )
            }
            SettingsDivider()
            SwitchRow(
                title = "Skip Silence",
                subtitle = "Automatically bypass silent gaps in audio",
                checked = settings.skipSilence,
                onCheckedChange = { viewModel.setSkipSilence(it) }
            )
            SettingsDivider()
            SwitchRow(
                title = "Remember Playback Position",
                subtitle = "Resume last song & timestamp on restart",
                checked = settings.rememberPlaybackPosition,
                onCheckedChange = { viewModel.setRememberPlaybackPosition(it) }
            )
        }

        // 2. AUDIO & VOLUME
        SectionHeader("2. Audio & Volume")
        SettingsCard {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Device Music Volume (Hardware Synced)", fontWeight = FontWeight.SemiBold)
                    Text("${volumeState.percentage}%", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = volumeState.ratio,
                    onValueChange = { viewModel.setDeviceVolumeRatio(it) }
                )
            }
            SettingsDivider()
            SwitchRow(
                title = "Volume Normalization",
                subtitle = "Even out loudness across different tracks",
                checked = settings.volumeNormalization,
                onCheckedChange = { viewModel.setVolumeNormalization(it) }
            )
            SettingsDivider()
            SwitchRow(
                title = "ReplayGain Compensation",
                subtitle = "Apply ReplayGain track gain metadata",
                checked = settings.replayGainEnabled,
                onCheckedChange = { viewModel.setReplayGainEnabled(it) }
            )
        }

        // 3. EQUALIZER
        SectionHeader("3. Equalizer & Sound Effects")
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenEqualizer)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Equalizer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Open Equalizer Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (settings.equalizerEnabled) "Active: ${eqState.activePresetName}" else "Equalizer is currently off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(onClick = onOpenEqualizer) { Text("Configure") }
            }
        }

        // 4. LYRICS
        SectionHeader("4. Lyrics")
        SettingsCard {
            SwitchRow(
                title = "Auto-load External .LRC Files",
                subtitle = "Automatically match .lrc files beside audio tracks",
                checked = settings.autoLoadExternalLrc,
                onCheckedChange = { viewModel.setAutoLoadExternalLrc(it) }
            )
            SettingsDivider()
            SwitchRow(
                title = "Karaoke Line Highlight",
                subtitle = "Highlight current synced lyric line",
                checked = settings.karaokeHighlight,
                onCheckedChange = { viewModel.setKaraokeHighlight(it) }
            )
        }

        // 5. LIBRARY & STATS
        SectionHeader("5. Library, Duplicates & Stats")
        SettingsCard {
            SwitchRow(
                title = "Auto-Scan on Startup",
                subtitle = "Automatically refresh MediaStore when opening app",
                checked = settings.autoScanOnStartup,
                onCheckedChange = { viewModel.setAutoScanOnStartup(it) }
            )
            SettingsDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rescan Music Library", fontWeight = FontWeight.SemiBold)
                    Text("Refresh tracks, genres, albums & artwork", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = { viewModel.rescanMusic() },
                    enabled = !isScanning,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("settings_rescan_button")
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rescan")
                    }
                }
            }
            SettingsDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenStats,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.BarChart, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stats")
                }
                OutlinedButton(
                    onClick = { viewModel.findDuplicateSongs() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.FilterNone, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Duplicates")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatBadge(icon = Icons.Rounded.MusicNote, label = "Songs", count = songs.size)
                StatBadge(icon = Icons.Rounded.Album, label = "Albums", count = albumsCount)
                StatBadge(icon = Icons.Rounded.Person, label = "Artists", count = artistsCount)
                StatBadge(icon = Icons.AutoMirrored.Rounded.QueueMusic, label = "Playlists", count = playlists.size)
            }
        }

        // 6. FOLDERS
        SectionHeader("6. Folder Exclusions")
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFolderManager = !showFolderManager }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.FolderOff, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Manage Music Folders", fontWeight = FontWeight.Bold)
                        Text("${excludedFolders.size} folder(s) hidden from library", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(if (showFolderManager) "Hide" else "Edit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (showFolderManager) {
                SettingsDivider()
                allFolders.forEach { folder ->
                    val isExcluded = folder.path in excludedFolders
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(folder.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${folder.songCount} files • ${folder.path}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Switch(
                            checked = !isExcluded,
                            onCheckedChange = { include ->
                                if (include) viewModel.includeFolder(folder.path)
                                else viewModel.excludeFolder(folder.path)
                            }
                        )
                    }
                }
            }
        }

        // 7. NOTIFICATIONS
        SectionHeader("7. Notifications & Background Playback")
        SettingsCard {
            SwitchRow(
                title = "Show Album Artwork in Notification",
                subtitle = "Display album cover on lock screen & media shade",
                checked = settings.showArtworkInNotification,
                onCheckedChange = { viewModel.setShowArtworkInNotification(it) }
            )
            SettingsDivider()
            SwitchRow(
                title = "Keep Playing When App Swiped Away",
                subtitle = "Continue foreground service if UI is dismissed",
                checked = settings.keepPlayingOnTaskRemove,
                onCheckedChange = { viewModel.setKeepPlayingOnTaskRemove(it) }
            )
        }

        // 8. APPEARANCE
        SectionHeader("8. Appearance & Player Style")
        SettingsCard {
            ThemeOptionItem(
                title = stringResource(R.string.ocean_navy_mode),
                icon = Icons.Rounded.DarkMode,
                selected = settings.themeMode == AppThemeMode.OCEAN_NAVY,
                onClick = { viewModel.setThemeMode(AppThemeMode.OCEAN_NAVY) }
            )
            SettingsDivider()
            ThemeOptionItem(
                title = stringResource(R.string.dark_mode),
                icon = Icons.Rounded.DarkMode,
                selected = settings.themeMode == AppThemeMode.DARK,
                onClick = { viewModel.setThemeMode(AppThemeMode.DARK) }
            )
            SettingsDivider()
            ThemeOptionItem(
                title = stringResource(R.string.amoled_mode),
                icon = Icons.Rounded.DarkMode,
                selected = settings.themeMode == AppThemeMode.AMOLED,
                onClick = { viewModel.setThemeMode(AppThemeMode.AMOLED) }
            )
            SettingsDivider()
            ThemeOptionItem(
                title = stringResource(R.string.light_mode),
                icon = Icons.Rounded.LightMode,
                selected = settings.themeMode == AppThemeMode.LIGHT,
                onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) }
            )
            SettingsDivider()
            ThemeOptionItem(
                title = stringResource(R.string.system_mode),
                icon = Icons.Rounded.PhoneAndroid,
                selected = settings.themeMode == AppThemeMode.SYSTEM,
                onClick = { viewModel.setThemeMode(AppThemeMode.SYSTEM) }
            )
            SettingsDivider()
            Text("Accent Color", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AccentColorOption.entries.forEach { option ->
                    val selected = settings.accentColor == option
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(option.primaryHex))
                            .then(
                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { viewModel.setAccentColor(option) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Icon(Icons.Rounded.Check, contentDescription = option.label, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SettingsDivider()
            Text("Now Playing Layout Style", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerScreenStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.playerStyle == style,
                        onClick = { viewModel.setPlayerStyle(style) },
                        label = { Text(style.label) }
                    )
                }
            }
        }

        // 9. STORAGE, BACKUP & CLOUD SYNC
        SectionHeader("9. Storage, Backup & Cloud Sync")
        SettingsCard {
            Text("Local Offline Backup (JSON & M3U)", fontWeight = FontWeight.Bold)
            Text(
                "Export playlists, favorites, lyrics, and all audio/EQ preferences to a file on your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExportBackupJson, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export")
                }
                OutlinedButton(onClick = onImportBackupJson, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.FileDownload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Restore")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onImportM3u, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Import M3U / M3U8 Playlist")
            }

            SettingsDivider()
            Spacer(Modifier.height(6.dp))

            // Preserved Firebase Cloud Sync
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudUpload, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentUser != null) (currentUser?.displayName ?: "Signed In") else "Optional Cloud Sync (Firebase)",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (currentUser != null) (currentUser?.email ?: "") else "Completely optional — Lyneck works 100% offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (currentUser == null) {
                OutlinedButton(
                    onClick = { viewModel.signInWithGoogle() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("sign_in_google_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Login, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign in with Google")
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.backupToCloud() },
                        enabled = !isCloudSyncing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("backup_cloud_button")
                    ) {
                        if (isCloudSyncing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Backup Now")
                        }
                    }
                    OutlinedButton(onClick = { viewModel.signOut() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("sign_out_button")) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sign out")
                    }
                }
            }
        }

        // 10. ABOUT LYNECK PLAYER
        SectionHeader("10. About Lyneck Music Player")
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("100% Offline Audio Engine", fontWeight = FontWeight.Bold)
                    Text("Local MediaStore playback, no accounts required for music.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            SettingsDivider()
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.about_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.version_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.version_value), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
    )
}

@Composable
private fun ThemeOptionItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StatBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
