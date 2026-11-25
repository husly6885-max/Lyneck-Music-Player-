package com.example.data.firebase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import com.google.firebase.firestore.FirebaseFirestoreException

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun testCreateAndObserveCloudPlaylist() = runBlocking {
    val uid = signInTestUser("alice_rule@test.com")
    val repository = CloudSyncRepository(firestore)

    val playlist = CloudPlaylist(
      id = "pl_rule_1",
      name = "Rule Playlist",
      songCount = 3L
    )

    val saveResult = repository.saveCloudPlaylist(uid, playlist)
    assertTrue(saveResult.isSuccess)

    val playlists = withTimeout(5000L) {
      repository.observeCloudPlaylists(uid).first()
    }
    assertTrue(playlists.any { it.name == "Rule Playlist" })
  }

  @Test
  fun testUnauthenticatedAccessFails() = runBlocking {
    auth.signOut()
    val repository = CloudSyncRepository(firestore)

    try {
      withTimeout(3000L) {
        repository.observeCloudPlaylists("some_user_id").first()
      }
      fail("Expected FirebaseFirestoreException for unauthenticated access")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }

  @Test
  fun testCrossUserAccessFails() = runBlocking {
    val aliceUid = signInTestUser("alice_cross@test.com")
    val aliceRepo = CloudSyncRepository(firestore)
    aliceRepo.saveCloudPlaylist(aliceUid, CloudPlaylist(id = "alice_pl", name = "Alice Playlist"))

    // Sign in as Bob
    val bobUid = signInTestUser("bob_cross@test.com")
    val bobRepo = CloudSyncRepository(firestore)

    try {
      withTimeout(3000L) {
        bobRepo.observeCloudPlaylists(aliceUid).first()
      }
      fail("Expected FirebaseFirestoreException for cross-user access")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }
}
