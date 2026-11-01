/*
 * CollectionHelper.kt
 * Implements the CollectionHelper object
 * A CollectionHelper provides helper methods for the podcast collection
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.y20k.escapepod.Keys
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.database.wrappers.PodcastWithAllEpisodesWrapper
import java.io.File
import java.util.Calendar
import java.util.Date


/*
 * CollectionHelper object
 */
object CollectionHelper {

    /* Define log tag */
    private val TAG: String = CollectionHelper::class.java.simpleName


    /* Check if a rss podcast (= download result) has cover and audio files */
    fun validateRssPodcast(remoteImageFileLocationEmpty: Boolean, epsiodesEmpty: Boolean): Int {
        var result: Int = Keys.PODCAST_VALIDATION_SUCESS
        // check for cover url
        if (remoteImageFileLocationEmpty)  {
            result = Keys.PODCAST_VALIDATION_MISSING_COVER
        }
        // check for audio files
        if (epsiodesEmpty) {
            result = Keys.PODCAST_VALIDATION_NO_VALID_EPISODES
        }
        return result
    }


    /* Checks if enough time passed since last update */
    fun hasEnoughTimePassedSinceLastUpdate(): Boolean {
        val lastUpdate: Date = PreferencesHelper.loadLastUpdateCollection()
        val currentDate: Date = Calendar.getInstance().time
        return currentDate.time - lastUpdate.time  > Keys.MINIMUM_TIME_BETWEEN_UPDATES
    }


    /* Returns if podcast has episodes that can be downloaded */
    fun hasDownloadableEpisodes(episodes: List<Episode>): Boolean = episodes.isNotEmpty() && episodes[0].audio.isEmpty() && !episodes[0].manuallyDeleted


    /* Returns if an episode has been listened to end */
    fun isEpisodeFinished(playbackPosition: Long, duration: Long): Boolean {
        if (playbackPosition == 0L || duration == 0L) return false
        return playbackPosition >= duration - Keys.PLAYBACK_FINISH_SLACK_MS
    }


    /* Copies over episode states from old episode list to new episode list */
    fun updateEpisodeList(podcast: Podcast, oldEpisodes: List<Episode>, newEpisodes: List<Episode>): List<Episode> {
        val updatedEpisodeList: MutableList<Episode> = mutableListOf()
        newEpisodes.forEach { newEpisode ->
            // try to find matching old episode
            var isNew: Boolean = true
            oldEpisodes.forEach { oldEpisode ->
                // matching old episode found - update old episode and add to list
                if (oldEpisode.mediaId == newEpisode.mediaId ||
                    /* special case for remoteAudioFileLocation because this used to be the mediaId - todo remove in v1.1.5 */
                    oldEpisode.mediaId == newEpisode.remoteAudioFileLocation) {
                    isNew = false
                    val updatedEpisode: Episode = Episode(newEpisode,
                        audio = oldEpisode.audio,
                        cover = podcast.cover,
                        smallCover = podcast.smallCover,
                        isPlaying = oldEpisode.isPlaying,
                        playbackPosition = oldEpisode.playbackPosition,
                        duration = oldEpisode.duration,
                        lastListened = oldEpisode.lastListened,
                        manuallyDeleted = oldEpisode.manuallyDeleted,
                        manuallyDownloaded = oldEpisode.manuallyDownloaded)
                    updatedEpisodeList.add(updatedEpisode)
                }
            }
            // no matching old episode found - add new episode to list
            if (isNew) {
                val updatedEpisode: Episode = Episode(newEpisode,
                        cover = podcast.cover,
                        smallCover = podcast.smallCover)
                updatedEpisodeList.add(updatedEpisode)
            }
        }
        return updatedEpisodeList
    }


    /* Clears an image folder for a given podcast */
    fun clearImagesFolder(context: Context, podcast: Podcast) {
        // clear image folder
        val imagesFolder: File = File(context.getExternalFilesDir(""), FileHelper.determineDestinationFolderPath(Keys.FILE_TYPE_IMAGE, podcast.name))
        FileHelper.clearFolder(imagesFolder, 0)
    }


    /* Deletes Images and Audio folder of a given podcast */
    fun deletePodcastFolders(context: Context, podcast: Podcast) {
        // delete image folder
        val imagesFolder: File = File(context.getExternalFilesDir(""), FileHelper.determineDestinationFolderPath(Keys.FILE_TYPE_IMAGE, podcast.name))
        FileHelper.clearFolder(imagesFolder, 0, true)
        // delete audio folder
        val audioFolder: File = File(context.getExternalFilesDir(""), FileHelper.determineDestinationFolderPath(Keys.FILE_TYPE_AUDIO, podcast.name))
        FileHelper.clearFolder(audioFolder, 0, true)
    }


    /* Removes feeds that are already in the podcast collection */
    fun removeDuplicates(podcastList: List<Podcast>, feedUrls: Array<String>): Array<String> {
        val feedUrlList: MutableList<String> = feedUrls.toMutableList()
        podcastList.forEach { podcast ->
            feedUrlList.remove(podcast.remotePodcastFeedLocation)
        }
        return feedUrlList.toTypedArray()
    }


