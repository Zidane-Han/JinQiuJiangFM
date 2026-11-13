/*
 * WorkerHelper.kt
 * Implements the WorkerHelper object
 * A WorkerHelper provides helper methods for starting work jobs
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jinqiujiang.fm.helpers

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jinqiujiang.fm.Keys
import java.util.UUID
import java.util.concurrent.TimeUnit


/*
 * WorkerHelper object
 */
object WorkerHelper {

    /* Define log tag */
    private val TAG: String = WorkerHelper::class.java.simpleName


    /* Schedules a DownloadWorker that triggers background updates of the collection periodically */
    fun schedulePeriodicUpdateWorker(context: Context): UUID {
        Log.v(TAG, "Schedule periodic work: update collection")
        val requestData: Data = Data.Builder()
                .putInt(Keys.KEY_DOWNLOAD_WORK_REQUEST, Keys.REQUEST_UPDATE_COLLECTION)
                .build()
        val updateCollectionPeriodicWork = PeriodicWorkRequestBuilder<DownloadWorker>(Keys.UPDATE_REPEAT_INTERVAL, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .setInputData(requestData)
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(Keys.NAME_PERIODIC_COLLECTION_UPDATE_WORK,  ExistingPeriodicWorkPolicy.KEEP, updateCollectionPeriodicWork)
        return updateCollectionPeriodicWork.id
    }

}