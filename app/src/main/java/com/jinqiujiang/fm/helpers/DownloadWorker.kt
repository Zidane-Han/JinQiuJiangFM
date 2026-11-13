package com.jinqiujiang.fm.helpers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

class DownloadWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!PreferencesHelper.loadBackgroundRefresh() || !CollectionHelper.hasEnoughTimePassedSinceLastUpdate()) return Result.success()
        return try {
            PodcastSync.refresh(applicationContext)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
