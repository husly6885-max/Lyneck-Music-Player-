package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Lyneck Music Player", appName)
  }

  @Test
  fun `song formatted duration test`() {
    val song = Song(
      id = 1L,
      title = "Test Song",
      artist = "Test Artist",
      album = "Test Album",
      duration = 215000L, // 3 mins 35 seconds
      contentUri = Uri.parse("content://media/external/audio/media/1"),
      albumId = 10L
    )

    assertEquals("3:35", song.formattedDuration)
    assertFalse(song.isFavorite)
  }
}
