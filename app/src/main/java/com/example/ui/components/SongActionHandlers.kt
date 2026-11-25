package com.example.ui.components

import com.example.data.model.Song

data class SongActionHandlers(
    val onOpenLyrics: (Song) -> Unit,
    val onShareSong: (Song) -> Unit,
    val onSetRingtone: (Song) -> Unit,
    val onCopyToFolder: (Song) -> Unit,
    val onMoveToFolder: (Song) -> Unit,
    val onRequestDeleteSongs: (List<Song>) -> Unit
)
