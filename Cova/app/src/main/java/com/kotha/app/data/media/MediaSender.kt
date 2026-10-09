package com.kotha.app.data.media

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kotha.app.data.auth.AuthRepository
import com.kotha.app.data.chat.ChatRepository
import com.kotha.app.data.chat.ReplyRef
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class MediaSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stager: MediaStager,
    private val store: MediaJobStore,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository
) {

    suspend fun enqueue(chatId: String, members: List<String>, picks: List<PickedMedia>, reply: ReplyRef?): Int {
        val uid = authRepository.user?.uid ?: return picks.size
        var failed = 0
        for ((index, pick) in picks.withIndex()) {
            val id = chatRepository.newMessageId(chatId)
            try {
                val staged = stager.stage(pick, id)
                store.put(jobOf(id, chatId, uid, members, staged, if (index == 0) reply else null))
                schedule(id, false)
            } catch (e: CancellationException) {
                cleanup(id)
                throw e
            } catch (e: Exception) {
                failed++
                cleanup(id)
            }
        }
        return failed
    }

    suspend fun enqueueVoice(chatId: String, members: List<String>, voice: RecordedVoice, reply: ReplyRef?): Boolean {
        val uid = authRepository.user?.uid ?: return false
        val id = chatRepository.newMessageId(chatId)
        return try {
            val staged = stager.stageVoice(voice.file, voice.durationMs, voice.wave, id)
            store.put(jobOf(id, chatId, uid, members, staged, reply))
            schedule(id, false)
            true
        } catch (e: CancellationException) {
            cleanup(id)
            throw e
        } catch (e: Exception) {
            cleanup(id)
            false
        }
    }

    fun retry(id: String) {
        val job = store.update(id) { it.copy(state = JobState.QUEUED, attempts = 0) } ?: return
        store.setProgress(job.id, 0f)
        schedule(id, true)
    }

    fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        store.remove(id)
    }

    private fun jobOf(
        id: String,
        chatId: String,
        uid: String,
        members: List<String>,
        staged: StagedMedia,
        reply: ReplyRef?
    ): MediaJob = MediaJob(
        id = id,
        chatId = chatId,
        uid = uid,
        members = members,
        kind = staged.kind,
        path = staged.file.absolutePath,
        thumbPath = staged.thumb?.absolutePath.orEmpty(),
        name = staged.name,
        size = staged.size,
        mime = staged.mime,
        durationMs = staged.durationMs,
        wave = staged.wave,
        width = staged.width,
        height = staged.height,
        replyId = reply?.id.orEmpty(),
        replyText = reply?.text.orEmpty(),
        createdAtMs = System.currentTimeMillis(),
        state = JobState.QUEUED,
        attempts = 0,
        url = "",
        thumbUrl = ""
    )

    private fun schedule(id: String, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<MediaSendWorker>()
            .setInputData(workDataOf(MediaSendWorker.KEY_JOB to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(id),
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun cleanup(id: String) {
        File(context.filesDir, "outbox/$id").deleteRecursively()
    }

    private fun workName(id: String): String = "media-$id"
}
