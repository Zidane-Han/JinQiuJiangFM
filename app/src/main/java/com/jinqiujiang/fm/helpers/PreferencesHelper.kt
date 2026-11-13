/*
 * PreferencesHelper.kt
 * Implements the PreferencesHelper object
 * A PreferencesHelper provides helper methods for the saving and loading values from shared preferences
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
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.jinqiujiang.fm.Keys
import com.jinqiujiang.fm.R
import com.jinqiujiang.fm.ui.PlayerState
import java.util.Calendar
import java.util.Date


/*
 * PreferencesHelper object
 */
object PreferencesHelper {

    /* The sharedPreferences object to be initialized */
    private lateinit var sharedPreferences: SharedPreferences

    /* Initialize a single sharedPreferences object when the app is launched */
    fun Context.initPreferences() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    /* Define log tag */
    private val TAG: String = PreferencesHelper::class.java.simpleName


    /* Loads mediaId of current episode from shared preferences */
    fun loadCurrentMediaId(): String {
        return sharedPreferences.getString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String()) ?: String()
    }


    /* Saves mediaId of current episode to shared preferences */
    fun saveCurrentMediaId(mediaId: String = String()) {
        sharedPreferences.edit {
            putString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, mediaId)
        }
    }


    /* Loads mediaId of next episode in Up Next queue from shared preferences */
    fun loadUpNextMediaId(): String {
        return sharedPreferences.getString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, String()) ?: String()
    }


    /* Saves mediaId of next episode in Up Next queue  to shared preferences */
    fun saveUpNextMediaId(mediaId: String = String()) {
        sharedPreferences.edit {
            putString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, mediaId)
        }
    }


    /* Load feed location of the podcast in the podcast list which is currently expanded */
    fun loadPodcastListExpandedFeedLocation(): String {
        return sharedPreferences.getString(Keys.PREF_PODCAST_LIST_EXPANDED_FEED_LOCATION, String()) ?: String()
    }


    /* Save feed location of the podcast in the podcast list which is currently expanded */
    fun savePodcastListExpandedFeedLocation(podcastFeedLocation: String = String()) {
        sharedPreferences.edit {
            putString(Keys.PREF_PODCAST_LIST_EXPANDED_FEED_LOCATION, podcastFeedLocation)
        }
    }


    /* Loads keepDebugLog true or false */
    fun loadKeepDebugLog(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_KEEP_DEBUG_LOG, false)
    }


    /* Saves keepDebugLog true or false */
    fun saveKeepDebugLog(keepDebugLog: Boolean = false) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_KEEP_DEBUG_LOG, keepDebugLog)
        }
    }


    /* Loads state of playback for PlayerService from shared preferences */
    fun loadPlayerPlaybackState(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_PLAYER_STATE_IS_PLAYING, false)
    }


    /* Saves state of playback for player to shared preferences */
    fun saveIsPlaying(isPlaying: Boolean) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_PLAYER_STATE_IS_PLAYING, isPlaying)
        }
    }


    fun clearRetiredPreferences() {
        sharedPreferences.edit {
            remove(Keys.PREF_PLAYER_STATE_PLAYBACK_SPEED)
            // Upgrade-only cleanup: these settings no longer have runtime consumers.
            remove("TAP_ANYWHERE_PLAYBACK")
            remove("PODCAST_SEARCH_PROVIDER")
            remove("ONE_TIME_HOUSEKEEPING_NECESSARY_VERSIONCODE_46")
        }
    }

    /* Loads state sleep timer from shared preferences */
    fun loadSleepTimerRunning(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING, false)
    }


    /* Saves state of sleep timer to shared preferences */
    fun saveSleepTimerRunning(isRunning: Boolean) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING, isRunning)
        }
    }

    /* Loads last update from shared preferences */
    fun loadLastUpdateCollection(): Date {
        val lastSaveString: String = sharedPreferences.getString(Keys.PREF_LAST_UPDATE_COLLECTION, "") ?: String()
        return DateTimeHelper.convertFromRfc2822(lastSaveString)
    }


    /* Saves last update to shared preferences */
    fun saveLastUpdateCollection(lastUpdate: Date = Calendar.getInstance().time) {
        sharedPreferences.edit {
            putString(Keys.PREF_LAST_UPDATE_COLLECTION, DateTimeHelper.convertToRfc2822(lastUpdate))
        }
    }


    /* Loads size of collection from shared preferences */
    fun loadCollectionSize(): Int {
        return sharedPreferences.getInt(Keys.PREF_COLLECTION_SIZE, -1)
    }


    /* Saves site of collection to shared preferences */
    fun saveCollectionSize(size: Int) {
        sharedPreferences.edit {
            putInt(Keys.PREF_COLLECTION_SIZE, size)
        }
    }


    /* Loads date of last save operation from shared preferences */
    fun loadCollectionModificationDate(): Date {
        val modificationDateString: String = sharedPreferences.getString(Keys.PREF_COLLECTION_MODIFICATION_DATE, "") ?: String()
        return DateTimeHelper.convertFromRfc2822(modificationDateString)
    }


    /* Saves date of last save operation to shared preferences */
    fun saveCollectionModificationDate(lastSave: Date = Calendar.getInstance().time) {
        sharedPreferences.edit {
            putString(Keys.PREF_COLLECTION_MODIFICATION_DATE, DateTimeHelper.convertToRfc2822(lastSave))
        }
    }


    /* Returns if background refresh is allowed */
    fun loadBackgroundRefresh(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_BACKGROUND_REFRESH, Keys.DEFAULT_BACKGROUND_REFRESH_MODE)
    }


    /* Returns if download over mobile network is allowed */
    fun loadEpisodeDownloadOverMobile(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_EPISODE_DOWNLOAD_OVER_MOBILE, Keys.DEFAULT_EPISODE_DOWNLOAD_OVER_MOBILE_MODE)
    }


    /* Returns if auto cleanup of downloaded episodes is enabled */
    fun loadDownloadAutoCleanupEnabled(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_DOWNLOAD_AUTO_CLEANUP_ENABLED, Keys.DEFAULT_DOWNLOAD_AUTO_CLEANUP_ENABLED)
    }


    /* Loads active downloads from shared preferences */
    fun loadActiveDownloads(): String {
        val activeDownloadsString: String = sharedPreferences.getString(Keys.PREF_ACTIVE_DOWNLOADS, Keys.ACTIVE_DOWNLOADS_EMPTY) ?: Keys.ACTIVE_DOWNLOADS_EMPTY
        Log.v(TAG, "IDs of active downloads: $activeDownloadsString")
        return activeDownloadsString
    }


    /* Return if active downloads is empty */
    fun isActiveDownloadsEmpty(): Boolean {
        return sharedPreferences.getString(Keys.PREF_ACTIVE_DOWNLOADS, Keys.ACTIVE_DOWNLOADS_EMPTY) == Keys.ACTIVE_DOWNLOADS_EMPTY
    }


    /* Saves active downloads to shared preferences */
    fun saveActiveDownloads(activeDownloadsString: String = String()) {
        sharedPreferences.edit {
            putString(Keys.PREF_ACTIVE_DOWNLOADS, activeDownloadsString)
        }
    }


    /* Loads state of player user interface from shared preferences */
    fun loadPlayerState(): PlayerState {
        return PlayerState().apply {
            currentEpisodeMediaId = sharedPreferences.getString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String()) ?: String()
            isPlaying = sharedPreferences.getBoolean(Keys.PREF_PLAYER_STATE_IS_PLAYING, false)
            sleepTimerRunning = sharedPreferences.getBoolean(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING, false)
            upNextEpisodeMediaId = sharedPreferences.getString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, String()) ?: String()
        }
    }


    /* Saves state of player user interface to shared preferences */
    fun savePlayerState(playerState: PlayerState) {
        sharedPreferences.edit {
            putString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, playerState.currentEpisodeMediaId)
            putBoolean(Keys.PREF_PLAYER_STATE_IS_PLAYING, playerState.isPlaying)
            putBoolean(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING, playerState.sleepTimerRunning)
            putString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, playerState.upNextEpisodeMediaId)
        }
    }


    /* Resets state of player user interface */
    fun resetPlayerState(keepUpNextMediaId: Boolean = true) {
        sharedPreferences.edit {
            putString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String())
            putBoolean(Keys.PREF_PLAYER_STATE_IS_PLAYING, false)
            putBoolean(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING, false)
            // reset Up Next only if requested
            if (!keepUpNextMediaId) putString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, String())
        }
    }


    /* Start watching for changes in shared preferences - context must implement OnSharedPreferenceChangeListener */
    fun registerPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }


    /* Stop watching for changes in shared preferences - context must implement OnSharedPreferenceChangeListener */
    fun unregisterPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }


    /* Load currently selected app theme */
    fun loadThemeSelection(): String {
        return sharedPreferences.getString(Keys.PREF_THEME_SELECTION, Keys.STATE_THEME_FOLLOW_SYSTEM) ?: Keys.STATE_THEME_FOLLOW_SYSTEM
    }


    /* Returns a readable String for currently selected App Theme */
    fun getCurrentTheme(context: Context): String {
        return when (loadThemeSelection()) {
            Keys.STATE_THEME_LIGHT_MODE -> context.getString(R.string.pref_theme_selection_mode_light)
            Keys.STATE_THEME_DARK_MODE -> context.getString(R.string.pref_theme_selection_mode_dark)
            else -> context.getString(R.string.pref_theme_selection_mode_device_default)
        }
    }

    /* Loads value of the option: Dynamic Colors */
    fun loadDynamicColorsEnabled(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_DYNAMIC_COLORS, false)
    }


    /* Saves value of the option: Dynamic Colors */
    fun saveDynamicColorsEnabled(enabled: Boolean = true) {
       sharedPreferences.edit {
            putBoolean(Keys.PREF_DYNAMIC_COLORS, enabled)
        }
    }


    /* Loads value of the option: UI Transparency Effect */
    fun loadUserInterfaceTransparencyEffect(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_USER_INTERFACE_TRANSPARENCY_EFFECT, false)
    }


    /* Saves value of the option: UI Transparency Effect */
    fun saveUserInterfaceTransparencyEffect(enabled: Boolean = false) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_USER_INTERFACE_TRANSPARENCY_EFFECT, enabled)
        }
    }


    /* Return the set number of audio files to keep */
    fun numberOfAudioFilesToKeep(): Int {
        return sharedPreferences.getInt(
            Keys.PREF_NUMBER_OF_AUDIO_FILES_TO_KEEP, Keys.DEFAULT_NUMBER_OF_AUDIO_FILES_TO_KEEP
        )
    }
}
