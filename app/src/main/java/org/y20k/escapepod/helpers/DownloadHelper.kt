/*
 * DownloadHelper.kt
 * Implements the DownloadHelper object
 * A DownloadHelper provides helper methods for downloading files
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.EpisodeDescription
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.database.objects.PodcastDescription
import org.y20k.escapepod.database.wrappers.PodcastWithAllEpisodesWrapper
import org.y20k.escapepod.extensions.copy
import org.y20k.escapepod.xml.RssHelper
import java.util.Date
import java.util.GregorianCalendar
import java.util.StringTokenizer


/*
 * DownloadHelper object
 */
object DownloadHelper {

    /* Define log tag */
    private val TAG: String = DownloadHelper::class.java.simpleName


    /* Main class variables */
    private lateinit var collectionDatabase: CollectionDatabase
    private lateinit var downloadManager: DownloadManager
    private lateinit var activeDownloads: ArrayList<Long>
    private val activeDownloadsLock: Any = Any()
    private var pendingWork: Int = 0
    private lateinit var modificationDate: Date
    private var tempFolderCleared: Boolean = false


    /* Download a podcast */
    fun downloadPodcasts(context: Context, podcastUrlStrings: Array<String>) {
        // initialize main class variables, if necessary
        initialize(context)
        // convert array
        val uris: Array<Uri> = Array<Uri>(podcastUrlStrings.size) { index -> podcastUrlStrings[index].toUri() }
        // enqueue podcast
        enqueueDownload(context, uris, Keys.FILE_TYPE_RSS)
    }


    /* Download an episode */
    fun downloadEpisode(context: Context, mediaId: String, ignoreWifiRestriction: Boolean, manuallyDownloaded: Boolean = false) {
        // initialize main class variables, if necessary
        initialize(context)
        // get episode
        CoroutineScope(IO).launch {
            val episode: Episode? = collectionDatabase.episodeDao().findByMediaId(mediaId)
            if (episode != null) {
                // mark as manually downloaded if necessary
                if (manuallyDownloaded) {
                    collectionDatabase.episodeDao().upsert(Episode(episode, manuallyDownloaded = true))
                }
                // enqueue episode
                val uris = Array(1) { episode.remoteAudioFileLocation.toUri() }
                enqueueDownload(context, uris, Keys.FILE_TYPE_AUDIO, ignoreWifiRestriction)
            }
        }
    }


    /* Refresh cover of given podcast */
    fun refreshCover(context: Context, podcast: Podcast) {
        // initialize main class variables, if necessary
        initialize(context)
        // check if feed has a cover
        if (podcast.remoteImageFileLocation.isNotEmpty()) {
            Toast.makeText(context, context.getString(R.string.toast_message_refreshing_cover), Toast.LENGTH_LONG).show()
            CollectionHelper.clearImagesFolder(context, podcast)
            val uris: Array<Uri> = Array(1) { podcast.remoteImageFileLocation.toUri() }
            enqueueDownload(context, uris, Keys.FILE_TYPE_IMAGE)
        } else {
            Toast.makeText(context, context.getString(R.string.toast_message_error_refreshing_cover), Toast.LENGTH_LONG).show()
        }
    }


    /* Updates podcast collection */
    fun updateCollection(context: Context, clearTempFolder: Boolean = false) {
        // initialize main class variables, if necessary
        initialize(context)
        CoroutineScope(IO).launch {
            // delete temp files
            if (clearTempFolder && copyActiveDownloads().isEmpty()) {
                FileHelper.clearFolder(context.getExternalFilesDir(Keys.FOLDER_TEMP), 0)
            }
            // save time stamp of last update
            PreferencesHelper.saveLastUpdateCollection()
            // get all podcasts
            val podcasts: List<Podcast> = collectionDatabase.podcastDao().getAll()
            val uris: Array<Uri> = Array(podcasts.size) {podcasts[it].remotePodcastFeedLocation.toUri()}
            // enqueue downloads to DownloadManager
            enqueueDownload(context, uris, Keys.FILE_TYPE_RSS, ignoreWifiRestriction = false)
            // reset expanded state of podcast list
            PreferencesHelper.savePodcastListExpandedFeedLocation()
        }
    }


