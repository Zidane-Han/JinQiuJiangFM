/*
 * AudioHelper.kt
 * Implements the AudioHelper object
 * A AudioHelper provides helper methods for handling audio files
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
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log


/*
 * AudioHelper object
 */
object AudioHelper {

    /* Define log tag */
    private val TAG: String = AudioHelper::class.java.simpleName


    /* Extract duration metadata from audio file - used with file uris */
    fun getDuration(context: Context, audioFileUri: Uri): Long {
        val metadataRetriever: MediaMetadataRetriever = MediaMetadataRetriever()
        var duration: Long = 0L
        try {
            metadataRetriever.setDataSource(context, audioFileUri)
            val durationString = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: String()
            duration = durationString.toLong()
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to extract duration metadata from audio file: $exception")
        }
        return duration
    }


    /* Extract duration metadata from audio file - used for http file paths */
    fun getDuration(audioFilePath: String): Long {
        val metadataRetriever: MediaMetadataRetriever = MediaMetadataRetriever()
        var duration: Long = 0L
        try {
            metadataRetriever.setDataSource(audioFilePath)
            val durationString = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: String()
            duration = durationString.toLong()
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to extract duration metadata from audio file: $exception")
        }
        return duration
    }


}