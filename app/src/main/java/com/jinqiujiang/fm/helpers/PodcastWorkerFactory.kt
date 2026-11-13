package com.jinqiujiang.fm.helpers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/** WorkManager persists class names, including requests created before v1.0.0. */
class PodcastWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        "org.y20k.escapepod.helpers.DownloadWorker", DownloadWorker::class.java.name ->
            DownloadWorker(appContext, workerParameters)
        else -> null
    }
}