    /* Export podcast collection as OPML */
    fun exportCollectionOpml(context: Context, podcastList: List<Podcast>) {
        Log.v(TAG, "Exporting podcast collection as OPML")
        // export collection as OPML - launch = fire & forget (no return value from save collection)
        CoroutineScope(IO).launch { FileHelper.backupCollectionAsOpmlSuspended(context, podcastList) }
    }


    /* Creates a MediaItem with MediaMetadata for a single episode - used to prepare player */
    fun buildMediaItem(context: Context, episode: Episode, streaming: Boolean = false): MediaItem {
        // get the correct source for streaming / local playback
        // streaming hands a feed-supplied remote url straight to ExoPlayer - guard its scheme (http/https only)
        // so file:// / content:// can not reach the media pipeline; local playback uses an app-internal file uri (not checked)
        // note: no caller passes streaming = true - if that changes, give the player a DefaultHttpDataSource.Factory with Keys.USER_AGENT (see BasePlayerService.localPlayer)
        val source: String = if (streaming && !NetworkHelper.isAllowedScheme(episode.remoteAudioFileLocation)) {
            Log.w(TAG, "Rejected streaming media uri with disallowed scheme: ${episode.remoteAudioFileLocation}")
            String()
        } else if (streaming) {
            episode.remoteAudioFileLocation
        } else {
            episode.audio
        }
        // put uri in RequestMetadata - credit: https://stackoverflow.com/a/70103460
        val requestMetadata = MediaItem.RequestMetadata.Builder().apply {
            setMediaUri(source.toUri())
        }.build()
        // build MediaMetadata
        val mediaMetadata = MediaMetadata.Builder().apply {
            setTitle(episode.title)
            setSubtitle(episode.podcastName)
            setAlbumTitle(episode.podcastName)
            setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            setIsBrowsable(false)
            setIsPlayable(true)
            setExtras(bundleOf(Pair(Keys.KEY_ORIGINAL_EPISODE_URI, episode.remoteAudioFileLocation), Pair(Keys.KEY_ORIGINAL_ARTWORK_URI, episode.remoteCoverFileLocation), Pair(Keys.KEY_EPISODE_PLAYBACK_POSITION, episode.playbackPosition), Pair(Keys.KEY_EPISODE_DURATION, episode.duration)))
            if (episode.cover.isNotEmpty()) {
                val coverFile: File = episode.cover.toUri().toFile()
                if (coverFile.exists()) {
                    maybeSetArtworkData(coverFile.readBytes(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            } else {
                // setArtworkUri(Uri.parse(Keys.LOCATION_RESOURCES + R.raw.ic_default_cover_rss_icon))
                maybeSetArtworkData(UiHelper.getDefaultCoverAsByteArray(context), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }.build()
        // build MediaItem and return it
        return MediaItem.Builder().apply {
            setMediaId(episode.mediaId)
            setRequestMetadata(requestMetadata)
            setMediaMetadata(mediaMetadata)
            setUri(source.toUri())
            setMimeType(episode.contentType)
        }.build()
    }


    /* Creates the Root media item for the browser */
    fun getRootItem(): MediaItem {
        return MediaItem.Builder().apply {
            setMediaId(Keys.MEDIA_BROWSER_ROOT)
            setMediaMetadata(
                MediaMetadata.Builder(). apply {
                    setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                    setIsBrowsable(true)
                    setIsPlayable(false)
                }.build())
        }.build()
    }


    /* Creates a media item for a single podcast for the browser */
    fun getPodcastItem(context: Context, podcast: Podcast): MediaItem {
        return MediaItem.Builder().apply {
            setMediaId(podcast.remotePodcastFeedLocation)
            setMediaMetadata(
                MediaMetadata.Builder(). apply {
                    setTitle(podcast.name)
                    setSubtitle(podcast.name)
                    if (podcast.cover.isNotEmpty()) {
                        val coverFile: File = podcast.cover.toUri().toFile()
                        if (coverFile.exists()) {
                            maybeSetArtworkData(coverFile.readBytes(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                    } else {
                        // setArtworkUri(Uri.parse(Keys.LOCATION_RESOURCES + R.raw.ic_default_cover_rss_icon))
                        maybeSetArtworkData(UiHelper.getDefaultCoverAsByteArray(context), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                    setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
                    setIsBrowsable(true)
                    setIsPlayable(false)
                }.build())
        }.build()
    }


    /* Creates a list of children media items for the browser */
    fun getChildren(context: Context, parentId: String, collectionDatabase: CollectionDatabase): List<MediaItem> {
        val children: MutableList<MediaItem> = mutableListOf()
        if (parentId == Keys.MEDIA_BROWSER_ROOT) {
            val podcasts: List<Podcast> = collectionDatabase.podcastDao().getAll()
            podcasts.forEach { podcast -> children.add(getPodcastItem(context, podcast)) }
        } else {
            val episodes: List<Episode> = collectionDatabase.episodeDao().getAllWithRemotePodcastFeedLocation(parentId)
            episodes.forEach { episode -> if (episode.audio.isNotEmpty()) children.add(buildMediaItem(context, episode)) }
        }
        return children
    }


    /* Deletes audio files that are no longer needed - but keep episodes */
    fun deleteUnneededAudioFiles(podcast: PodcastWithAllEpisodesWrapper): List<Episode> {
        val episodes: List<Episode> = podcast.episodes.sortedByDescending { episode -> episode.publicationDate }.toMutableList()
        val updatedEpisodes: MutableList<Episode> = mutableListOf()
        val numberOfAudioFilesToKeep: Int = PreferencesHelper.numberOfAudioFilesToKeep()
        val numberOfEpisodes: Int = episodes.size

        if (numberOfEpisodes > numberOfAudioFilesToKeep) {
            for (i in numberOfEpisodes -1 downTo numberOfAudioFilesToKeep) {
                val episode: Episode = episodes[i]
                // check if episode can be deleted
                if (canBeDeleted(episode)) {
                    // delete audio file
                    try {
                        episode.audio.toUri().toFile().delete()
                    } catch (e: Exception) {
                        Log.e(TAG, "Unable to delete file. File has probably been deleted manually. Stack trace: $e")
                    }
                    // remove audio reference
                    val updatedEpisode: Episode = Episode(episode, audio = String(), isPlaying = false, playbackPosition = 0L, duration = 0L)
                    // add to updated list
                    updatedEpisodes.add(updatedEpisode)
                }
            }
        }
        return updatedEpisodes
    }


    /* Deletes all files in the audio folders of a collection */
    fun deleteAllAudioFiles(context: Context) {
        // get main audio directory
        val audioFolder: File? = context.getExternalFilesDir(Keys.FOLDER_AUDIO)
        if (audioFolder != null && audioFolder.exists() && audioFolder.isDirectory) {
            // list sub-directories
            audioFolder.listFiles()?.forEach { podcastAudioFolder ->
                if (podcastAudioFolder != null && podcastAudioFolder.exists() && podcastAudioFolder.isDirectory) {
                    FileHelper.clearFolder(podcastAudioFolder, 0, false)
                }
            }
        }
    }


    /* Deletes an episode audio file - used when user presses the delete button */
    fun deleteEpisodeAudioFile(episode: Episode, manuallyDeleted: Boolean = false): Episode {
        // delete audio file
        Log.d(TAG, "Deleting audio file for episode: ${episode.title}")
        try {
            episode.audio.toUri().toFile().delete()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to delete file. File has probably been deleted manually. Stack trace: $e")
        }
        return Episode(episode, manuallyDeleted = manuallyDeleted, manuallyDownloaded = false, audio = String())
    }


    /* Delete files in audio folder that are not referenced in collection - used for housekeeping */
    fun deleteUnReferencedAudioFiles(context: Context, episodeList: List<Episode>) {
        val audioFileReferences: List<String> = getAllAudioFileReferences(episodeList)
        // get main audio directory
        val audioFolder: File? = context.getExternalFilesDir(Keys.FOLDER_AUDIO)
        if (audioFolder != null && audioFolder.exists() && audioFolder.isDirectory)  {
            // list sub-directories
            audioFolder.listFiles()?.forEach { podcastAudioFolder ->
                // look for un-referenced files in each subfolder
                podcastAudioFolder.listFiles()?.forEach { audioFile ->
                    val fileUriString: String = Uri.fromFile(audioFile).toString()
                    if (!(audioFileReferences.contains(fileUriString))) {
                        audioFile.delete()
                    }
                }

            }
        }
    }


    /* Determines if an episode audio file can be deleted */
    private fun canBeDeleted(episode: Episode): Boolean {
        if (episode.audio.isEmpty()) {
            // episode has no audio reference
            return false
        } else if (episode.isPlaying && !episode.isFinished()) { // todo test where playbackState is set to stopped
            // episode is playing and not finished
            return false
        } else if (episode.guid == PreferencesHelper.loadUpNextMediaId()) {
            // episode is in Up Next queue
            return false
        } else if (episode.hasBeenStarted() && !episode.isFinished() && !episode.isAbandoned()) {
            // episode has been started but is not finished and is not abandoned
            return false
        } else if (episode.manuallyDownloaded && !episode.isAbandoned()) {
            // episode was manually downloaded
            return false
        } else if (!episode.isFinished() && !PreferencesHelper.loadDownloadAutoCleanupEnabled()) {
            // episode is not finished and auto cleanup is disabled (in Settings)
            return false
        } else {
            // none of the above: episode audio file may be deleted
            return true
        }
    }


    /* Extracts all audio file references from a collection */
    private fun getAllAudioFileReferences(episodeList: List<Episode>): ArrayList<String> {
        val audioFileReferences: ArrayList<String> = arrayListOf()
        episodeList.forEach { episode ->
            if (episode.audio.isNotBlank()) {
                audioFileReferences.add(episode.audio)
            }
        }
        return audioFileReferences
    }

}