    /* Updates a selected podcast */
    fun updatePodcastEpisodes(context: Context, remotePodcastFeedLocation: String, clearTempFolder: Boolean = false) {
        // initialize main class variables, if necessary
        initialize(context)
        // delete temp files
        if (clearTempFolder && copyActiveDownloads().isEmpty()) {
            FileHelper.clearFolder(context.getExternalFilesDir(Keys.FOLDER_TEMP), 0)
        }
//        // save time stamp of last update // note: not necessary, users can update a single podcast as often as they want
//        PreferencesHelper.saveLastUpdateCollection()
        val uris: Array<Uri> = Array(1) { remotePodcastFeedLocation.toUri() }
        // enqueue downloads to DownloadManager
        enqueueDownload(context, uris, Keys.FILE_TYPE_RSS, ignoreWifiRestriction = false)
    }


    /* Updates all podcast covers */
    fun updateCovers(context: Context) {
        // initialize main class variables, if necessary
        initialize(context)
        // re-download all podcast covers
        CoroutineScope(IO).launch {
            PreferencesHelper.saveLastUpdateCollection()
            val podcasts: List<Podcast> = collectionDatabase.podcastDao().getAll()
            val uris: Array<Uri> = Array(podcasts.size) { podcasts[it].remoteImageFileLocation.toUri() }
            enqueueDownload(context, uris, Keys.FILE_TYPE_IMAGE)
            Log.i(TAG, "Updating all covers.")
        }
    }


    /* Processes a given download ID */
    fun processDownload(context: Context, downloadId: Long) {
        // initialize main class variables, if necessary
        initialize(context)

        // STEP 1: check if download was successful
        val downloadResult: Uri? = downloadManager.getUriForDownloadedFile(downloadId)
        if (downloadResult == null) {
            val downloadErrorCode: Int = getDownloadError(downloadId)
            val downloadErrorFileName: String = getDownloadFileName(downloadManager, downloadId)
            Toast.makeText(context, "${context.getString(R.string.toast_message_error_download_error)}: $downloadErrorFileName ($downloadErrorCode)", Toast.LENGTH_LONG).show()
            Log.w(TAG, "Download not successful: File name = $downloadErrorFileName Error code = $downloadErrorCode")
            removeFromActiveDownloads(arrayOf(downloadId), deleteDownload = true)
            return
        }
        // successfully got URI from DownloadManager
        val localFileUri: Uri = downloadResult
        val remoteFileLocation: String = getDownloadDescription(downloadManager, downloadId)

        // STEP 2: get MIME type of download
        var determinedFileType: String = String()
        Log.d(TAG, "Processing downloadId: $downloadId, localFileUri: $localFileUri")

        // step 2a: try to get the specific MIME type from DownloadManager
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(localFileUri, arrayOf(DownloadManager.COLUMN_MEDIA_TYPE, DownloadManager.COLUMN_STATUS), null, null, null)
            cursor?.use { // .use ensures the cursor is closed
                if (it.moveToFirst()) {
                    val statusColumnIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = if (statusColumnIndex != -1) it.getInt(statusColumnIndex) else -1
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        val mimeTypeColumnIndex = it.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
                        if (mimeTypeColumnIndex != -1) {
                            determinedFileType = it.getString(mimeTypeColumnIndex)
                            Log.d(TAG, "MIME type from DownloadManager column for $downloadId: $determinedFileType")
                        } else {
                            Log.d(TAG, "MIME type column not found for $downloadId.")
                        }
                    } else {
                        Log.d(TAG, "Download status not successful ($status) for $downloadId. Cannot reliably get MIME type from DM column.")
                    }
                } else {
                    Log.d(TAG, "Cursor was empty when querying DownloadManager for $downloadId.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying DownloadManager for MIME type for $downloadId: ${e.message}", e)
        } finally {
            // cursor is already closed by .use if it was not null
        }

        // step 2b: if DownloadManager didn't give a type, or it's generic, try ContentResolver.getType() as a fallback (though for DM URIs it often gives vnd.android.cursor.item/download)
        if (Keys.MIME_TYPES_OCTET_STREAM.contains(determinedFileType) || determinedFileType == Keys.MIME_TYPE_UNSUPPORTED || determinedFileType.isBlank()) {
            val resolverMimeType: String? = context.contentResolver.getType(localFileUri)
            Log.d(TAG, "Resolver.getType() for $localFileUri (downloadId $downloadId) returned: $resolverMimeType")
            if (resolverMimeType != null && resolverMimeType != "vnd.android.cursor.item/download" && resolverMimeType != Keys.MIME_TYPE_OCTET_STREAM && resolverMimeType != Keys.MIME_TYPE_UNSUPPORTED && resolverMimeType.isNotBlank()) {
                determinedFileType = resolverMimeType
                Log.d(TAG, "Using MIME type from resolver for $downloadId: $determinedFileType")
            }
        }

        // step 2c: if no specific type so far, look for magic bytes (file signature)
        if (Keys.MIME_TYPES_OCTET_STREAM.contains(determinedFileType) || determinedFileType == Keys.MIME_TYPE_UNSUPPORTED || determinedFileType.isBlank()) {
            val detectedType = FileHelper.detectMimeType(context, localFileUri)
            if (detectedType != Keys.MIME_TYPE_UNSUPPORTED) {
                determinedFileType = detectedType
                Log.d(TAG, "Using MIME type from magic bytes for $downloadId: $determinedFileType")
            }
        }

        // step 2d: if still no specific type, fall back to file extension
        if (Keys.MIME_TYPES_OCTET_STREAM.contains(determinedFileType) || determinedFileType == Keys.MIME_TYPE_UNSUPPORTED || determinedFileType.isBlank()) {
            val fileName: String = getDownloadFileName(downloadManager, downloadId)
            // get the original or downloaded file name
            if (fileName.isNotBlank()) {
                determinedFileType = FileHelper.getContentTypeFromExtension(fileName)
                Log.d(TAG, "Using MIME type from file extension '$fileName' for $downloadId: $determinedFileType")
            }
        }

        // STEP 3: handle download according to mime type
        Log.i(TAG, "File type for downloadId $downloadId is $determinedFileType")
        when (determinedFileType) {
            in Keys.MIME_TYPES_RSS -> readPodcastFeed(context, localFileUri, remoteFileLocation)
            in Keys.MIME_TYPES_ATOM -> Toast.makeText(context, context.getString(R.string.toast_message_error_feed_not_supported), Toast.LENGTH_LONG).show()
            in Keys.MIME_TYPES_AUDIO -> setEpisodeMediaUri(context, localFileUri, remoteFileLocation, determinedFileType)
            in Keys.MIME_TYPES_IMAGE -> setPodcastImage(context, localFileUri, remoteFileLocation)
            else -> Toast.makeText(context,"${context.getString(R.string.toast_message_error_file_type_unknown)}: $determinedFileType", Toast.LENGTH_LONG).show()
        }
        // remove ID from active downloads
        removeFromActiveDownloads(arrayOf(downloadId))
    }


