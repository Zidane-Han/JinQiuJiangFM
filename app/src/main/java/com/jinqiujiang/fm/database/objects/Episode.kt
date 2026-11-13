/*
 * Episode.kt
 * Implements the Episode class
 * A Episode object holds the base data of an episode
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jinqiujiang.fm.database.objects

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jinqiujiang.fm.Keys
import com.jinqiujiang.fm.helpers.CollectionHelper
import com.jinqiujiang.fm.helpers.FileHelper
import com.jinqiujiang.fm.xml.RssHelper
import java.util.Date
import java.util.GregorianCalendar
import java.util.concurrent.TimeUnit


/*
 * Episode class
 */
@Entity(tableName = "episodes", indices = [Index(value = ["media_id"], unique = true), Index(value = ["publication_date"], unique = false), Index(value = ["remote_audio_file_location"], unique = true), Index(value = ["episode_remote_podcast_feed_location"], unique = false)])
data class Episode (

    // unique media id - currently just the remoteAudioFileLocation
    @PrimaryKey
    @ColumnInfo (name = "media_id") val mediaId: String,                                                    /* database version 1 | app version 31 => added   */

    @ColumnInfo (name = "guid") val guid: String,                                                           /* database version 1 | app version 31 => added   */
    @ColumnInfo (name = "title") val title: String,                                                         /* database version 1 | app version 31 => added   */
    @ColumnInfo (name = "audio") val audio: String,                                                         /* database version 1 | app version 31 => added   */
    @ColumnInfo (name = "cover") val cover: String,                                                         /* database version 1 | app version 31 => added   */
    @ColumnInfo (name = "small_cover") val smallCover: String,                                              /* database version 1 | app version 31 => added   */
    @ColumnInfo(name = "cover_version") val coverVersion: Long,                                             /* database version 7 | app version 49 => added   */
    @ColumnInfo (name = "publication_date") val publicationDate: Date,                                      /* database version 1 | app version 31 => added   */
    /* @ColumnInfo (name = "playback_state") val playbackState: Int, */                                     /* database version 2 | app version 40 => removed */
    @ColumnInfo (name = "is_playing") val isPlaying: Boolean,                                               /* database version 2 | app version 40 => added   */
    @ColumnInfo (name = "playback_position") val playbackPosition: Long,                                    /* database version 1 | app version 31 => added   */
    @ColumnInfo (name = "duration") val duration: Long,                                                     /* database version 1 | app version 31 => added   */
    @ColumnInfo (name = "content_type") val contentType: String,                                            /* database version 6 | app version 45 => added   */
    @ColumnInfo (name = "last_listened") val lastListened: Date,                                            /* database version 3 | app version 42 => added   */
    @ColumnInfo (name = "manually_deleted") val manuallyDeleted: Boolean,                                   /* database version 1 | app version 31 => added   */
    @ColumnInfo (name = "manually_downloaded") val manuallyDownloaded: Boolean,                             /* database version 1 | app version 31 => added   */
    @ColumnInfo (name = "podcast_name") val podcastName: String,                                            /* database version 1 | app version 31 => added   */
    @ColumnInfo (name = "remote_audio_file_location") val remoteAudioFileLocation: String,                  /* database version 1 | app version 31 => added   */
    @ColumnInfo (name = "remote_audio_file_name") val remoteAudioFileName: String,                          /* database version 1 | app version 31 => added   */
    @ColumnInfo (name = "remote_cover_file_location") val remoteCoverFileLocation: String,                  /* database version 1 | app version 31 => added   */

    // defines the relation between episode and podcast
    @ColumnInfo (name = "episode_remote_podcast_feed_location") val episodeRemotePodcastFeedLocation: String

) {


    /* Super-Constructor - can be used to clone an episode - with/without selected alterations */
    constructor(
        episode: Episode,
        mediaId: String = episode.mediaId,
        guid: String  = episode.guid,
        title: String  = episode.title,
        audio: String  = episode.audio,
        cover: String  = episode.cover,
        smallCover: String  = episode.smallCover,
        coverVersion: Long = episode.coverVersion,
        publicationDate: Date = episode.publicationDate,
        isPlaying: Boolean = episode.isPlaying,
        playbackPosition: Long = episode.playbackPosition,
        duration: Long = episode.duration,
        contentType: String = episode.contentType,
        lastListened: Date = episode.lastListened,
        manuallyDeleted: Boolean = episode.manuallyDeleted,
        manuallyDownloaded: Boolean = episode.manuallyDownloaded,
        podcastName: String  = episode.podcastName,
        remoteAudioFileLocation: String  = episode.remoteAudioFileLocation,
        remoteAudioFileName: String  = episode.remoteAudioFileName,
        remoteCoverFileLocation: String  = episode.remoteCoverFileLocation,
        episodeRemotePodcastFeedLocation: String  = episode.episodeRemotePodcastFeedLocation) : this (
        mediaId = mediaId,
        guid = guid,
        title = title,
        audio = audio,
        cover = cover,
        smallCover = smallCover,
        coverVersion = coverVersion,
        publicationDate = publicationDate,
        isPlaying = isPlaying,
        playbackPosition = playbackPosition,
        duration = duration,
        contentType = contentType,
        lastListened = lastListened,
        manuallyDeleted = manuallyDeleted,
        manuallyDownloaded = manuallyDownloaded,
        podcastName = podcastName,
        remoteAudioFileLocation = remoteAudioFileLocation,
        remoteAudioFileName = remoteAudioFileName,
        remoteCoverFileLocation = remoteCoverFileLocation,
        episodeRemotePodcastFeedLocation = episodeRemotePodcastFeedLocation
    )


    /* Constructor that uses output from RssHelper */
    constructor(rssEpisode: RssHelper.RssEpisode) : this (
        // use podcast name + episode guid as unique media id - used to be remoteAudioFileLocation
        mediaId = com.jinqiujiang.fm.PodcastConfig.episodeId(rssEpisode.guid, rssEpisode.remoteAudioFileLocation),
        guid = rssEpisode.guid,
        title = rssEpisode.title,
        audio = rssEpisode.audio,
        cover = rssEpisode.cover,
        smallCover = rssEpisode.smallCover,
        coverVersion = 0L,
        publicationDate = rssEpisode.publicationDate,
        isPlaying = rssEpisode.isPlaying,
        playbackPosition = rssEpisode.playbackPosition,
        duration = rssEpisode.duration,
        contentType = rssEpisode.contentType,
        lastListened = Date(),
        manuallyDownloaded = rssEpisode.manuallyDownloaded,
        manuallyDeleted = rssEpisode.manuallyDeleted,
        podcastName = rssEpisode.podcastName,
        remoteCoverFileLocation = rssEpisode.remoteCoverFileLocation,
        remoteAudioFileLocation = rssEpisode.remoteAudioFileLocation,
        remoteAudioFileName = FileHelper.getFileNameFromUrl(rssEpisode.remoteAudioFileLocation),
        episodeRemotePodcastFeedLocation = rssEpisode.episodeRemotePodcastFeedLocation
    )


    /* Return if an episode has been listened to end */
    fun isFinished(): Boolean = CollectionHelper.isEpisodeFinished(playbackPosition, duration)


    /* Return if an episode has been started listening to */
    fun hasBeenStarted(): Boolean = playbackPosition > 0L


    /* Return if an episode is abandoned (last listened to was a set number of days ago) */
    fun isAbandoned(): Boolean {
        val differenceMilliseconds = GregorianCalendar.getInstance().timeInMillis - lastListened.time
        val differenceDays: Long = TimeUnit.DAYS.convert(differenceMilliseconds, TimeUnit.MILLISECONDS)
        return differenceDays > Keys.NUMBER_OF_DAYS_UNTIL_ABANDONED
    }

}