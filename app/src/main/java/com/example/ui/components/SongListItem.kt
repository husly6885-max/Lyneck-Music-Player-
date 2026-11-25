package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Song

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: Song,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    onSongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onPlayClick: (() -> Unit)? = null,
    onPlayNextClick: (() -> Unit)? = null,
    onAddToQueueClick: (() -> Unit)? = null,
    onEditTagsClick: (() -> Unit)? = null,
    onEditArtworkClick: (() -> Unit)? = null,
    onLyricsClick: (() -> Unit)? = null,
    onFileInfoClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    onSetRingtoneClick: (() -> Unit)? = null,
    onCopyToFolderClick: (() -> Unit)? = null,
    onMoveToFolderClick: (() -> Unit)? = null,
    onDeletePermanentlyClick: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    val rowBackground = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        isCurrentSong -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBackground)
            .combinedClickable(
                onClick = onSongClick,
                onLongClick = onLongClick
            )
            .testTag("song_item_${song.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(22.dp)
                )
            } else if (isCurrentSong) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = if (isPlaying) "Playing" else "Paused",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(20.dp)
                )
            }

            // Crisp 2-line typography row (fast & smooth on low-end devices, matches screenshot)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = " • ${song.formattedDuration}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                    if (song.isFavorite) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = "Favorite",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            if (!isSelectionMode) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("more_btn_${song.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Song options for ${song.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // 1. Play
                        DropdownMenuItem(
                            text = { Text("Play") },
                            leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                            onClick = {
                                showMenu = false
                                (onPlayClick ?: onSongClick).invoke()
                            }
                        )
                        // 2. Play Next
                        onPlayNextClick?.let { callback ->
                            DropdownMenuItem(
                                text = { Text("Play Next") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null) },
                                onClick = { showMenu = false; callback() }
                            )
                        }
                        // 3. Add to Queue
                        onAddToQueueClick?.let { callback ->
                            DropdownMenuItem(
                                text = { Text("Add to Queue") },
                                leadingIcon = { Icon(Icons.Rounded.AddToQueue, null) },
                                onClick = { showMenu = false; callback() }
                            )
                        }
                        // 4. Add to Playlist
                        DropdownMenuItem(
                            text = { Text("Add to Playlist") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                            onClick = { showMenu = false; onAddToPlaylistClick() }
                        )
                        // 5. Add to Favorites / Remove from Favorites
                        DropdownMenuItem(
                            text = {
                                Text(if (song.isFavorite) "Remove from Favorites" else "Add to Favorites")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (song.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = { showMenu = false; onFavoriteClick() }
                        )

                        HorizontalDivider()

                        // 6. Edit Music Information
                        onEditTagsClick?.let { callback ->
                            DropdownMenuItem(
                                text = { Text("Edit Music Information") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = { showMenu = false; callback() }
                            )
                        }
                        // 7. Set as Ringtone
                        onSetRingtoneClick?.let { callback ->
                            DropdownMenuItem(
                                text = { Text("Set as Ringtone") },
                                leadingIcon = { Icon(Icons.Rounded.NotificationsActive, null) },
                                onClick = { showMenu = false; callback() }
                            )
                        }
                        // 8. Share
                        onShareClick?.let { callback ->
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                onClick = { showMenu = false; callback() }
                            )
                        }
                        // 9. View Details
                        onFileInfoClick?.let { callback ->
                            DropdownMenuItem(
                                text = { Text("View Details") },
                                leadingIcon = { Icon(Icons.Rounded.Info, null) },
                                onClick = { showMenu = false; callback() }
                            )
                        }

                        if (onLyricsClick != null || onEditArtworkClick != null || onCopyToFolderClick != null || onMoveToFolderClick != null) {
                            HorizontalDivider()
                        }

                        onLyricsClick?.let { callback ->
                            DropdownMenuItem(
                                text = { Text("Lyrics") },
                                leadingIcon = { Icon(Icons.Rounded.Lyrics, null) },
                                onClick = { showMenu = false; callback() }
                            )
                        }
                        onEditArtworkClick?.let { callback ->
                            DropdownMenuItem(
                                text = { Text("Edit Album Artwork") },
                                leadingIcon = { Icon(Icons.Rounded.Image, null) },
                                onClick = { showMenu = false; callback() }
                            )
                        }
                        onCopyToFolderClick?.let { callback ->
                            DropdownMenuItem(
                                text = { Text("Copy to Folder…") },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                onClick = { showMenu = false; callback() }
                            )
                        }
                        onMoveToFolderClick?.let { callback ->
                            DropdownMenuItem(
                                text = { Text("Move to Folder…") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null) },
                                onClick = { showMenu = false; callback() }
                            )
                        }

                        // 10. Delete Permanently
                        onDeletePermanentlyClick?.let { callback ->
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete Permanently",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DeleteForever,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { showMenu = false; callback() }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