    /* Removes downloads that will never finish from the list of active downloads */
    fun reconcileActiveDownloads(context: Context) {
        // initialize main class variables, if necessary
        initialize(context)
        val inactiveDownloads: List<Long> = copyActiveDownloads().filter { downloadId -> !isDownloadActive(downloadId) }
        if (inactiveDownloads.isNotEmpty()) {
            removeFromActiveDownloads(inactiveDownloads.toTypedArray())
        }
    }


    /* Initializes main class variables of DownloadHelper, if necessary */
    private fun initialize(context: Context) {
        if (!this::modificationDate.isInitialized) {
            modificationDate = PreferencesHelper.loadCollectionModificationDate()
        }
        if (!this::collectionDatabase.isInitialized) {
            collectionDatabase = CollectionDatabase.getInstance(context)
        }
        if (!this::downloadManager.isInitialized) {
            downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        }
        if (!this::activeDownloads.isInitialized) {
            initializeActiveDownloads()
        }
    }


    /* Enqueues an Array of file Uris in DownloadManager */
    private fun enqueueDownload(context: Context, uris: Array<Uri>, type: Int, ignoreWifiRestriction: Boolean = false) {
        val backgroundJob = Job()
        val ioScope = CoroutineScope(Dispatchers.IO + backgroundJob)
        beginPendingWork()
        ioScope.launch {
            // delete leftover temp files - once per session, before the first download
            if (!tempFolderCleared) {
                FileHelper.clearFolder(context.getExternalFilesDir(Keys.FOLDER_TEMP), 0)
                tempFolderCleared = true
            }
            // do not start downloads when storage is insufficient (F-13)
            if (!FileHelper.enoughFreeSpaceAvailable(context)) {
                Log.w(TAG, "Not enough free storage space - skipping download.")
                withContext(Main) { Toast.makeText(context, R.string.toast_message_error_insufficient_storage, Toast.LENGTH_LONG).show() }
                backgroundJob.cancel()
                return@launch
            }
            // determine allowed network types
            val allowedNetworkTypes: Int = determineAllowedNetworkTypes(type, ignoreWifiRestriction)
            // enqueue downloads
            val newIds = LongArray(uris.size)
            for (i in uris.indices) {
                // resolve url redirects
                val resolvedUri: Uri = NetworkHelper.resolveRedirects(uris[i].toString()).toUri()
                Log.v(TAG, "DownloadManager enqueue: $resolvedUri")
                // check if valid url and prevent double download
                if (NetworkHelper.isSafeRemoteUrl(resolvedUri.toString()) && isNotInDownloadQueue(resolvedUri)) {
                    val fileName: String = FileHelper.getFileNameFromUri(resolvedUri)
                    val request: DownloadManager.Request = DownloadManager.Request(resolvedUri)
                        .setAllowedNetworkTypes(allowedNetworkTypes)
                        .setTitle(fileName)
                        .setDescription(uris[i].toString()) // store the unresolved URL
                        .setDestinationInExternalFilesDir(context, Keys.FOLDER_TEMP, fileName)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                        .addRequestHeader("User-Agent", Keys.USER_AGENT)
                    newIds[i] = downloadManager.enqueue(request)
                    addActiveDownload(newIds[i])
                }
            }
            backgroundJob.cancel()
        }.invokeOnCompletion { endPendingWork() }
    }


