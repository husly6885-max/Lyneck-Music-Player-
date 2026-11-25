package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.service.MusicService

class LyneckMusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val service = MusicService.instance
        val state = service?.playerManager?.playbackState?.value
        updateWidgets(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetIds = appWidgetIds,
            title = state?.currentSong?.title,
            artist = state?.currentSong?.artist,
            artUri = state?.currentSong?.albumArtUri,
            isPlaying = state?.isPlaying ?: false
        )
    }

    companion object {
        fun pushUpdate(
            context: Context,
            title: String?,
            artist: String?,
            artUri: Uri?,
            isPlaying: Boolean
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, LyneckMusicWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                updateWidgets(context, manager, ids, title, artist, artUri, isPlaying)
            }
        }

        private fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            title: String?,
            artist: String?,
            artUri: Uri?,
            isPlaying: Boolean
        ) {
            val artBitmap = artUri?.let { loadScaledBitmap(context, it) }

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_music_player)

                views.setTextViewText(
                    R.id.widget_song_title,
                    title ?: context.getString(R.string.app_name)
                )
                views.setTextViewText(
                    R.id.widget_song_artist,
                    artist ?: context.getString(R.string.widget_idle_subtitle)
                )
                views.setImageViewResource(
                    R.id.widget_btn_play_pause,
                    if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                )

                if (artBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_album_art, artBitmap)
                } else {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_widget_music_note)
                }

                // Open App intent on root click
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openPending = PendingIntent.getActivity(
                    context,
                    10,
                    openIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_root, openPending)

                // Control intents sent to MusicService
                views.setOnClickPendingIntent(
                    R.id.widget_btn_play_pause,
                    serviceActionPendingIntent(context, MusicService.ACTION_WIDGET_PLAY_PAUSE, 11)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_next,
                    serviceActionPendingIntent(context, MusicService.ACTION_WIDGET_NEXT, 12)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_prev,
                    serviceActionPendingIntent(context, MusicService.ACTION_WIDGET_PREV, 13)
                )

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        private fun serviceActionPendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, MusicService::class.java).apply {
                this.action = action
            }
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getService(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
        }

        private fun loadScaledBitmap(context: Context, uri: Uri): Bitmap? {
            return try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
