/*
 * EpisodeDao.kt
 * Implements the EpisodeDao interface
 * An EpisodeDao interface provides methods for accessing episodes within the collection database
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
import org.y20k.escapepod.database.objects.Episode
import java.util.Date
import java.util.GregorianCalendar


/*
 * EpisodeDao interface
 */
@Dao
interface EpisodeDao {
    @Query("SELECT COUNT(*) FROM episodes")
    fun getSize(): Int

    @Query("SELECT COUNT(*) FROM episodes")
    fun getSizeLiveData(): LiveData<Int>

    @Query("SELECT * FROM episodes")
    fun getAll(): LiveData<List<Episode>>

    @Query("SELECT * FROM episodes ORDER BY publication_date DESC LIMIT :limit")
    fun getChronological(limit: Int): List<Episode>

    @Query("SELECT * FROM episodes WHERE episode_remote_podcast_feed_location IS :episodeRemotePodcastFeedLocation ORDER BY publication_date DESC")
    fun getAllWithRemotePodcastFeedLocation(episodeRemotePodcastFeedLocation: String): List<Episode>

    @Query("SELECT * FROM episodes WHERE episode_remote_podcast_feed_location IS :episodeRemotePodcastFeedLocation ORDER BY publication_date DESC")
    fun getAllWithRemotePodcastFeedLocationLiveData(episodeRemotePodcastFeedLocation: String): LiveData<List<Episode>>

    @Query("SELECT * FROM episodes WHERE episode_remote_podcast_feed_location IS :episodeRemotePodcastFeedLocation ORDER BY publication_date DESC LIMIT 1")
    fun getLatest(episodeRemotePodcastFeedLocation: String): Episode

    @Query("SELECT title FROM episodes WHERE media_id IS :mediaId LIMIT 1")
    fun getTitle(mediaId: String): String?

    @Query("SELECT playback_position FROM episodes WHERE media_id IS :mediaId LIMIT 1")
    fun getPlaybackPosition(mediaId: String): Long

    @Query("SELECT :mediaId, CASE WHEN playback_position >= duration - 550 THEN 1 ELSE 0 END AS result FROM episodes LIMIT 1")
    fun getIsFinished(mediaId: String): Int

    @Query("SELECT * FROM episodes WHERE media_id IS :mediaId LIMIT 1")
    fun findByMediaId(mediaId: String): Episode?

//    @Query("SELECT * FROM episodes WHERE media_id IS :mediaId LIMIT 1")
//    suspend fun findByMediaIdSuspended(mediaId: String): Episode?

    @Query("SELECT * FROM episodes WHERE episode_remote_podcast_feed_location IS :episodeRemotePodcastFeedLocation ORDER BY publication_date DESC")
    fun findByEpisodeRemotePodcastFeedLocation(episodeRemotePodcastFeedLocation: String): List<Episode>

    @Query("SELECT * FROM episodes WHERE episode_remote_podcast_feed_location IS :episodeRemotePodcastFeedLocation ORDER BY publication_date DESC")
    fun findByEpisodeRemotePodcastFeedLocationLiveData(episodeRemotePodcastFeedLocation: String): LiveData<List<Episode>>

    @Query("SELECT * FROM episodes WHERE remote_audio_file_location IS :remoteAudioFileLocation LIMIT 1")
    fun findByRemoteAudioFileLocation(remoteAudioFileLocation: String): Episode?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(episode: Episode): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(episodes: List<Episode>): List<Long>

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(episode: Episode)

    @Delete
    fun delete(episode: Episode)

    @Query("DELETE from episodes where episode_remote_podcast_feed_location IS :remotePodcastFeedLocation")
    fun deleteAll(remotePodcastFeedLocation: String): Int

    @Transaction
    fun upsert(episode: Episode): Boolean {
        val rowId = insert(episode)
        if (rowId == -1L) {
            update(episode)
            // false = episode was NOT NEW (= update)
            return false
        }
        // true = episode was NEW (= insert)
        return true
    }

    @Transaction
    fun upsertAll(episodes: List<Episode>) {
        val rowIds = insertAll(episodes)
        val episodesToUpdate = rowIds.mapIndexedNotNull { index, rowId ->
            if (rowId == -1L) {
                // result -1 means that insert operation was not successful
                episodes[index]
            } else {
                null
            }
        }
        episodesToUpdate.forEach { update(it) }
    }

    /* Updates epsiode cover and small cover */
    @Query("UPDATE episodes SET cover = :cover , small_cover = :smallCover, cover_version = :coverVersion WHERE episode_remote_podcast_feed_location IS :episodeRemotePodcastFeedLocation")
    fun updateCover(episodeRemotePodcastFeedLocation: String, cover: String, smallCover: String, coverVersion: Long): Int


    /* Updates episode audio and duration */
    @Query("UPDATE episodes SET audio = :audio , duration = :duration , content_type = :contentType , last_listened = :lastListened WHERE remote_audio_file_location IS :remoteAudioFileLocation")
    fun updateAudioRemoteAudioFileLocation(remoteAudioFileLocation: String, audio: String, duration: Long, contentType: String, lastListened: Long = GregorianCalendar.getInstance().timeInMillis): Int


    /* Updates episode playback position */
    @Query("UPDATE episodes SET playback_position = :playbackPosition, is_playing =:isPlaying, last_listened=:lastListened WHERE media_id IS :mediaId")
    fun updatePlaybackPosition(mediaId: String, playbackPosition: Long, isPlaying: Boolean, lastListened: Date = GregorianCalendar.getInstance().time): Int


    /* Updates episode audio and duration */
    @Query("UPDATE episodes SET audio = :audio , duration = :duration , content_type = :contentType , last_listened = :lastListened WHERE remote_audio_file_name IS :localFileName")
    fun updateAudioByFileName(localFileName: String, audio: String, duration: Long, contentType: String, lastListened: Long = GregorianCalendar.getInstance().timeInMillis): Int


    /* Set episode playback position to it's duration - marking it as played */
    @Query("UPDATE episodes SET playback_position = duration, is_playing = 0 WHERE media_id IS :mediaId")
    fun markPlayed(mediaId: String): Int


    /* Set an episode's playback state - 0 => isPlaying = false/ 1 => isPlaying = true */
    @Query("UPDATE episodes SET is_playing = :stateBoolean WHERE media_id IS :mediaId")
    fun setPlaybackState(mediaId: String?, stateBoolean: Int): Int


    /* Resets local audio reference - used when user taps on trashcan */
    @Query("UPDATE episodes SET audio = '', playback_position = 0, duration = 0, is_playing = 0, manually_deleted = :manuallyDeleted WHERE media_id IS :mediaId")
    fun resetLocalAudioReference(mediaId: String, manuallyDeleted: Boolean): Int


    /* Resets local audio references of all episodes */
    @Query("UPDATE episodes SET audio = '', playback_position = 0, duration = 0, is_playing = 0, manually_deleted = 1")
    fun resetLocalAudioReferencesForAllEpisodes()


    /* set playback state for all episodes - except of the one indicated by "exclude" */
    @Query("UPDATE episodes SET is_playing = :isPlaying WHERE media_id IS NOT :exclude")
    fun setPlaybackStateForAllEpisodes(isPlaying: Boolean, exclude: String): Int


    /* Resets episode cover and small cover where the default cover has been set (one-time operation) */
    @Query("UPDATE episodes SET cover = '' , small_cover = '' WHERE cover IS :defaultCoverLocation")
    fun resetDefaultCover(defaultCoverLocation: String): Int

}