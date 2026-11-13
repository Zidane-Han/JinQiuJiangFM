/*
 * PodcastDescriptionDao.kt
 * Implements the PodcastDescriptionDao interface
 * A PodcastDescriptionDao interface provides methods for accessing podcast descriptions within the collection database
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jinqiujiang.fm.database.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jinqiujiang.fm.database.objects.PodcastDescription


/*
 * PodcastDescriptionDao interface
 */
@Dao
interface PodcastDescriptionDao {

    @Query("SELECT * FROM podcast_descriptions WHERE remote_podcast_feed_location IS :remotePodcastFeedLocation LIMIT 1")
    fun findByRemotePodcastFeedLocation(remotePodcastFeedLocation: String): PodcastDescription?

    @Query("SELECT * FROM podcast_descriptions WHERE remote_podcast_feed_location IS :remotePodcastFeedLocation LIMIT 1")
    fun findByRemotePodcastFeedLocationLiveData(remotePodcastFeedLocation: String): LiveData<PodcastDescription?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(podcastDescription: PodcastDescription): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(podcastDescriptions: List<PodcastDescription>): List<Long>

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(podcastDescription: PodcastDescription)

    @Delete
    fun delete(podcastDescription: PodcastDescription)

    @Query("DELETE from podcast_descriptions where remote_podcast_feed_location IS :remotePodcastFeedLocation")
    fun delete(remotePodcastFeedLocation: String)

    @Transaction
    fun upsert(podcastDescription: PodcastDescription): Boolean {
        val rowId = insert(podcastDescription)
        if (rowId == -1L) {
            // false = podcast was NOT NEW (= update)
            update(podcastDescription)
            return false
        }
        // true = podcast was NEW (= insert)
        return true
    }

    @Transaction
    fun upsertAll(podcastDescriptions: List<PodcastDescription>) {
        val rowIds = insertAll(podcastDescriptions)
        val podcastDescriptionsToUpdate: List<PodcastDescription> = rowIds.mapIndexedNotNull { index, rowId ->
            if (rowId == -1L) {
                // result -1 means that insert operation was not successful
                podcastDescriptions[index]
            } else {
                null
            }
        }
        podcastDescriptionsToUpdate.forEach { update(it) }
    }

}