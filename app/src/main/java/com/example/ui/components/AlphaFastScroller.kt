package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import kotlinx.coroutines.launch

object AlphaIndexHelper {
    val ALPHABET: List<String> = ('A'..'Z').map { it.toString() } + "#"

    fun letterForTitle(title: String): String {
        val first = title.trim().firstOrNull()?.uppercaseChar()
        return if (first != null && first in 'A'..'Z') first.toString() else "#"
    }

    /**
     * Maps each letter bucket ('A'..'Z', '#') to its first item index in the LazyColumn.
     * [headerCount] offsets past non-song header items.
     */
    fun buildIndexMap(songs: List<Song>, headerCount: Int = 1): Map<String, Int> =
        buildIndexMapForTitles(songs.map { it.title }, headerCount)

    fun buildIndexMapForTitles(titles: List<String>, headerCount: Int = 1): Map<String, Int> {
        val map = LinkedHashMap<String, Int>()
        titles.forEachIndexed { idx, title ->
            val key = letterForTitle(title)
            if (key !in map) {
                map[key] = idx + headerCount
            }
        }
        return map
    }

    /**
     * Resolves an exact or nearest populated list index for [letter] so scrubbing
     * through sparse letters still glides smoothly across the song list.
     */
    fun findTargetIndex(letter: String, indexMap: Map<String, Int>): Int? {
        indexMap[letter]?.let { return it }
        if (indexMap.isEmpty()) return null
        val startPos = ALPHABET.indexOf(letter).coerceAtLeast(0)
        for (i in startPos until ALPHABET.size) {
            indexMap[ALPHABET[i]]?.let { return it }
        }
        for (i in startPos downTo 0) {
            indexMap[ALPHABET[i]]?.let { return it }
        }
        return null
    }
}

@Composable
fun AlphabetFastScroller(
    songs: List<Song>,
    listState: LazyListState,
    headerCount: Int = 1,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val letterIndexMap = remember(songs, headerCount) {
        AlphaIndexHelper.buildIndexMap(songs, headerCount)
    }

    var columnHeightPx by remember { mutableIntStateOf(0) }
    var activeScrubLetter by remember { mutableStateOf<String?>(null) }

    val visibleLetter by remember(songs, headerCount) {
        derivedStateOf {
            val firstSongIdx = (listState.firstVisibleItemIndex - headerCount).coerceAtLeast(0)
            songs.getOrNull(firstSongIdx)?.let { AlphaIndexHelper.letterForTitle(it.title) }
        }
    }

    val highlightedLetter = activeScrubLetter ?: visibleLetter

    fun jumpToOffsetY(y: Float) {
        if (columnHeightPx <= 0) return
        val fraction = (y / columnHeightPx.toFloat()).coerceIn(0f, 0.9999f)
        val letterIdx = (fraction * AlphaIndexHelper.ALPHABET.size)
            .toInt()
            .coerceIn(0, AlphaIndexHelper.ALPHABET.lastIndex)
        val letter = AlphaIndexHelper.ALPHABET[letterIdx]
        activeScrubLetter = letter
        val target = AlphaIndexHelper.findTargetIndex(letter, letterIndexMap)
        if (target != null) {
            coroutineScope.launch {
                listState.scrollToItem(target)
            }
        }
    }

    Box(modifier = modifier) {
        // Floating active letter preview bubble while scrubbing
        AnimatedVisibility(
            visible = activeScrubLetter != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 36.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = activeScrubLetter ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(24.dp)
                .onSizeChanged { columnHeightPx = it.height }
                .pointerInput(songs, letterIndexMap) {
                    detectTapGestures { offset ->
                        jumpToOffsetY(offset.y)
                        activeScrubLetter = null
                    }
                }
                .pointerInput(songs, letterIndexMap) {
                    detectVerticalDragGestures(
                        onDragStart = { offset -> jumpToOffsetY(offset.y) },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            jumpToOffsetY(change.position.y)
                        },
                        onDragEnd = { activeScrubLetter = null },
                        onDragCancel = { activeScrubLetter = null }
                    )
                }
                .testTag("alphabet_scroller"),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlphaIndexHelper.ALPHABET.forEach { letter ->
                val isPresent = letter in letterIndexMap
                val isHighlighted = highlightedLetter == letter
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.background.copy(alpha = 0f)
                        )
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter,
                        fontSize = if (isHighlighted) 11.sp else 9.5.sp,
                        fontWeight = when {
                            isHighlighted -> FontWeight.ExtraBold
                            isPresent -> FontWeight.SemiBold
                            else -> FontWeight.Normal
                        },
                        color = when {
                            isHighlighted -> MaterialTheme.colorScheme.primary
                            isPresent -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        }
                    )
                }
            }
        }
    }
}