    /* Enqueue episode and podcast cover */
    private fun enqueuePodcastMediaFiles(context: Context, podcast: Podcast, episodes: List<Episode>, isNewPodcast: Boolean, ignoreWifiRestriction: Boolean = false) {
        // new podcast: first download the cover
        if (isNewPodcast && podcast.remoteImageFileLocation.isNotEmpty()) {
            CollectionHelper.clearImagesFolder(context, podcast)
            val coverUris: Array<Uri> = Array(1) { podcast.remoteImageFileLocation.toUri() }
            enqueueDownload(context, coverUris, Keys.FILE_TYPE_IMAGE)
        }
        // download audio files only when connected to wifi - or if user chose otherwise
        if (ignoreWifiRestriction || PreferencesHelper.loadEpisodeDownloadOverMobile() || NetworkHelper.isConnectedToWifi(context)) {
            // download only if podcast has episodes
            if (episodes.isNotEmpty()) {
                // start download of latest episode audio file
                val episodeUris: Array<Uri> = Array(1) { episodes[0].remoteAudioFileLocation.toUri() }
                enqueueDownload(context, episodeUris, Keys.FILE_TYPE_AUDIO)
            }
        }
    }


    /* Adds podcast and episodes to podcast collection - use within co-routine */
    private fun addPodcast(context: Context, rssPodcast: RssHelper.RssPodcast) {
        // extract database objects from rss podcast
        val podcastWithAllEpisodes: PodcastWithAllEpisodesWrapper = PodcastWithAllEpisodesWrapper(rssPodcast)
        val podcast: Podcast
        val episodes: List<Episode>
        val podcastDescription: PodcastDescription = PodcastDescription(rssPodcast)
        val episodeDescriptions: List<EpisodeDescription> = rssPodcast.episodes.map { EpisodeDescription(it) }
        // store old podcast
        val oldPodcast: Podcast? = collectionDatabase.podcastDao().findByRemotePodcastFeedLocation(podcastWithAllEpisodes.data.remotePodcastFeedLocation)
        val isNewPodcast: Boolean
        if (oldPodcast != null) {
            // CASE: existing podcast
            isNewPodcast = false
            podcast = Podcast(podcastWithAllEpisodes.data, cover = oldPodcast.cover, smallCover = oldPodcast.smallCover)
            val oldEpisodes: List<Episode> = collectionDatabase.episodeDao().findByEpisodeRemotePodcastFeedLocation(podcastWithAllEpisodes.data.remotePodcastFeedLocation)
            episodes = CollectionHelper.updateEpisodeList(podcast = podcast, oldEpisodes = oldEpisodes, newEpisodes = podcastWithAllEpisodes.episodes).sortedByDescending { it.publicationDate }
        } else {
            // CASE: new podcast
            isNewPodcast = true
            episodes = podcastWithAllEpisodes.episodes.sortedByDescending { it.publicationDate }
            podcast = podcastWithAllEpisodes.data
        }
        // update/insert podcast
        collectionDatabase.podcastDao().upsert(podcast)
        collectionDatabase.podcastDescriptionDao().upsert(podcastDescription)
        // update/insert episodes
        collectionDatabase.episodeDao().upsertAll(episodes)
        collectionDatabase.episodeDescriptionDao().upsertAll(episodeDescriptions)
        // enqueue audio files and cover
        val hasDownloadableEpisodes: Boolean = CollectionHelper.hasDownloadableEpisodes(episodes)
        if (isNewPodcast || hasDownloadableEpisodes) {
            enqueuePodcastMediaFiles(context, podcast, episodes, isNewPodcast)
        }
        // delete un-needed audio files and update episodes in database
        if (!isNewPodcast) {
            val updatedEpisodes: List<Episode> = CollectionHelper.deleteUnneededAudioFiles(PodcastWithAllEpisodesWrapper(podcast, episodes))
            collectionDatabase.episodeDao().upsertAll(updatedEpisodes)
        }
    }


