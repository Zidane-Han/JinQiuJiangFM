/*
 * BasePlayerService.kt
 * Implements the BasePlayerService abstract class
 * BasePlayerService is Escapepod's foreground service that plays podcast audio
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jinqiujiang.fm

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jinqiujiang.fm.database.CollectionDatabase
import com.jinqiujiang.fm.database.objects.Episode
import com.jinqiujiang.fm.helpers.CollectionHelper
import com.jinqiujiang.fm.helpers.PreferencesHelper

/*
 * BasePlayerService class
 */
@UnstableApi
abstract class BasePlayerService: MediaLibraryService() {


    /* Define log tag */
    private val TAG: String = BasePlayerService::class.java.simpleName


    /* Main class variables */
    abstract val player: Player
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var collectionDatabase: CollectionDatabase
    private lateinit var sleepTimer: CountDownTimer
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val librarySessionCallback = CustomMediaLibrarySessionCallback()
    private var sleepTimerTimeRemaining: Long = 0L
    private var sleepTimerRunning: Boolean = false
    private var upNextEpisodeMediaId: String = String()
    private var currentEpisodeMediaId: String = String()


    /* Overrides onCreate from Service */
    override fun onCreate() {
        super.onCreate()
        // get instance of database
        collectionDatabase = CollectionDatabase.getInstance(application)
        // get media ids of the current episode and the episode in Up Next queue
        upNextEpisodeMediaId = PreferencesHelper.loadUpNextMediaId()
        currentEpisodeMediaId = PreferencesHelper.loadCurrentMediaId()
        // initialize player and session
        initializePlayer()
        initializeSession()
        // initializeMediaItems()
        val notificationProvider: DefaultMediaNotificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification_app_icon_white_24dp)
        setMediaNotificationProvider(notificationProvider)
    }


    /* Overrides onDestroy from Service */
    override fun onDestroy() {
        // player.removeAnalyticsListener(analyticsListener)
        handler.removeCallbacksAndMessages(null)
        player.removeListener(playerListener)
        player.release()
        com.jinqiujiang.fm.helpers.PendingAudioDeletes.flush(this, "")
        mediaSession.release()
        super.onDestroy()
    }


    /* Overrides onGetSession from MediaSessionService */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaSession
    }


    /* Initialize the player - override in implementation */
    abstract fun initializePlayer()


    /* Initializes the MediaSession */
    private fun initializeSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = TaskStackBuilder.create(this).run {
                addNextIntent(intent)
                getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        mediaSession = MediaLibrarySession.Builder(this, player, librarySessionCallback).apply {
            setSessionActivity(pendingIntent)
            setMediaButtonPreferences(createMediaButtonPreferences())
        }.build()
    }


    /* Creates the buttons shown in the media notification - replaces the system's "previous" and "next" */
    private fun createMediaButtonPreferences(): ImmutableList<CommandButton> {
        val seekBack10sCommandButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            // setIconResId is only a fallback - surfaces that know the predefined icon use their own
            .setIconResId(R.drawable.ic_notification_skip_back_36dp)
            .setDisplayName(getString(R.string.notification_skip_back))
            .build()
        val seekForward30sCommandButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setIconResId(R.drawable.ic_notification_skip_forward_36dp)
            .setDisplayName(getString(R.string.notification_skip_forward))
            .build()
        return ImmutableList.of(seekBack10sCommandButton, seekForward30sCommandButton)
    }


    /* Starts sleep timer / adds default duration to running sleeptimer */
    private fun startSleepTimer(continueWithPreviousTimerDuration: Boolean) {
        // stop running timer
        if (sleepTimerTimeRemaining > 0L && this::sleepTimer.isInitialized) {
            handler.removeCallbacks(resetSleepTimerStateRunnable)
            sleepTimer.cancel()
        }
        // initialize timer
        val duration: Long
        if (continueWithPreviousTimerDuration) {
            duration = sleepTimerTimeRemaining
        } else {
            duration = Keys.SLEEP_TIMER_DURATION + sleepTimerTimeRemaining
        }
        sleepTimer = object: CountDownTimer(duration, Keys.SLEEP_TIMER_INTERVAL) {
            override fun onFinish() {
                Log.v(TAG, "Sleep timer finished. Sweet dreams.")
                sleepTimerTimeRemaining = 0L
                sleepTimerRunning = false
                player.pause()
            }
            override fun onTick(millisUntilFinished: Long) {
                sleepTimerRunning = true
                sleepTimerTimeRemaining = millisUntilFinished
            }
        }
        // start timer
        sleepTimer.start()
        // store timer state
        PreferencesHelper.saveSleepTimerRunning(isRunning = true)
    }


    /* Cancels sleep timer */
    private fun cancelSleepTimer(delayedReset: Boolean) {
        if (this::sleepTimer.isInitialized && sleepTimerTimeRemaining > 0L) {
            if (delayedReset) {
                handler.postDelayed(resetSleepTimerStateRunnable, 2500L)
            } else {
                resetSleepTimerState()
            }
            sleepTimer.cancel()
        }
        // store timer state
        PreferencesHelper.saveSleepTimerRunning(isRunning = false)
    }


    /* Resets the state of the sleep timer */
    private fun resetSleepTimerState() {
        sleepTimerTimeRemaining = 0L
        sleepTimerRunning = false
    }


    /* Handles transition to Up Next episode */
    private fun handleTransitionToUpNext(mediaItem: MediaItem?): String {
        // remove previous episode and store its mediaId, if available
        val previousEpisodeMediaId: String = removePreviousEpisodeFromQueue()
        // save mediaId for current episode
        PreferencesHelper.saveCurrentMediaId(player.currentMediaItem?.mediaId ?: String())
        // resume playback where it was previously stopped
        val extras: Bundle? = mediaItem?.mediaMetadata?.extras
        if (extras != null) {
            val playbackPosition: Long = extras.getLong(Keys.KEY_EPISODE_PLAYBACK_POSITION, 0L)
            val duration: Long = extras.getLong(Keys.KEY_EPISODE_DURATION, 0L)
            val position: Long = if (CollectionHelper.isEpisodeFinished(playbackPosition, duration)) 0L else playbackPosition
            if (position > 0L) player.seekTo(position)
        }
        return previousEpisodeMediaId
    }


    /* Remove previous episode from queue */
    private fun removePreviousEpisodeFromQueue(): String {
        var previousEpisodeMediaId: String = String()
        if (player.hasPreviousMediaItem()) {
            previousEpisodeMediaId = player.getMediaItemAt(/* index = */ 0).mediaId
            player.removeMediaItem(/* index = */ 0)
            upNextEpisodeMediaId = String()
            PreferencesHelper.saveUpNextMediaId()
        }
        return previousEpisodeMediaId
    }


    /*
     * Runnable: Periodically saves playback position
     */
    private val periodicProgressUpdateRunnable: Runnable = object : Runnable {
        override fun run() {
            val mediaId: String = player.currentMediaItem?.mediaId ?: String()
            val position: Long =  player.currentPosition
            val isPlaying: Boolean = player.isPlaying
            val duration = player.duration
            CoroutineScope(IO).launch {
                if (duration > 0L) collectionDatabase.episodeDao().updateDuration(mediaId, duration)
                collectionDatabase.episodeDao().updatePlaybackPosition(mediaId, position, isPlaying)
            }
            // use the handler to start runnable again after specified delay
            handler.postDelayed(this, 15000)
        }
    }
    /*
     * End of declaration
     */


    /*
     * Runnable: Reset the sleep timer state
     */
    private val resetSleepTimerStateRunnable: Runnable = object : Runnable {
        override fun run() {
            resetSleepTimerState()
        }
    }
    /*
     * End of declaration
     */


    /*
     * Custom MediaSession Callback that handles player commands
     */
    private inner class CustomMediaLibrarySessionCallback: MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(CollectionHelper.getRootItem(), params))
        }

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            CoroutineScope(IO).launch {
                val children = CollectionHelper.getChildren(this@BasePlayerService, parentId, collectionDatabase)
                future.set(LibraryResult.ofItemList(children, params))
            }
            return future
        }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            CoroutineScope(IO).launch {
                val episode: Episode? = collectionDatabase.episodeDao().findByMediaId(mediaId)
                if (episode != null) {
                    val item: MediaItem = CollectionHelper.buildMediaItem(this@BasePlayerService, episode)
                    future.set(LibraryResult.ofItem(item, /* params= */ null))
                } else {
                    future.set(LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE))
                }
            }
            return future
        }

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<List<MediaItem>> {
            val future = SettableFuture.create<List<MediaItem>>()
            CoroutineScope(IO).launch {
                try {
                    val items = mediaItems.mapNotNull { requested ->
                        collectionDatabase.episodeDao().findByMediaId(requested.mediaId)
                            ?.takeIf { it.episodeRemotePodcastFeedLocation == PodcastConfig.FEED_URL }
                            ?.let { CollectionHelper.buildMediaItem(this@BasePlayerService, it) }
                    }
                    future.set(items)
                } catch (error: Exception) { future.setException(error) }
            }
            return future
        }

        override fun onConnectAsync(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.ConnectionResult> {
            // add custom commands
            val connectionResult: MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
            val builder: SessionCommands.Builder = connectionResult.availableSessionCommands.buildUpon()
            builder.add(SessionCommand(Keys.CMD_UP_NEXT_UPDATED, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_START_SLEEP_TIMER, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_RUNNING, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY))
            // let untrusted controllers (third-party widgets, launchers) play, pause and skip back / forward
            val playerCommands: Player.Commands = if (controller.isTrusted) {
                connectionResult.availablePlayerCommands
            } else {
                connectionResult.availablePlayerCommands.buildUpon().addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_FORWARD
                ).build()
            }
            return Futures.immediateFuture(MediaSession.ConnectionResult.accept(builder.build(), playerCommands.buildUpon().remove(Player.COMMAND_SET_SPEED_AND_PITCH).build()))
        }

        override fun onSubscribe(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,  parentId: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
            val future = SettableFuture.create<LibraryResult<Void>>()
            CoroutineScope(IO).launch {
                val itemCount: Int = collectionDatabase.episodeDao().getSize()
                session.notifyChildrenChanged(browser, parentId, itemCount, params)
                future.set(LibraryResult.ofVoid())
            }
            return future
        }

        override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // note onPlaybackResumption is only called if player has no media items yet
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            CoroutineScope(IO).launch {
                // create list if media items for playback resumption
                val mediaItems: MutableList<MediaItem> = mutableListOf()
                val currentEpisode: Episode? = collectionDatabase.episodeDao().findByMediaId(currentEpisodeMediaId)
                if (currentEpisode != null) {
                    mediaItems.add(CollectionHelper.buildMediaItem(this@BasePlayerService, currentEpisode))
                }
                val result: MediaSession.MediaItemsWithStartPosition = if (mediaItems.isNotEmpty()) {
                    MediaSession.MediaItemsWithStartPosition(mediaItems, 0, currentEpisode?.playbackPosition ?: C.TIME_UNSET)
                } else {
                    MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET)
                }
                future.set(result)
            }
            return future
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                Keys.CMD_UP_NEXT_UPDATED -> {
                    if (args.containsKey(Keys.EXTRA_UP_NEXT_MEDIA_ID)) {
                        upNextEpisodeMediaId = args.getString(Keys.EXTRA_UP_NEXT_MEDIA_ID) ?: String()
                    }
                }

