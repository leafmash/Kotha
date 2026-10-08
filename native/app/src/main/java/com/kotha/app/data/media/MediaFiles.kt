package com.kotha.app.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.kotha.app.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaFiles {

    fun mimeOf(name: String, type: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        val byName = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        return byName ?: when (type) {
            "image" -> "image/*"
            "video" -> "video/*"
            "audio" -> "audio/*"
            else -> "*/*"
        }
    }

    fun displayName(name: String, url: String, id: String, type: String): String {
        if (name.isNotEmpty()) return name
        val last = url.substringBefore('?').substringAfterLast('/')
        if (last.contains('.')) return last
        return "cova_$id" + if (type == "video") ".mp4" else ".jpg"
    }

    suspend fun fetch(context: Context, url: String, fileName: String): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val safe = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { "file" }
            val target = File(dir, "${url.hashCode().toUInt()}_$safe")
            if (target.exists() && target.length() > 0) return@withContext target
            download(url) { input -> target.outputStream().use { input.copyTo(it) } }
            target
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun copyToUri(context: Context, url: String, target: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = context.contentResolver.openOutputStream(target) ?: throw IOException("cannot open $target")
            output.use { sink -> download(url) { input -> input.copyTo(sink) } }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    fun share(context: Context, file: File, mime: String): Boolean = try {
        val uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
        true
    } catch (e: Exception) {
        false
    }

    private fun download(url: String, consume: (java.io.InputStream) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            if (connection.responseCode !in 200..299) throw IOException("status ${connection.responseCode}")
            connection.inputStream.use(consume)
        } finally {
            connection.disconnect()
        }
    }
}
