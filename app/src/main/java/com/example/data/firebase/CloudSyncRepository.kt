package com.example.data.firebase

import android.content.Context
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeCloudPlaylists(userId: String): Flow<List<CloudPlaylist>> {
        return db.collection("users").document(userId)
            .collection("cloud_playlists")
            .snapshots()
            .map { snapshot -> snapshot.toObjects(CloudPlaylist::class.java) }
    }

    suspend fun saveCloudPlaylist(userId: String, playlist: CloudPlaylist): Result<Unit> {
        return runCatching {
            val collection = db.collection("users").document(userId).collection("cloud_playlists")
            val docRef = if (playlist.id.isBlank()) collection.document() else collection.document(playlist.id)
            docRef.set(
                mapOf(
                    "name" to playlist.name,
                    "songCount" to playlist.songCount,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun deleteCloudPlaylist(userId: String, playlistId: String): Result<Unit> {
        return runCatching {
            db.collection("users").document(userId)
                .collection("cloud_playlists")
                .document(playlistId)
                .delete()
                .await()
        }
    }

    fun observeCloudFavorites(userId: String): Flow<List<CloudFavorite>> {
        return db.collection("users").document(userId)
            .collection("cloud_favorites")
            .snapshots()
            .map { snapshot -> snapshot.toObjects(CloudFavorite::class.java) }
    }

    suspend fun saveCloudFavorite(userId: String, favorite: CloudFavorite): Result<Unit> {
        return runCatching {
            val collection = db.collection("users").document(userId).collection("cloud_favorites")
            val docRef = if (favorite.id.isBlank()) collection.document() else collection.document(favorite.id)
            docRef.set(
                mapOf(
                    "songTitle" to favorite.songTitle,
                    "artist" to favorite.artist,
                    "album" to favorite.album,
                    "duration" to favorite.duration,
                    "addedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun syncUserProfile(userId: String, profile: UserProfile): Result<Unit> {
        return runCatching {
            val docRef = db.collection("users").document(userId)
            docRef.set(
                mapOf(
                    "displayName" to profile.displayName,
                    "email" to profile.email,
                    "photoUrl" to profile.photoUrl,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()
        }
    }
}