//                Keys.CMD_ADD_TO_UP_NEXT -> {
//                    if (args.containsKey(Keys.EXTRA_UP_NEXT_MEDIA_ID)) {
//                        addToUpNext(args.getString(Keys.EXTRA_UP_NEXT_MEDIA_ID) ?: String())
//                    }
//                }
//                Keys.CMD_CLEAR_UP_NEXT -> {
//                    clearUpNext()
//                }

                Keys.CMD_START_SLEEP_TIMER -> {
                    startSleepTimer(continueWithPreviousTimerDuration = false)
                }
                Keys.CMD_CANCEL_SLEEP_TIMER -> {
                    cancelSleepTimer(delayedReset = false)
                }
                Keys.CMD_REQUEST_SLEEP_TIMER_RUNNING -> {
                    val resultBundle = Bundle()
                    resultBundle.putBoolean(Keys.EXTRA_SLEEP_TIMER_RUNNING, sleepTimerRunning)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
                }
                Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING -> {
                    val resultBundle = Bundle()
                    resultBundle.putLong(Keys.EXTRA_SLEEP_TIMER_REMAINING, sleepTimerTimeRemaining)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        /* Overrides onMediaButtonEvent from MediaSession.Callback */
        override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean {
            // emulate headphone buttons
            // next: adb shell input keyevent 87
            // prev: adb shell input keyevent 88
            val keyEvent: KeyEvent? = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            when (keyEvent?.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    // override "skip to next" command - just skip forward instead
                    if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) player.seekForward()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    // override "skip to previous" command - just skip back instead
                    if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) player.seekBack()
                    return true
                }
                else -> return super.onMediaButtonEvent(session, controllerInfo, intent)
            }
        }

        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
            // playerCommand = one of COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_SEEK_TO_DEFAULT_POSITION, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD, COMMAND_SET_SPEED_AND_PITCH, COMMAND_SET_SHUFFLE_MODE, COMMAND_SET_REPEAT_MODE, COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_MEDIA_ITEMS_METADATA, COMMAND_SET_MEDIA_ITEMS_METADATA, COMMAND_CHANGE_MEDIA_ITEMS, COMMAND_GET_AUDIO_ATTRIBUTES, COMMAND_GET_VOLUME, COMMAND_GET_DEVICE_VOLUME, COMMAND_SET_VOLUME, COMMAND_SET_DEVICE_VOLUME, COMMAND_ADJUST_DEVICE_VOLUME, COMMAND_SET_VIDEO_SURFACE, COMMAND_GET_TEXT, COMMAND_SET_TRACK_SELECTION_PARAMETERS or COMMAND_GET_TRACK_INFOS. */
            // emulate headphone buttons
            // start/pause: adb shell input keyevent 85
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    val currentEpisodeMediaId: String? = player.currentMediaItem?.mediaId
                    val currentPosition: Long = player.currentPosition
                    if (currentEpisodeMediaId != null) {
                        CoroutineScope(IO).launch {
                            collectionDatabase.episodeDao().updatePlaybackPosition(currentEpisodeMediaId, currentPosition, false)
                        }
                    }
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
                Player.COMMAND_STOP -> {
                    // todo hide notification
                    player.pause()
//                    mediaSession.player.release()
//                    mediaSession.release()
//                    stopSelf()
//                    player.clearMediaItems()
//                    stopForeground(true)
                    return SessionResult.RESULT_INFO_SKIPPED
                }
                Player.COMMAND_PLAY_PAUSE -> {
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
                else -> {
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            }
        }
    }
    /*
     * End of inner class
     */


    /*
     * Custom Player for local playback
     */
    val localPlayer: Player by lazy {
        // step 1: create the local player
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory().setUserAgent(Keys.USER_AGENT)
        val data = androidx.media3.datasource.DefaultDataSource.Factory(this, http)
        val exoPlayer: ExoPlayer = ExoPlayer.Builder(this).apply {
            setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(data))
            setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            setHandleAudioBecomingNoisy(true)
            setSeekBackIncrementMs(Keys.SKIP_BACK_TIME_SPAN)
            setSeekForwardIncrementMs(Keys.SKIP_FORWARD_TIME_SPAN)
        }.build()
        exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters.DEFAULT
        exoPlayer.addListener(playerListener)
        //exoPlayer.addAnalyticsListener(analyticsListener)
        // manually add seek to next, seek to previous, etc.
        val player = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_FORWARD)
                    .add(Player.COMMAND_SEEK_BACK)
                    .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_SET_MEDIA_ITEM)
                    .remove(Player.COMMAND_SET_SPEED_AND_PITCH)
                    .build()
            }
            override fun setPlaybackSpeed(speed: Float) { /* Fixed normal playback. */ }
            override fun setPlaybackParameters(playbackParameters: androidx.media3.common.PlaybackParameters) { /* Fixed speed and pitch. */ }
            override fun isCommandAvailable(command: Int): Boolean {
                return availableCommands.contains(command)
            }
        }
        player
    }
    /*
     * End of declaration
     */


    /*
     * Player.Listener: Called when one or more player states changed.
     */
    val playerListener: Player.Listener = object : Player.Listener {

        //        // log number of episodes in player
//        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
//            super.onTimelineChanged(timeline, reason)
//            Log.v(TAG, "Number of Episodes in player: ${player.mediaItemCount}")
//        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Log.e(TAG, "Playback error: ${error.errorCodeName}", error)
            // surface a user-visible error - a malformed/undecodable episode otherwise fails silently
            Toast.makeText(this@BasePlayerService, R.string.toast_message_error_playback_failed, Toast.LENGTH_LONG).show()
            // Retain the item and position so the next play tap can prepare it again.
            player.playWhenReady = false
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            com.jinqiujiang.fm.helpers.PendingAudioDeletes.flush(this@BasePlayerService, mediaItem?.mediaId ?: "")
            super.onMediaItemTransition(mediaItem, reason)

            // check if Up Next episode started
            when (reason) {
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
                    val previousEpisodeMediaId: String = handleTransitionToUpNext(mediaItem)
                    // mark previously played episode as played
                    CoroutineScope(IO).launch {
                        collectionDatabase.episodeDao().markPlayed(previousEpisodeMediaId)
                    }
                }
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> {
                    handleTransitionToUpNext(mediaItem)
                    // playback position of previous episode is stored in onPlayerCommandRequest (COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                }
                else -> {
                    // do nothing
                }
            }
//            // Log transition reasons
//            when (reason) {
//                // media item has been repeated
//                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> Log.d(TAG, "0 MEDIA_ITEM_TRANSITION_REASON_REPEAT - count: ${player.mediaItemCount} // item: ${mediaItem?.mediaMetadata?.title} ") // todo remove
//                // playback automatically transitioned to next media item (previous episode finished and Up Next episode started automatically)
//                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> Log.d(TAG, "1 MEDIA_ITEM_TRANSITION_REASON_AUTO - count: ${player.mediaItemCount} // item: ${mediaItem?.mediaMetadata?.title} ") // todo remove
//                // seek to another media item has occurred
//                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> Log.d(TAG, "2 MEDIA_ITEM_TRANSITION_REASON_SEEK - count: ${player.mediaItemCount} // item: ${mediaItem?.mediaMetadata?.title} ") // todo remove
//                // current media item has changed because of a change in the playlist - a new media item was started
//                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> Log.d(TAG, "3 MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED - count: ${player.mediaItemCount} // item: ${mediaItem?.mediaMetadata?.title} ") // todo remove
//            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            val mediaId: String = player.currentMediaItem?.mediaId ?: String()
            val currentPosition: Long = player.currentPosition
            // store state of playback
            PreferencesHelper.saveIsPlaying(isPlaying)
            PreferencesHelper.saveCurrentMediaId(mediaId)
            // check if  user has started the episode that is in the up next queue
            if (mediaId == upNextEpisodeMediaId) {
                removePreviousEpisodeFromQueue()
            }
            // save playback state of episode to database
            CoroutineScope(IO).launch {
                collectionDatabase.episodeDao().updatePlaybackPosition(mediaId, currentPosition, isPlaying)
            }

            if (isPlaying) {
                // playback is active - start to periodically save the playback position to database
                handler.removeCallbacks(periodicProgressUpdateRunnable)
                handler.postDelayed(periodicProgressUpdateRunnable, 0)
                // restart the sleep time if there is still time on the clock
                if (sleepTimerTimeRemaining > 0) startSleepTimer(continueWithPreviousTimerDuration = true)
                // add the Up Next episode to the queue - used when playback was started by the system not the user
                if (player.mediaItemCount == 1 && player.currentMediaItem?.mediaId != upNextEpisodeMediaId && upNextEpisodeMediaId.isNotEmpty()) {
                    CoroutineScope(IO).launch {
                        val upNextEpisode: Episode? = collectionDatabase.episodeDao().findByMediaId(upNextEpisodeMediaId)
                        withContext(Main) {
                            if (upNextEpisode != null) {
                                player.addMediaItem(1, CollectionHelper.buildMediaItem(this@BasePlayerService, upNextEpisode))
                            }
                        }
                    }
                }
            } else {
                // playback is not active - stop periodically saving the playback position to database
                handler.removeCallbacks(periodicProgressUpdateRunnable)

                // cancel sleep timer
                cancelSleepTimer(delayedReset = true)

                // Not playing because playback is paused, ended, suppressed, or the player
                // is buffering, stopped or failed. Check player.getPlayWhenReady,
                // player.getPlaybackState, player.getPlaybackSuppressionReason and
                // player.getPlaybackError for details.
                when (player.playbackState) {
                    // player is able to immediately play from its current position
                    Player.STATE_READY -> {
                        // todo
                    }
                    // buffering - data needs to be loaded
                    Player.STATE_BUFFERING -> {
                        // todo
                    }
                    // player finished playing all media
                    Player.STATE_ENDED -> {
                        player.stop()
                        player.removeMediaItem(0)
                    }
                    // initial state or player is stopped or playback failed
                    Player.STATE_IDLE -> {
                        // todo
                    }
                }
            }
        }
    }
    /*
     * End of declaration
     */


    /*
     * Custom AnalyticsListener that enables AudioFX equalizer integration
     */
    private val analyticsListener = object: AnalyticsListener {
        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            super.onAudioSessionIdChanged(eventTime, audioSessionId)
            // integrate with system equalizer (AudioFX)
            val intent: Intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_VOICE)
            sendBroadcast(intent)
            // note: remember to broadcast AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, when not needed anymore
        }
    }
    /*
     * End of declaration
     */


}