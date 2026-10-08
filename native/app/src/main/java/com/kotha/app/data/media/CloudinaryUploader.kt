package com.kotha.app.data.media

import com.kotha.app.core.AppConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UploadResult(val url: String, val resourceType: String)

class UploadHttpException(val code: Int) : IOException("upload failed with status $code")

@Singleton
class CloudinaryUploader @Inject constructor() {

    suspend fun upload(
        file: File,
        fileName: String,
        mime: String,
        onProgress: (Float) -> Unit
    ): UploadResult = withContext(Dispatchers.IO) {
        val boundary = "----cova" + UUID.randomUUID().toString().replace("-", "")
        val head = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n")
            append(AppConfig.UPLOAD_PRESET).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"file\"; filename=\"")
            append(fileName.replace("\"", "_").replace("\r", "").replace("\n", ""))
            append("\"\r\n")
            append("Content-Type: ").append(mime.ifEmpty { "application/octet-stream" }).append("\r\n\r\n")
        }.toByteArray()
        val tail = "\r\n--$boundary--\r\n".toByteArray()
        val length = file.length()
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                connection.disconnect()
            }
        }
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = AppConfig.UPLOAD_TIMEOUT_MS
            connection.readTimeout = AppConfig.UPLOAD_TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("Accept", "application/json")
            connection.setFixedLengthStreamingMode(head.size + length + tail.size)
            connection.outputStream.use { output ->
                output.write(head)
                file.inputStream().use { input ->
                    val buffer = ByteArray(BUFFER)
                    var sent = 0L
                    var reported = 0f
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        sent += read
                        val fraction = if (length > 0) sent.toFloat() / length else 1f
                        if (fraction - reported >= STEP) {
                            reported = fraction
                            onProgress(fraction.coerceIn(0f, 1f))
                        }
                    }
                }
                output.write(tail)
                output.flush()
            }
            val code = connection.responseCode
            if (code !in 200..299) throw UploadHttpException(code)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val url = json.optString("secure_url")
            if (url.isEmpty()) throw IOException("missing url")
            onProgress(1f)
            UploadResult(url, json.optString("resource_type"))
        } catch (e: IOException) {
            ensureActive()
            throw e
        } finally {
            watcher.cancel()
            connection.disconnect()
        }
    }

    private companion object {
        val ENDPOINT = "https://api.cloudinary.com/v1_1/${AppConfig.CLOUD_NAME}/auto/upload"
        const val BUFFER = 16 * 1024
        const val STEP = 0.01f
    }
}
