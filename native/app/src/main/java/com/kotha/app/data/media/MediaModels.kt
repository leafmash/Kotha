package com.kotha.app.data.media

import android.net.Uri
import java.io.File

data class PickedMedia(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String,
    val kind: String
)

data class StagedMedia(
    val file: File,
    val name: String,
    val size: Long,
    val mime: String,
    val kind: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val thumb: File?,
    val wave: List<Int> = emptyList()
)

enum class JobState { QUEUED, UPLOADING, FAILED }

data class MediaJob(
    val id: String,
    val chatId: String,
    val uid: String,
    val members: List<String>,
    val kind: String,
    val path: String,
    val thumbPath: String,
    val name: String,
    val size: Long,
    val mime: String,
    val durationMs: Long,
    val wave: List<Int>,
    val width: Int,
    val height: Int,
    val replyId: String,
    val replyText: String,
    val createdAtMs: Long,
    val state: JobState,
    val attempts: Int,
    val url: String,
    val thumbUrl: String
)

enum class MediaOutcome { Done, Retry, Failed }
