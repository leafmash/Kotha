package com.kotha.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.kotha.app.BuildConfig
import com.kotha.app.core.AppConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class MediaStager @Inject constructor(@ApplicationContext private val context: Context) {

    fun describe(uri: Uri): PickedMedia {
        var name = ""
        var size = 0L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex).orEmpty()
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        if (name.isEmpty()) name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        if (name.isEmpty()) name = "file"
        val mime = context.contentResolver.getType(uri) ?: mimeFromName(name)
        return PickedMedia(uri, name, size, mime, kindOf(mime))
    }

    suspend fun stage(pick: PickedMedia, jobId: String): StagedMedia = withContext(Dispatchers.IO) {
        val dir = jobDir(jobId)
        when (pick.kind) {
            "image" -> stageImage(pick, dir)
            "video" -> stageVideo(pick, dir)
            "audio" -> stageAudio(pick, dir)
            else -> stageFile(pick, dir)
        }
    }

    suspend fun stageVoice(file: File, durationMs: Long, wave: List<Int>, jobId: String): StagedMedia =
        withContext(Dispatchers.IO) {
            val dir = jobDir(jobId)
            val target = File(dir, "voice.m4a")
            file.copyTo(target, overwrite = true)
            file.delete()
            StagedMedia(
                file = target,
                name = "voice.m4a",
                size = target.length(),
                mime = "audio/mp4",
                kind = "audio",
                durationMs = durationMs,
                width = 0,
                height = 0,
                thumb = null,
                wave = wave
            )
        }

    fun previewFrame(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun newCameraUri(video: Boolean): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val file = File(dir, if (video) "VID_$stamp.mp4" else "IMG_$stamp.jpg")
        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".files", file)
    }

    private fun jobDir(jobId: String): File = File(context.filesDir, "outbox/$jobId").apply { mkdirs() }

    private fun kindOf(mime: String): String = when {
        mime.startsWith("image/") -> "image"
        mime.startsWith("video/") -> "video"
        mime.startsWith("audio/") -> "audio"
        else -> "file"
    }

    private fun mimeFromName(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { "file" }

    private fun copyTo(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open $uri")
        input.use { source -> target.outputStream().use { sink -> source.copyTo(sink) } }
    }

    private fun stageImage(pick: PickedMedia, dir: File): StagedMedia {
        val compressible = !pick.mime.contains("gif") && !pick.mime.contains("svg") &&
            (pick.size == 0L || pick.size >= AppConfig.IMAGE_SKIP_BYTES)
        if (compressible) {
            val compressed = compressImage(pick, dir)
            if (compressed != null) return compressed
        }
        val target = File(dir, safeName(pick.name))
        copyTo(pick.uri, target)
        val (width, height) = boundsOf(target)
        return StagedMedia(target, pick.name, target.length(), pick.mime, "image", 0L, width, height, null)
    }

    suspend fun avatarFile(uri: Uri): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "avatars").apply { mkdirs() }
        val target = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
        if (renderJpeg(uri, target, AppConfig.AVATAR_MAX_SIDE, AppConfig.AVATAR_QUALITY) != null) target else null
    }

    private fun compressImage(pick: PickedMedia, dir: File): StagedMedia? {
        val base = pick.name.substringBeforeLast('.', pick.name).ifEmpty { "photo" }
        val outName = "$base.jpg"
        val target = File(dir, safeName(outName))
        val size = renderJpeg(pick.uri, target, AppConfig.IMAGE_MAX_SIDE, AppConfig.IMAGE_QUALITY) ?: return null
        if (pick.size > 0 && target.length() >= pick.size) {
            target.delete()
            return null
        }
        return StagedMedia(target, outName, target.length(), "image/jpeg", "image", 0L, size.first, size.second, null)
    }

    private fun renderJpeg(uri: Uri, target: File, maxSide: Int, quality: Int): Pair<Int, Int>? {
        val resolver = context.contentResolver
        var decoded: Bitmap? = null
        var transformed: Bitmap? = null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            var sample = 1
            val longest = max(bounds.outWidth, bounds.outHeight)
            while (longest / (sample * 2) >= maxSide) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val source = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null
            decoded = source
            val matrix = Matrix()
            applyOrientation(matrix, orientation)
            val scale = min(1f, maxSide.toFloat() / max(source.width, source.height))
            if (scale < 1f) matrix.postScale(scale, scale)
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            val result = flatten(rotated)
            transformed = result
            FileOutputStream(target).use { result.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            result.width to result.height
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } finally {
            if (transformed != null && transformed !== decoded) runCatching { transformed?.recycle() }
            runCatching { decoded?.recycle() }
        }
    }

    private fun flatten(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, null)
        if (!source.isRecycled) source.recycle()
        return out
    }

    private fun applyOrientation(matrix: Matrix, orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
    }

    private fun boundsOf(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val swap = orientation in ExifInterface.ORIENTATION_TRANSPOSE..ExifInterface.ORIENTATION_ROTATE_270
        val width = max(options.outWidth, 0)
        val height = max(options.outHeight, 0)
        return if (swap) height to width else width to height
    }

    private fun stageVideo(pick: PickedMedia, dir: File): StagedMedia {
        val target = File(dir, safeName(pick.name))
        copyTo(pick.uri, target)
        var duration = 0L
        var width = 0
        var height = 0
        var thumb: File? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(target.absolutePath)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val at = if (duration > 2_000L) 1_000_000L else 0L
            val frame = retriever.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame != null) {
                width = frame.width
                height = frame.height
                thumb = writeThumb(frame, File(dir, "thumb.jpg"))
                frame.recycle()
            }
        } catch (e: Exception) {
            thumb = null
        } finally {
            runCatching { retriever.release() }
        }
        return StagedMedia(target, pick.name, target.length(), pick.mime, "video", duration, width, height, thumb)
    }

    private fun stageAudio(pick: PickedMedia, dir: File): StagedMedia {
        val target = File(dir, safeName(pick.name))
        copyTo(pick.uri, target)
        var duration = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(target.absolutePath)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            duration = 0L
        } finally {
            runCatching { retriever.release() }
        }
        return StagedMedia(target, pick.name, target.length(), pick.mime, "audio", duration, 0, 0, null)
    }

    private fun stageFile(pick: PickedMedia, dir: File): StagedMedia {
        val target = File(dir, safeName(pick.name))
        copyTo(pick.uri, target)
        return StagedMedia(target, pick.name, target.length(), pick.mime, "file", 0L, 0, 0, null)
    }

    private fun writeThumb(frame: Bitmap, target: File): File? = try {
        val scale = min(1f, AppConfig.THUMB_MAX_SIDE.toFloat() / max(frame.width, frame.height))
        val width = max(1, (frame.width * scale).toInt())
        val height = max(1, (frame.height * scale).toInt())
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(frame, width, height, true) else frame
        FileOutputStream(target).use { scaled.compress(Bitmap.CompressFormat.JPEG, AppConfig.THUMB_QUALITY, it) }
        if (scaled !== frame) scaled.recycle()
        target
    } catch (e: Exception) {
        null
    }
}
