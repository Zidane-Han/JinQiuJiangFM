/*
 * MediaControllerExt.kt
 * Implements MediaControllerExt extension methods
 * Useful extension methods for MediaController
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.extensions

import android.content.Context
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import org.y20k.escapepod.Keys
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.helpers.CollectionHelper
import org.y20k.escapepod.helpers.PreferencesHelper


private val TAG: String = "MediaControllerExt"


/* Starts the sleep timer */
fun MediaController.startSleepTimer() {
    sendCustomCommand(SessionCommand(Keys.CMD_START_SLEEP_TIMER, Bundle.EMPTY), Bundle.EMPTY)
}


/* Cancels the sleep timer */
fun MediaController.cancelSleepTimer() {
    sendCustomCommand(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY), Bundle.EMPTY)
}


/* Request sleep timer state */
fun MediaController.requestSleepTimerRunning(): ListenableFuture<SessionResult> {
    return sendCustomCommand(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_RUNNING, Bundle.EMPTY), Bundle.EMPTY)
}


/* Request sleep timer remaining */
fun MediaController.requestSleepTimerRemaining(): ListenableFuture<SessionResult> {
    return sendCustomCommand(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY), Bundle.EMPTY)
}


/* Add episode to Up Next */
fun MediaController.addToUpNext(context: Context, episode: Episode): ListenableFuture<SessionResult> {
    if (mediaItemCount > 1) {
        // replace existing Up Next item
        replaceMediaItem(1, CollectionHelper.buildMediaItem(context, episode))
    } else {
        // add new Up Next item
        addMediaItem(1, CollectionHelper.buildMediaItem(context, episode))
    }
    // save Up Next state
    PreferencesHelper.saveUpNextMediaId(episode.mediaId)
    val args = Bundle().apply { putString(Keys.EXTRA_UP_NEXT_MEDIA_ID, episode.mediaId) }
    return sendCustomCommand(SessionCommand(Keys.CMD_UP_NEXT_UPDATED, Bundle.EMPTY), args)
}


/* Clears the Up Next queue */
fun MediaController.clearUpNext(): ListenableFuture<SessionResult> {
    // remove media items
    if (mediaItemCount > 1) {
        removeMediaItems(1, mediaItemCount)
    }
    // save Up Next state
    PreferencesHelper.saveUpNextMediaId()
    // send update
    val args = Bundle().apply { putString(Keys.EXTRA_UP_NEXT_MEDIA_ID, String()) }
    return sendCustomCommand(SessionCommand(Keys.CMD_UP_NEXT_UPDATED, Bundle.EMPTY), args)
}


/* Continue playback */
fun MediaController.continuePlayback() {
    // if episode is finished / almost finished (1/2 second before the end), then continue from start of episode
    if (currentPosition >= duration - 500L) seekTo(0L)
    playWhenReady = true
}


/* Starts playback with a new media item */
fun MediaController.play(context: Context, episode: Episode, upNextEpisode: Episode? = null) {
    if (isPlaying) pause()
    // set media item, prepare and play
    val position: Long = if (episode.isFinished()) 0L else episode.playbackPosition
    setMediaItem(CollectionHelper.buildMediaItem(context, episode), position)
    // add Up Next media item
    if (upNextEpisode != null) {
        addToUpNext(context, upNextEpisode)
    }
    prepare()
    playWhenReady = true
}


/* Starts playback for Up Next episode */
fun MediaController.startUpNextEpisode() {
    if (hasNextMediaItem()) {
        seekToNextMediaItem()
        if (!isPlaying) play()
        // PlayerService.handleTransitionToUpNext() sets the correct start position
    }
}


/* Change playback speed */
fun MediaController.changePlaybackSpeed(): Float {
    var newSpeed: Float = 1f
    // circle through the speed presets
    val iterator = Keys.PLAYBACK_SPEEDS.iterator()
    while (iterator.hasNext()) {
        // found current speed in array
        if (iterator.next() == playbackParameters.speed) {
            if (iterator.hasNext()) {
                newSpeed = iterator.next()
            }
            break
        }
    }
    // apply new speed and save playback state
    setPlaybackSpeed(newSpeed)
    PreferencesHelper.savePlayerPlaybackSpeed(newSpeed)
    return newSpeed
}


/* Reset playback speed */
fun MediaController.resetPlaybackSpeed(): Float {
    val newSpeed: Float = 1f
    // reset playback speed and save playback state
    setPlaybackSpeed(newSpeed)
    PreferencesHelper.savePlayerPlaybackSpeed(newSpeed)
    return newSpeed
}


/* Returns mediaId of currently active media item */
fun MediaController.getCurrentMediaId(): String {
    if (mediaItemCount > 0) {
        return getMediaItemAt(0).mediaId
    } else {
        return String()
    }
}


/* Returns mediaId of currently active media item */
fun MediaController.getNextMediaId(): String {
    if (mediaItemCount > 1) {
        return getMediaItemAt(1).mediaId
    } else {
        return String()
    }
}


/* Returns if controller/player has one or more media items  */
fun MediaController.hasMediaItems(): Boolean {
    return mediaItemCount > 0
}
