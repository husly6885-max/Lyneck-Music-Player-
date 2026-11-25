package com.example.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudPlaylist(
    @DocumentId val id: String = "",
    val name: String = "",
    val songCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudFavorite(
    @DocumentId val id: String = "",
    val songTitle: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0L,
    @ServerTimestamp val addedAt: Timestamp? = null
)
