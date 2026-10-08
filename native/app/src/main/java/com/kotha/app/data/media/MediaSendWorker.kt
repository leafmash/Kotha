package com.kotha.app.data.media

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaWorkerEntryPoint {
    fun processor(): MediaSendProcessor
}

class MediaSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_JOB) ?: return Result.failure()
        val processor = EntryPointAccessors
            .fromApplication(applicationContext, MediaWorkerEntryPoint::class.java)
            .processor()
        return when (processor.process(id)) {
            MediaOutcome.Done -> Result.success()
            MediaOutcome.Retry -> Result.retry()
            MediaOutcome.Failed -> Result.failure()
        }
    }

    companion object {
        const val KEY_JOB = "job"
    }
}
