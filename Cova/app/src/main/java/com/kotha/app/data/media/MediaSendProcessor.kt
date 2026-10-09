package com.kotha.app.data.media

import com.google.firebase.firestore.FirebaseFirestoreException
import com.kotha.app.core.AppConfig
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.chat.ChatRepository
import com.kotha.app.data.chat.ReplyRef
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException

@Singleton
class MediaSendProcessor @Inject constructor(
    private val store: MediaJobStore,
    private val uploader: CloudinaryUploader,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository
) {

    suspend fun process(id: String): MediaOutcome {
        val initial = store.get(id) ?: return MediaOutcome.Done
        val user = authRepository.user ?: return MediaOutcome.Retry
        if (user.uid != initial.uid) {
            store.remove(id)
            return MediaOutcome.Done
        }
        store.update(id) { it.copy(state = JobState.UPLOADING) }
        return try {
            var job = initial
            if (job.url.isEmpty()) {
                val file = File(job.path)
                if (!file.exists()) return markFailed(id)
                store.setProgress(id, 0f)
                val result = uploader.upload(file, job.name, job.mime) { store.setProgress(id, it) }
                job = store.update(id) { it.copy(url = result.url) } ?: return MediaOutcome.Done
            }
            if (job.thumbUrl.isEmpty() && job.thumbPath.isNotEmpty()) {
                job = uploadThumb(job) ?: return MediaOutcome.Done
            }
            chatRepository.send(job.chatId, job.members, payloadOf(job), replyOf(job), job.id)
            store.remove(id)
            MediaOutcome.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: UploadHttpException) {
            if (permanent(e.code)) markFailed(id) else retryOrFail(id)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) markFailed(id) else retryOrFail(id)
        } catch (e: Exception) {
            retryOrFail(id)
        }
    }

    private suspend fun uploadThumb(job: MediaJob): MediaJob? {
        val file = File(job.thumbPath)
        if (!file.exists()) return job
        val thumb = try {
            uploader.upload(file, "thumb.jpg", "image/jpeg") { }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (thumb == null) return job
        return store.update(job.id) { it.copy(thumbUrl = thumb.url) }
    }

    private fun payloadOf(job: MediaJob): Map<String, Any> {
        val payload = mutableMapOf<String, Any>("type" to job.kind, "url" to job.url)
        val seconds = (job.durationMs / 100.0).roundToLong() / 10.0
        val voice = job.kind == "audio" && job.wave.isNotEmpty()
        if (!voice) {
            payload["name"] = job.name
            payload["size"] = job.size
        }
        if ((job.kind == "image" || job.kind == "video") && job.width > 0 && job.height > 0) {
            payload["w"] = job.width.toLong()
            payload["h"] = job.height.toLong()
        }
        if ((job.kind == "video" || job.kind == "audio") && job.durationMs > 0) payload["duration"] = seconds
        if (job.kind == "video" && job.thumbUrl.isNotEmpty()) payload["thumb"] = job.thumbUrl
        if (voice) payload["wave"] = job.wave.map { it.toLong() }
        return payload
    }

    private fun replyOf(job: MediaJob): ReplyRef? =
        if (job.replyId.isNotEmpty()) ReplyRef(job.replyId, job.replyText) else null

    private fun permanent(code: Int): Boolean = code in 400..499 && code != 408 && code != 429

    private fun markFailed(id: String): MediaOutcome {
        store.update(id) { it.copy(state = JobState.FAILED) }
        return MediaOutcome.Failed
    }

    private fun retryOrFail(id: String): MediaOutcome {
        val job = store.get(id) ?: return MediaOutcome.Done
        val attempts = job.attempts + 1
        return if (attempts >= AppConfig.UPLOAD_MAX_ATTEMPTS) {
            store.update(id) { it.copy(state = JobState.FAILED, attempts = attempts) }
            MediaOutcome.Failed
        } else {
            store.update(id) { it.copy(state = JobState.QUEUED, attempts = attempts) }
            MediaOutcome.Retry
        }
    }
}
