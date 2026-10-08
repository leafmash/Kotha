package com.kotha.app.data.media

import android.content.Context
import android.util.AtomicFile
import com.kotha.app.core.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class MediaJobStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val file = AtomicFile(File(context.filesDir, "media_jobs.json"))
    private val mutex = Mutex()
    private val mutableJobs = MutableStateFlow(load())
    private val mutableProgress = MutableStateFlow<Map<String, Float>>(emptyMap())

    val jobs: StateFlow<List<MediaJob>> = mutableJobs.asStateFlow()
    val progress: StateFlow<Map<String, Float>> = mutableProgress.asStateFlow()

    fun get(id: String): MediaJob? = mutableJobs.value.firstOrNull { it.id == id }

    fun put(job: MediaJob) {
        mutableJobs.update { list -> list.filterNot { it.id == job.id } + job }
        persist()
    }

    fun update(id: String, transform: (MediaJob) -> MediaJob): MediaJob? {
        var result: MediaJob? = null
        mutableJobs.update { list ->
            list.map { job ->
                if (job.id == id) {
                    val next = transform(job)
                    result = next
                    next
                } else {
                    job
                }
            }
        }
        persist()
        return result
    }

    fun setProgress(id: String, value: Float) {
        mutableProgress.update { it + (id to value) }
    }

    fun remove(id: String) {
        val job = get(id)
        mutableJobs.update { list -> list.filterNot { it.id == id } }
        mutableProgress.update { it - id }
        persist()
        if (job != null) {
            scope.launch(Dispatchers.IO) { File(job.path).parentFile?.deleteRecursively() }
        }
    }

    private fun persist() {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val snapshot = mutableJobs.value
                val stream = try {
                    file.startWrite()
                } catch (e: IOException) {
                    return@withLock
                }
                try {
                    stream.write(encode(snapshot).toByteArray())
                    file.finishWrite(stream)
                } catch (e: IOException) {
                    file.failWrite(stream)
                }
            }
        }
    }

    private fun load(): List<MediaJob> {
        if (!file.baseFile.exists()) return emptyList()
        return try {
            val array = JSONArray(String(file.readFully()))
            (0 until array.length())
                .mapNotNull { decode(array.getJSONObject(it)) }
                .map { if (it.state == JobState.UPLOADING) it.copy(state = JobState.QUEUED) else it }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun encode(list: List<MediaJob>): String {
        val array = JSONArray()
        list.forEach { job ->
            array.put(
                JSONObject()
                    .put("id", job.id)
                    .put("chatId", job.chatId)
                    .put("uid", job.uid)
                    .put("members", JSONArray(job.members))
                    .put("kind", job.kind)
                    .put("path", job.path)
                    .put("thumbPath", job.thumbPath)
                    .put("name", job.name)
                    .put("size", job.size)
                    .put("mime", job.mime)
                    .put("durationMs", job.durationMs)
                    .put("wave", JSONArray(job.wave))
                    .put("width", job.width)
                    .put("height", job.height)
                    .put("replyId", job.replyId)
                    .put("replyText", job.replyText)
                    .put("createdAtMs", job.createdAtMs)
                    .put("state", job.state.name)
                    .put("attempts", job.attempts)
                    .put("url", job.url)
                    .put("thumbUrl", job.thumbUrl)
            )
        }
        return array.toString()
    }

    private fun decode(o: JSONObject): MediaJob? = try {
        val members = o.getJSONArray("members")
        val wave = o.optJSONArray("wave")
        MediaJob(
            id = o.getString("id"),
            chatId = o.getString("chatId"),
            uid = o.getString("uid"),
            members = (0 until members.length()).map { members.getString(it) },
            kind = o.getString("kind"),
            path = o.getString("path"),
            thumbPath = o.optString("thumbPath"),
            name = o.optString("name"),
            size = o.optLong("size"),
            mime = o.optString("mime"),
            durationMs = o.optLong("durationMs"),
            wave = if (wave == null) emptyList() else (0 until wave.length()).map { wave.getInt(it) },
            width = o.optInt("width"),
            height = o.optInt("height"),
            replyId = o.optString("replyId"),
            replyText = o.optString("replyText"),
            createdAtMs = o.optLong("createdAtMs"),
            state = JobState.valueOf(o.getString("state")),
            attempts = o.optInt("attempts"),
            url = o.optString("url"),
            thumbUrl = o.optString("thumbUrl")
        )
    } catch (e: Exception) {
        null
    }
}