    /* Sets podcast cover */
    private fun setPodcastImage(context: Context, tempFileUri: Uri, remoteFileLocation: String) {
        CoroutineScope(IO).launch {
            // save cover
            val podcastData: Podcast? = collectionDatabase.podcastDao().findByRemoteImageFileLocation(remoteFileLocation)
            val tempFileUriString: String = tempFileUri.toString()
            if (podcastData != null && tempFileUriString.isNotEmpty()) {
                val timestamp: Long = GregorianCalendar.getInstance().time.time
                val smallCover: String = FileHelper.saveCover(context, podcastData.name, tempFileUriString, Keys.SIZE_COVER_PODCAST_SMALL, Keys.PODCAST_SMALL_COVER_FILE, timestamp).toString()
                val cover: String = FileHelper.saveCover(context, podcastData.name, tempFileUriString, Keys.SIZE_COVER_PODCAST, Keys.PODCAST_COVER_FILE, timestamp).toString()
                // update podcast cover
                collectionDatabase.podcastDao().updateCover(remoteImageFileLocation = remoteFileLocation, cover = cover, smallCover = smallCover, timestamp)
                // update covers for all episodes
                collectionDatabase.episodeDao().updateCover(episodeRemotePodcastFeedLocation = podcastData.remotePodcastFeedLocation, cover = cover, smallCover = smallCover, timestamp)
            }
        }
    }


    /* Sets Media Uri in episode */
    private fun setEpisodeMediaUri(context: Context, tempFileUri: Uri, remoteAudioFileLocation: String, contentType: String) {
        CoroutineScope(IO).launch {
            // save file and update audio reference and duration
            val episode: Episode? = collectionDatabase.episodeDao().findByRemoteAudioFileLocation(remoteAudioFileLocation)
            if (episode != null) {
                val audio: String = FileHelper.saveCopyOfFile(context, episode.podcastName, tempFileUri, Keys.FILE_TYPE_AUDIO, FileHelper.getFileName(context, tempFileUri), async = true).toString()
                val duration: Long = AudioHelper.getDuration(context, tempFileUri)
                // todo abort and notify user when duration is 0L
                val updatedEpisodesCount: Int = collectionDatabase.episodeDao().updateAudioRemoteAudioFileLocation(remoteAudioFileLocation = remoteAudioFileLocation, audio = audio, duration = duration, contentType = contentType)

                // no matching episode updated - try matching filename only (second run) - a hack that should prevent a ton of network requests for potential redirects (e.g. feedburner links)
                if (updatedEpisodesCount <= 0) {
                    val localFileName: String = FileHelper.getFileName(context, tempFileUri)
                    collectionDatabase.episodeDao().updateAudioByFileName(localFileName = localFileName, audio = audio, duration = duration, contentType = contentType)
                }

//                // remove unused audio references from collection
//                val podcast: PodcastWithAllEpisodesWrapper? = collectionDatabase.podcastDao().getWithRemotePodcastFeedLocation(episode.episodeRemotePodcastFeedLocation)
//                if (podcast != null) {
//                    // delete un-needed audio files
//                    val updatedEpisodes: List<Episode> = CollectionHelper.deleteUnneededAudioFiles(podcast)
//                    // update episodes in database
//                    collectionDatabase.episodeDao().upsertAll(updatedEpisodes)
//                }
            }
        }
    }


