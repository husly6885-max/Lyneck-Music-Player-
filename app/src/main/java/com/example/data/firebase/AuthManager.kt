package com.example.data.firebase

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthManager(
    private val context: Context,
    private val authOverride: FirebaseAuth? = null
) {
    private val credentialManager = CredentialManager.create(context)

    private fun getFirebaseAuth(): FirebaseAuth? {
        if (authOverride != null) return authOverride
        return if (FirebaseApp.getApps(context.applicationContext).isNotEmpty()) {
            FirebaseAuth.getInstance()
        } else {
            null
        }
    }

    val currentUser: FirebaseUser?
        get() = getFirebaseAuth()?.currentUser

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val firebaseAuth = getFirebaseAuth()
        if (firebaseAuth == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { authInstance ->
            trySend(authInstance.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    @SuppressLint("DiscouragedApi")
    suspend fun signInWithGoogle(): Result<FirebaseUser> {
        return runCatching {
            val firebaseAuth = checkNotNull(getFirebaseAuth()) {
                "Firebase is not configured in this build (missing app/google-services.json)."
            }
            val webClientResId = context.resources.getIdentifier(
                "default_web_client_id",
                "string",
                context.packageName
            )
            val serverClientId = if (webClientResId != 0) {
                context.getString(webClientResId)
            } else {
                ""
            }
            require(serverClientId.isNotBlank()) {
                "Google Sign-In is unavailable because default_web_client_id is missing (add app/google-services.json)."
            }

            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
            checkNotNull(authResult.user) { "Authentication resulted in null user" }
        }.recoverCatching { exception ->
            if (exception is GetCredentialCancellationException) {
                throw exception
            }
            throw exception
        }
    }

    suspend fun signOut() {
        getFirebaseAuth()?.signOut()
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {}
    }
}
