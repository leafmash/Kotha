package com.kotha.app.data.profile

import android.net.Uri
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.kotha.app.data.auth.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class ProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val photoUploader: PhotoUploader
) {

    suspend fun save(name: String, bio: String) {
        val user = authRepository.user ?: error("signed out")
        firestore.collection("users").document(user.uid)
            .update(mapOf("name" to name, "bio" to bio)).await()
        runCatching {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
        }
    }

    suspend fun changePhoto(uri: Uri) {
        val user = authRepository.user ?: error("signed out")
        val url = photoUploader.upload(uri)
        firestore.collection("users").document(user.uid).update(mapOf("photo" to url)).await()
    }
}
