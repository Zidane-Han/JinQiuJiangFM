/*
 * PodcastDao.kt
 * Implements the PodcastDao interface
 * A PodcastDao interface provides methods for accessing podcasts within the collection database
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.database.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.database.wrappers.PodcastWithAllEpisodesWrapper
import org.y20k.escapepod.database.wrappers.PodcastWithDescriptionAndAllEpisodesWrapper
import org.y20k.escapepod.database.wrappers.PodcastWithLatestEpisodeWrapper


/*
 * PodcastDao interface
 */
@Dao
interface PodcastDao {
    @Query("SELECT COUNT(*) FROM podcasts")
    fun getSizeLiveData(): LiveData<Int>

    @Query("SELECT * FROM podcasts")
    fun getAll(): List<Podcast>

    @Query("SELECT * FROM podcasts WHERE remote_image_file_location IS :remoteImageFileLocation LIMIT 1")
    fun findByRemoteImageFileLocation(remoteImageFileLocation: String): Podcast?

    @Query("SELECT * FROM podcasts WHERE remote_podcast_feed_location = :remotePodcastFeedLocation LIMIT 1")
    fun findByRemotePodcastFeedLocation(remotePodcastFeedLocation: String): Podcast?

    @Query("SELECT * FROM podcasts WHERE remote_podcast_feed_location = :remotePodcastFeedLocation LIMIT 1")
    fun findByRemotePodcastFeedLocationLiveData(remotePodcastFeedLocation: String): LiveData<Podcast?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(podcast: Podcast): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(podcasts: List<Podcast>): List<Long>

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(podcast: Podcast)

    @Delete
    fun delete(podcast: Podcast)

    @Transaction
    fun upsert(podcast: Podcast): Boolean {
        val rowId = insert(podcast)
        if (rowId == -1L) {
            // false = podcast was NOT NEW (= update)
            update(podcast)
            return false
        }
        // true = podcast was NEW (= insert)
        return true
    }

    @Transaction
    fun upsertAll(podcasts: List<Podcast>) {
        val rowIds = insertAll(podcasts)
        val podcastsToUpdate: List<Podcast> = rowIds.mapIndexedNotNull { index, rowId ->
            if (rowId == -1L) {
                // result -1 means that insert operation was not successful
                podcasts[index]
            } else {
                null
            }
        }
        podcastsToUpdate.forEach { update(it) }
    }


    /**
     * This query will tell Room to query both the podcast and episode tables and handle
     * the object mapping.
     */
    @Transaction
    @Query("SELECT * FROM podcasts")
    fun getAllPodcastsWithAllEpisodesLiveData(): LiveData<List<PodcastWithAllEpisodesWrapper>>

    /**
     * This query will tell Room to query both the podcast and episode tables and handle
     * the object mapping.
     */
    @Transaction
    @Query("SELECT * FROM podcasts")
    fun getAllPodcastsWithLatestEpisodeLiveData(): LiveData<List<PodcastWithLatestEpisodeWrapper>>

    @Transaction
    @Query("SELECT * FROM podcasts WHERE remote_podcast_feed_location = :remotePodcastFeedLocation LIMIT 1")
    fun getPodcastsWithDescriptionAndAllEpisodesLiveData(remotePodcastFeedLocation: String): LiveData<PodcastWithDescriptionAndAllEpisodesWrapper>

//    @Transaction
//    @Query("SELECT * FROM podcasts ORDER BY latest_episode_date")
//    fun getAllPodcastsWithMostRecentEpisodeLiveData(): LiveData<List<PodcastWithRecentEpisodesWrapper>>

    //@Transaction
    //@Query("SELECT * FROM episodes ORDER BY publication_date DESC LIMIT 10")
    //fun getCollectionLiveData2(): LiveData<List<PodcastWrapper>>

    /**
     * This query will tell Room to query both the podcast and episode tables and handle
     * the object mapping.
     */
    @Transaction
    @Query("SELECT * FROM podcasts WHERE remote_podcast_feed_location = :remotePodcastFeedLocation LIMIT 1")
    fun getWithRemotePodcastFeedLocation(remotePodcastFeedLocation: String): PodcastWithAllEpisodesWrapper?


    /* Updates podcast cover and small cover */
    @Query("UPDATE podcasts SET cover = :cover , small_cover = :smallCover, cover_version = :coverVersion WHERE remote_image_file_location = :remoteImageFileLocation")
    fun updateCover(remoteImageFileLocation: String, cover: String, smallCover: String, coverVersion: Long): Int

    /* Resets episode cover and small cover where the default cover has been set (one-time operation) */
    @Query("UPDATE podcasts SET cover = '' , small_cover = '' WHERE cover IS :defaultCoverLocation")
    fun resetDefaultCover(defaultCoverLocation: String): Int

    /* Updates filter state of the episode list: all episodes / downloaded only */
    @Query("UPDATE podcasts SET episode_list_display_filter = :filter WHERE remote_podcast_feed_location = :remotePodcastFeedLocation")
    fun updateEpisodeListFilter(remotePodcastFeedLocation: String, filter: Int): Int

    /* Updates state the episode search in the episode list (stores the search term) */
    @Query("UPDATE podcasts SET episode_list_search_term = :searchTerm WHERE remote_podcast_feed_location = :remotePodcastFeedLocation")
    fun updateEpisodeListSearchTerm(remotePodcastFeedLocation: String, searchTerm: String): Int

}