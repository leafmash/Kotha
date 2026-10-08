package com.kotha.app.data.profile

import android.net.Uri
import com.kotha.app.data.media.CloudinaryUploader
import com.kotha.app.data.media.MediaStager
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoUploader @Inject constructor(
    private val stager: MediaStager,
    private val uploader: CloudinaryUploader
) {

    suspend fun upload(uri: Uri): String {
        val file = stager.avatarFile(uri) ?: throw IOException("cannot read image")
        try {
            return uploader.upload(file, file.name, "image/jpeg") { }.url
        } finally {
            file.delete()
        }
    }
}