    /* Checks if a file is not yet in download queue */
    private fun isNotInDownloadQueue(remoteFileLocationUri: Uri): Boolean {
        copyActiveDownloads().forEach { downloadId ->
            if (getRemoteFileLocation(downloadManager, downloadId) == remoteFileLocationUri.toString()) {
                Log.w(TAG, "File is already in download queue: $remoteFileLocationUri")
                return false
            }
        }
        Log.v(TAG, "File is not in download queue.")
        return true
    }


    /* Safely loads activeDownloads from shared preferences - unless another thread got there first */
    private fun initializeActiveDownloads() {
        val loadedActiveDownloads: ArrayList<Long> = getActiveDownloads()
        // only one thread at a time can run a block locked on activeDownloadsLock, so these blocks never touch activeDownloads concurrently
        synchronized(activeDownloadsLock) {
            if (!this::activeDownloads.isInitialized) activeDownloads = loadedActiveDownloads
        }
    }


    /* Safely adds given download ID to activeDownloads */
    private fun addActiveDownload(downloadId: Long) {
        synchronized(activeDownloadsLock) {
            activeDownloads.add(downloadId)
            setActiveDownloads(activeDownloads)
        }
    }


    /* Safely returns a copy of activeDownloads */
    private fun copyActiveDownloads(): ArrayList<Long> {
        synchronized(activeDownloadsLock) {
            return activeDownloads.copy()
        }
    }


    /* Safely registers work (enqueueing, feed parsing) that may still add downloads */
    private fun beginPendingWork() {
        synchronized(activeDownloadsLock) {
            pendingWork++
        }
    }


    /* Safely unregisters finished work - publishes the active downloads once no work is left */
    private fun endPendingWork() {
        synchronized(activeDownloadsLock) {
            pendingWork--
            if (pendingWork == 0) {
                setActiveDownloads(activeDownloads)
            }
        }
    }


    /* Safely remove given download IDs from activeDownloads and delete download if requested */
    private fun removeFromActiveDownloads(downloadIds: Array<Long>, deleteDownload: Boolean = false): Boolean {
        // remove download ids from activeDownloads
        val success: Boolean = synchronized(activeDownloadsLock) {
            val removed: Boolean = activeDownloads.removeAll { downloadId -> downloadIds.contains(downloadId) }
            if (removed) {
                setActiveDownloads(activeDownloads)
            }
            removed
        }
        // optionally: delete download
        if (deleteDownload) {
            downloadIds.forEach { downloadId -> downloadManager.remove(downloadId) }
        }
        return success
    }


    /* Async via coroutine: Reads podcast feed */
    private fun readPodcastFeed(context: Context, localFileUri: Uri, remoteFileLocation: String) {
        beginPendingWork()
        CoroutineScope(IO).launch {
            Log.v(TAG, "Reading podcast RSS file ($remoteFileLocation) - Thread: ${Thread.currentThread().name}")
            // read RSS and create podcast
            val rssPodcast: RssHelper.RssPodcast = RssHelper().read(context, localFileUri, remoteFileLocation)
            // validate podcast
            when (CollectionHelper.validateRssPodcast(rssPodcast.remoteImageFileLocation.isEmpty(), rssPodcast.episodes.isEmpty())) {
                Keys.PODCAST_VALIDATION_SUCESS -> {
                    addPodcast(context, rssPodcast)
                }
                Keys.PODCAST_VALIDATION_MISSING_COVER -> {
                    addPodcast(context, rssPodcast)
                    withContext(Main) {
                        Toast.makeText(context, context.getString(R.string.toast_message_error_validation_missing_cover), Toast.LENGTH_LONG).show()
                    }
                }
                Keys.PODCAST_VALIDATION_NO_VALID_EPISODES -> {
                    withContext(Main) {
                        Toast.makeText(context, context.getString(R.string.toast_message_error_validation_no_valid_episodes), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.invokeOnCompletion { endPendingWork() }
    }


    /* Saves active downloads (IntArray) to shared preferences */
    private fun setActiveDownloads(activeDownloads: ArrayList<Long>) {
        // build and save under the lock, so concurrent writers cannot overtake each other
        synchronized(activeDownloadsLock) {
            val builder = StringBuilder()
            for (i in activeDownloads.indices) {
                builder.append(activeDownloads[i]).append(",")
            }
            val activeDownloadsString: String = builder.toString()
            if (activeDownloadsString.isNotEmpty()) {
                PreferencesHelper.saveActiveDownloads(activeDownloadsString)
            } else if (pendingWork == 0) {
                // publish an empty list only when no work that may add downloads is still running
                PreferencesHelper.saveActiveDownloads(Keys.ACTIVE_DOWNLOADS_EMPTY)
            }
        }
    }


    /* Loads active downloads (IntArray) from shared preferences */
    private fun getActiveDownloads(): ArrayList<Long> {
        var inactiveDownloadsFound: Boolean = false
        val activeDownloadsList: ArrayList<Long> = arrayListOf<Long>()
        val activeDownloadsString: String = PreferencesHelper.loadActiveDownloads()
        val count = activeDownloadsString.split(",").size - 1
        val tokenizer = StringTokenizer(activeDownloadsString, ",")
        repeat(count) {
            val token = tokenizer.nextToken().toLong()
            when (isDownloadActive(token)) {
                true -> activeDownloadsList.add(token)
                false -> inactiveDownloadsFound = true
            }
        }
        if (inactiveDownloadsFound) setActiveDownloads(activeDownloadsList)
        return activeDownloadsList
    }


    /* Determines the remote file location (URL given to DownloadManager.request) */
    private fun getRemoteFileLocation(downloadManager: DownloadManager, downloadId: Long): String {
        var remoteFileLocation: String = ""
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (cursor.count > 0) {
                cursor.moveToFirst()
                remoteFileLocation = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI))
            }
        }
        return remoteFileLocation
    }


    /* Determines the file name for given download id */
    private fun getDownloadFileName(downloadManager: DownloadManager, downloadId: Long): String {
        var downloadFileName: String = ""
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (cursor.count > 0) {
                cursor.moveToFirst()
                downloadFileName = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE))
            }
        }
        return downloadFileName
    }


    /* Determines the description for given download id */
    private fun getDownloadDescription(downloadManager: DownloadManager, downloadId: Long): String {
        var downloadDescription: String = ""
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (cursor.count > 0) {
                cursor.moveToFirst()
                downloadDescription = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION))
            }
        }
        return downloadDescription
    }


    /* Checks if a given download ID represents a finished download */
    private fun isDownloadFinished(downloadId: Long): Boolean {
        var downloadStatus: Int = -1
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (cursor.count > 0) {
                cursor.moveToFirst()
                downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            }
        }
        return (downloadStatus == DownloadManager.STATUS_SUCCESSFUL)
    }


    /* Checks if a given download ID represents a download that is still expected to finish */
    private fun isDownloadActive(downloadId: Long): Boolean {
        var downloadStatus: Int = -1
        var downloadReason: Int = -1
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (cursor.count > 0) {
                cursor.moveToFirst()
                downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                downloadReason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))
            }
        }
        return when (downloadStatus) {
            DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING -> true
            // a download waiting for WiFi may never start - do not treat it as active
            DownloadManager.STATUS_PAUSED -> downloadReason != DownloadManager.PAUSED_QUEUED_FOR_WIFI
            else -> false
        }
    }


    /* Retrieves reason of download error - returns http error codes plus error codes found here check: https://developer.android.com/reference/android/app/DownloadManager */
    private fun getDownloadError(downloadId: Long): Int {
        var reason: Int = -1
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (cursor.count > 0) {
                cursor.moveToFirst()
                val downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                if (downloadStatus == DownloadManager.STATUS_FAILED) {
                    reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))
                }
            }
        }
        return reason
    }


    /* Determine allowed network type */
    private fun determineAllowedNetworkTypes(type: Int, ignoreWifiRestriction: Boolean): Int {
        var allowedNetworkTypes: Int =  (DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        // restrict download of audio files to WiFi if necessary
        if (type == Keys.FILE_TYPE_AUDIO) {
            val downloadOverMobileAllowed: Boolean = PreferencesHelper.loadEpisodeDownloadOverMobile()
            if (!ignoreWifiRestriction && !downloadOverMobileAllowed) {
                allowedNetworkTypes = DownloadManager.Request.NETWORK_WIFI
            }
        }
        return allowedNetworkTypes
    }

}
