/*
 * BaseMainActivity.kt
 * Implements the BaseMainActivity class
 * The BaseMainActivity hosts the MainFragment, the PodcastFragment and the SettingsFragment
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package org.y20k.escapepod

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.google.android.material.slider.Slider
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.y20k.escapepod.collection.CollectionViewModel
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.dialogs.ErrorDialog
import org.y20k.escapepod.dialogs.FindPodcastDialog
import org.y20k.escapepod.dialogs.OpmlImportDialog
import org.y20k.escapepod.dialogs.YesNoDialog
import org.y20k.escapepod.extensions.addToUpNext
import org.y20k.escapepod.extensions.cancelSleepTimer
import org.y20k.escapepod.extensions.changePlaybackSpeed
import org.y20k.escapepod.extensions.clearUpNext
import org.y20k.escapepod.extensions.continuePlayback
import org.y20k.escapepod.extensions.getCurrentMediaId
import org.y20k.escapepod.extensions.getNextMediaId
import org.y20k.escapepod.extensions.hasMediaItems
import org.y20k.escapepod.extensions.play
import org.y20k.escapepod.extensions.requestSleepTimerRemaining
import org.y20k.escapepod.extensions.requestSleepTimerRunning
import org.y20k.escapepod.extensions.resetPlaybackSpeed
import org.y20k.escapepod.extensions.startSleepTimer
import org.y20k.escapepod.extensions.startUpNextEpisode
import org.y20k.escapepod.helpers.AppThemeHelper
import org.y20k.escapepod.helpers.CollectionHelper
import org.y20k.escapepod.helpers.DownloadHelper
import org.y20k.escapepod.helpers.FileHelper
import org.y20k.escapepod.helpers.NetworkHelper
import org.y20k.escapepod.helpers.PreferencesHelper
import org.y20k.escapepod.helpers.WorkerHelper
import org.y20k.escapepod.interfaces.FindPodcastDialogListener
import org.y20k.escapepod.interfaces.OpmlImportDialogListener
import org.y20k.escapepod.interfaces.PodcastListener
import org.y20k.escapepod.interfaces.YesNoDialogListener
import org.y20k.escapepod.ui.MainActivityLayoutHolder
import org.y20k.escapepod.ui.PlayerState
import org.y20k.escapepod.xml.OpmlHelper

abstract class BaseMainActivity : AppCompatActivity(), PodcastListener, FindPodcastDialogListener, OpmlImportDialogListener, SharedPreferences.OnSharedPreferenceChangeListener, YesNoDialogListener {

    /* Define log tag */
    private val TAG: String = BaseMainActivity::class.java.simpleName


    /* Main class variables */
    lateinit var layout: MainActivityLayoutHolder
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var collectionDatabase: CollectionDatabase
    private lateinit var collectionViewModel: CollectionViewModel
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    var playerState: PlayerState = PlayerState()
    val controller: MediaController? get() = if (controllerFuture.isDone) controllerFuture.get() else null // defines the Getter for the MediaController
    private val handler: Handler = Handler(Looper.getMainLooper())


    /* Overrides onCreate from AppCompatActivity */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // get instance of database
        collectionDatabase = CollectionDatabase.getInstance(this)

        // create view model and observe changes in collection view model
        collectionViewModel = ViewModelProvider(this)[CollectionViewModel::class.java]

        // set up views
        setContentView(R.layout.activity_main)

        // find views and set them up
        val rootView: View = findViewById(R.id.root_view)
        layout = MainActivityLayoutHolder(this, rootView, collectionDatabase)
        initializeViews()

        // load player state
        playerState = PreferencesHelper.loadPlayerState()

        // start worker that periodically updates the podcast collection
        WorkerHelper.schedulePeriodicUpdateWorker(this)

        // create .nomedia file - if not yet existing
        FileHelper.createNomediaFile(getExternalFilesDir(null))

        // register listener for changes in shared preferences
        PreferencesHelper.registerPreferenceChangeListener(sharedPreferenceChangeListener)


        // custom back press handling
        val onBackPressedCallback = object : OnBackPressedCallback(true /* enabled by default */) {
            override fun handleOnBackPressed() {
                if (!layout.hidePlayerExtendedViewsIfVisible()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // housekeeping for v1.1.1: reset the default cover
        if (PreferencesHelper.isHouseKeepingNecessary()) {
            CoroutineScope(IO).launch {
                DownloadHelper.updateCovers(this@BaseMainActivity)
//                collectionDatabase.episodeDao().resetDefaultCover(Keys.LOCATION_DEFAULT_COVER)
//                collectionDatabase.podcastDao().resetDefaultCover(Keys.LOCATION_DEFAULT_COVER)
            }
            PreferencesHelper.saveHouseKeepingNecessaryState()
        }
    }


    /* Overrides onSupportNavigateUp from AppCompatActivity */
    override fun onSupportNavigateUp(): Boolean {
        // Taken from: https://developer.android.com/guide/navigation/navigation-ui#action_bar
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.main_host_container) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }


    /* Overrides onStart from AppCompatActivity */
    override fun onStart() {
        super.onStart()
        // initialize MediaController - connect to PlayerService
        initializeController()
    }


    /* Overrides onResume from AppCompatActivity */
    override fun onResume() {
        super.onResume()
        // assign volume buttons to music volume
        volumeControlStream = AudioManager.STREAM_MUSIC
        // load player state
        playerState = PreferencesHelper.loadPlayerState()
        // recreate player ui
        layout.togglePlayButtons(playerState.isPlaying)
        setupPlaybackControls()
        updatePlayerViews(checkEpisodeIsFinished = true)
        updateUpNext(playerState.upNextEpisodeMediaId, forceUpdate = true)
        // show/hide download progress indicator
        layout.toggleDownloadProgressIndicator()
        // drop downloads that will never finish - then show/hide the indicator again
        CoroutineScope(IO).launch {
            DownloadHelper.reconcileActiveDownloads(this@BaseMainActivity)
            withContext(Main) { layout.toggleDownloadProgressIndicator() }
        }
        // begin looking for changes in podcast list
        observeListViewModel()
        // handle start intent - if started via tap on rss link
        handleStartIntent()
        // start watching for changes in shared preferences
        PreferencesHelper.registerPreferenceChangeListener(this as SharedPreferences.OnSharedPreferenceChangeListener)
    }


    /* Overrides onPause from AppCompatActivity */
    override fun onPause() {
        super.onPause()
        // reset player transparency in case a scroll was still in progress
        layout.resetPlayerTransparency()
        // stop receiving playback progress updates
        handler.removeCallbacks(periodicProgressUpdateRequestRunnable)
        // stop watching for changes in shared preferences
        PreferencesHelper.unregisterPreferenceChangeListener(this as SharedPreferences.OnSharedPreferenceChangeListener)
    }


    /* Overrides onStop from AppCompatActivity */
    override fun onStop() {
        super.onStop()
        // release MediaController - cut connection to PlayerService
        releaseController()
    }


    /* Overrides onDestroy from AppCompatActivity */
    override fun onDestroy() {
        super.onDestroy()
        // unregister listener for changes in shared preferences
        // PreferencesHelper.unregisterPreferenceChangeListener(sharedPreferenceChangeListener) // todo remove
    }



    /* Overrides onSharedPreferenceChanged from OnSharedPreferenceChangeListener */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Keys.PREF_ACTIVE_DOWNLOADS -> {
                layout.toggleDownloadProgressIndicator()
            }
            Keys.PREF_USER_INTERFACE_TRANSPARENCY_EFFECT -> {
                layout.userInterfaceTransparencyEffectActive = PreferencesHelper.loadUserInterfaceTransparencyEffect()
            }
        }
    }


    /* Overrides onYesNoDialog from YesNoDialogListener */
    override fun onYesNoDialog(type: Int, dialogResult: Boolean, payload: Int, payloadString: String, dialogCancelled: Boolean) {
        when (type) {
            // handle mark episode played dialog
            Keys.DIALOG_MARK_EPISODE_PLAYED -> {
                if (dialogResult) markEpisodePlayed(payloadString)
            }
            // handle delete episode dialog
            Keys.DIALOG_DELETE_EPISODE -> {
                if (dialogResult) deleteEpisode(payloadString)
            }
            // handle download episode without wifi dialog
            Keys.DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI -> {
                if (dialogResult) {
                    Toast.makeText(this, R.string.toast_message_downloading_episode, Toast.LENGTH_LONG).show()
                    DownloadHelper.downloadEpisode(this, payloadString, ignoreWifiRestriction = true, manuallyDownloaded = true)
                }
            }
            // handle download all episodes dialog
            Keys.DIALOG_DOWNLOAD_ALL_EPISODES -> {
                if (dialogResult) {
                    Toast.makeText(this, R.string.toast_message_downloading_all_episodes, Toast.LENGTH_LONG).show()
                    // todo implement download all episodes
                }
            }
            // handle add up next dialog
            Keys.DIALOG_ADD_UP_NEXT -> {
                // check if result contains a media id (payloadString)
                if (payloadString.isNotEmpty()) {
                    CoroutineScope(IO).launch {
                        // get episode from media id
                        // set media item, prepare and play
                        val episode: Episode? = collectionDatabase.episodeDao().findByMediaId(payloadString)
                        if (episode != null) {
                            // check if user chose to start the episode right away
                            if (dialogResult) {
                                // user tapped: start playback
                                withContext(Main) { controller?.play(this@BaseMainActivity, episode) }
                            } else {
                                // user tapped: add to Up Next queue (only if dialog has not been cancelled)
                                withContext(Main) { controller?.addToUpNext(this@BaseMainActivity, episode) }
                            }
                        }
                    }
                }
            }
        }
    }


    /* Overrides onFindPodcastDialog from FindPodcastDialog */
    override fun onFindPodcastDialog(remotePodcastFeedLocation: String) {
        // try to add podcast
        val podcastUrl: String = remotePodcastFeedLocation.trim()
        downloadPodcastFeed(podcastUrl)
    }


    /* Overrides onOpmlImportDialog from OpmlImportDialogListener */
    override fun onOpmlImportDialog(feedUrls: Array<String>) {
        downloadPodcastFeedsFromOpml(feedUrls)
    }


    /* Overrides onPlayButtonTapped from PodcastListener */
    override fun onPlayButtonTapped(selectedEpisode: Episode) {
        val selectedEpisodeMediaId: String = selectedEpisode.mediaId
        when (controller?.isPlaying) {
            // CASE: Playback is active
            true -> {
                when (selectedEpisodeMediaId) {
                    // CASE: Selected episode is currently in player
                    playerState.currentEpisodeMediaId -> {
                        // pause playback
                        controller?.pause()
                    }
                    // CASE: selected episode is already in the Up Next queue
                    playerState.upNextEpisodeMediaId -> {
                        // start playback of Up Next
                        handleStartPlayback(selectedEpisode)
                    }
                    // CASE: tapped on episode that is not the current one and not the Up Next one
                    else -> {
                        // ask user: playback or add to Up Next
                        val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_add_up_next)}\n\n- ${selectedEpisode.title}"
                        YesNoDialog(this as YesNoDialogListener).show(context = this, type = Keys.DIALOG_ADD_UP_NEXT, messageString = dialogMessage, yesButton = R.string.dialog_yes_no_positive_button_add_up_next, noButton = R.string.dialog_yes_no_negative_button_add_up_next, payloadString = selectedEpisode.mediaId)
                    }
                }

            }
            // CASE: Playback is NOT active
            else -> {
                // start playback
                handleStartPlayback(selectedEpisode)
            }
        }
    }


    /* Overrides onMarkListenedButtonTapped from PodcastListener */
    override fun onMarkListenedButtonTapped(selectedEpisode: Episode) {
        // if (playerState.currentEpisodeMediaId == selectedEpisode.mediaId) controller?.pause()
        val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_mark_episode_played)}\n\n- ${selectedEpisode.title}"
        YesNoDialog(this as YesNoDialogListener).show(context = this, type = Keys.DIALOG_MARK_EPISODE_PLAYED, messageString = dialogMessage, yesButton = R.string.dialog_yes_no_positive_button_mark_episode_played, noButton = R.string.dialog_yes_no_negative_button_cancel, payloadString = selectedEpisode.mediaId)
    }


    /* Overrides onDownloadButtonTapped from PodcastListener */
    override fun onDownloadButtonTapped(selectedEpisode: Episode) {
        downloadEpisode(selectedEpisode.mediaId)
    }


    /* Overrides onDownloadAllButtonTapped from PodcastListener */
    override fun onDownloadAllButtonTapped(episodeRemotePodcastFeedLocation: String) {
        // YesNoDialog(this).show(context = this, type = Keys.DIALOG_DOWNLOAD_ALL_EPISODES, messageString = getString(R.string.dialog_yes_no_message_download_all_episodes), payloadString = episodeRemotePodcastFeedLocation)
    }


    /* Overrides onDeleteButtonTapped from PodcastListener */
    override fun onDeleteButtonTapped(selectedEpisode: Episode) {
        // if (playerState.currentEpisodeMediaId == selectedEpisode.mediaId) controller?.pause() // todo implement controller and player state
        val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_delete_episode)}\n\n- ${selectedEpisode.title}"
        YesNoDialog(this).show(context = this, type = Keys.DIALOG_DELETE_EPISODE, messageString = dialogMessage, yesButton = R.string.dialog_yes_no_positive_button_delete_episode, payloadString = selectedEpisode.mediaId)
    }


    /* Overrides onPodcastCardTapped from PodcastListener */
    override fun onPodcastCardTapped() {
        layout.hidePlayerExtendedViewsIfVisible()
    }


    /* Overrides onAddNewButtonTapped from PodcastListener */
    override fun onAddNewButtonTapped() {
        FindPodcastDialog(this, this as FindPodcastDialogListener).show()
    }


    /* Download podcast feed using (async) coroutine */
    private fun downloadPodcastFeed(feedUrl: String) {
        if (!NetworkHelper.isAllowedScheme(feedUrl)) {
            ErrorDialog().show(this, R.string.dialog_error_title_podcast_invalid_feed, R.string.dialog_error_message_podcast_invalid_feed, feedUrl)
        } else if (!NetworkHelper.isConnectedToNetwork(this)) {
            ErrorDialog().show(this, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        } else {
            CoroutineScope(IO).launch {
                val existingPodcast: Podcast? = collectionDatabase.podcastDao().findByRemotePodcastFeedLocation(feedUrl)
                if (existingPodcast != null) {
                    // not adding podcast, because podcast is duplicate
                    withContext(Main) { ErrorDialog().show(this@BaseMainActivity, R.string.dialog_error_title_podcast_duplicate, R.string.dialog_error_message_podcast_duplicate, feedUrl) }
                } else {
                    // detect content type
                    val contentType: NetworkHelper.ContentType = NetworkHelper.detectContentType(feedUrl)
                    if ((contentType.type !in Keys.MIME_TYPES_RSS) && contentType.type !in Keys.MIME_TYPES_ATOM) {
                        withContext(Main) { ErrorDialog().show(this@BaseMainActivity, R.string.dialog_error_title_podcast_invalid_feed, R.string.dialog_error_message_podcast_invalid_feed, feedUrl) }
                    } else {
                        withContext(Main) { Toast.makeText(this@BaseMainActivity, R.string.toast_message_adding_podcast, Toast.LENGTH_LONG).show() }
                        DownloadHelper.downloadPodcasts(this@BaseMainActivity, arrayOf(feedUrl))
                    }
                }
            }
        }
    }


    /* Download an episode podcast collection */
    private fun downloadEpisode(episodeMediaId: String) {
        if (NetworkHelper.isConnectedToWifi(this)) {
            Toast.makeText(this, R.string.toast_message_downloading_episode, Toast.LENGTH_LONG).show()
            DownloadHelper.downloadEpisode(this, episodeMediaId, ignoreWifiRestriction = true, manuallyDownloaded = true)
        } else if (NetworkHelper.isConnectedToCellular(this) && PreferencesHelper.loadEpisodeDownloadOverMobile()) {
            Toast.makeText(this, R.string.toast_message_downloading_episode, Toast.LENGTH_LONG).show()
            DownloadHelper.downloadEpisode(this, episodeMediaId, ignoreWifiRestriction = true, manuallyDownloaded = true)
        } else if (NetworkHelper.isConnectedToCellular(this)) {
            YesNoDialog(this).show(context = this, type = Keys.DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI, message = R.string.dialog_yes_no_message_non_wifi_download, yesButton = R.string.dialog_yes_no_positive_button_non_wifi_download, payloadString = episodeMediaId)
        } else if (NetworkHelper.isConnectedToVpn(this))  {
            YesNoDialog(this).show(context = this, type = Keys.DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI, message = R.string.dialog_yes_no_message_vpn_download, yesButton = R.string.dialog_yes_no_positive_button_vpn_download, payloadString = episodeMediaId)
        } else {
            ErrorDialog().show(this, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Remove local audio reference within an episode */ // todo move to helper
    private fun deleteEpisode(mediaId: String) {
        Log.v(TAG, "Deleting audio file for episode: $mediaId")
        CoroutineScope(IO).launch {
            val episode: Episode? = collectionDatabase.episodeDao().findByMediaId(mediaId)
            if (episode != null) {
                // update episode (remove audio reference and mark as not downloaded)
                collectionDatabase.episodeDao().update(CollectionHelper.deleteEpisodeAudioFile(episode = episode, manuallyDeleted = true))
                // update media id in player state if necessary
                if (PreferencesHelper.loadCurrentMediaId() == mediaId) {
                    PreferencesHelper.resetPlayerState()
                    withContext(Main) {
                        if (controller?.isPlaying == true) controller?.pause()
                    }
                }
            }
        }
    }


    /* Marks an episode as played */
    private fun markEpisodePlayed(mediaId: String) {
        CoroutineScope(IO).launch {
            // mark as played
            collectionDatabase.episodeDao().markPlayed(mediaId = mediaId)
            // reset media id in player state if necessary
            if (PreferencesHelper.loadCurrentMediaId() == mediaId) {
                PreferencesHelper.resetPlayerState()
                withContext(Main) {
                    if (controller?.isPlaying == true) controller?.pause()
                    controller?.clearMediaItems()
                    playerState.currentEpisodeMediaId = String()
                    layout.hidePlayerExtendedViewsIfVisible()
                    layout.hidePlayer()
                }
            }
        }
    }


    /* Read OPML file */
    fun readOpmlFile(opmlUri: Uri)  {
        // read opml
        CoroutineScope(IO).launch {
            // readSuspended OPML on background thread
            val deferred: Deferred<Array<String>> = async(Dispatchers.Default) { OpmlHelper.readSuspended(this@BaseMainActivity, opmlUri) }
            // wait for result and update collection
            val feedUrls: Array<String> = deferred.await()
            withContext(Main) {
                OpmlImportDialog(this@BaseMainActivity).show(this@BaseMainActivity, feedUrls)
            }
        }
    }


    /* Download podcast feed using async co-routine */
    private fun downloadPodcastFeedsFromOpml(feedUrls: Array<String>) {
        if (NetworkHelper.isConnectedToNetwork(this)) {
            CoroutineScope(IO).launch {
                val podcastList: List<Podcast> = collectionDatabase.podcastDao().getAll()
                val urls = CollectionHelper.removeDuplicates(podcastList, feedUrls)
                if (urls.isNotEmpty()) {
                    withContext(Main) { Toast.makeText(this@BaseMainActivity, R.string.toast_message_adding_podcast, Toast.LENGTH_LONG).show() }
                    DownloadHelper.downloadPodcasts(this@BaseMainActivity, urls)
                    PreferencesHelper.saveLastUpdateCollection()
                }
            }
        } else {
            ErrorDialog().show(this, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Handles this activity's start intent */
    private fun handleStartIntent() {
        if (intent.action != null) {
            when (intent.action) {
                //Keys.ACTION_SHOW_PLAYER -> handleShowPlayer()
                Intent.ACTION_VIEW -> handleViewIntent()
            }
        }
        // clear intent action to prevent double calls
        intent.action = ""
    }


    /* Handles ACTION_VIEW request to add podcast or import OPML */
    private fun handleViewIntent() {
        val contentUri: Uri? = intent.data
        if (contentUri != null) {
            val scheme: String = contentUri.scheme ?: String()
            when {
                // download new podcast
                scheme.startsWith("http") -> downloadPodcastFeed(contentUri.toString())
                // readSuspended opml from content uri
                scheme.startsWith("content") -> readOpmlFile(contentUri)
            }
        }
    }


    /* Initializes the MediaController - handles connection to PlayerService under the hood */
    private fun initializeController() {
        controllerFuture = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlayerService::class.java))).buildAsync()
        controllerFuture.addListener({ setupController() }, MoreExecutors.directExecutor())
    }


    /* Releases MediaController */
    private fun releaseController() {
        MediaController.releaseFuture(controllerFuture)
    }


    /* Sets up the MediaController  */
    private fun setupController() {
        val controller: MediaController = this.controller ?: return

        // update Up Next views, if PlayerService has Up Next episode set
        if (controller.mediaItemCount == 2) {
            updateUpNext(controller.getMediaItemAt(1).mediaId) // todo implement
        }

        // update playback progress state
        togglePeriodicProgressUpdateRequest() // todo implement

        controller.addListener(playerListener)
    }


    /* Toggle periodic request of playback position from player service */
    private fun togglePeriodicProgressUpdateRequest() {
        when (controller?.isPlaying) {
            true -> {
                handler.removeCallbacks(periodicProgressUpdateRequestRunnable)
                handler.postDelayed(periodicProgressUpdateRequestRunnable, 0)
            }
            else -> {
                handler.removeCallbacks(periodicProgressUpdateRequestRunnable)
            }
        }
    }


    /* Sets up views and connects tap listeners - first run */
    private fun initializeViews() {

        // set up sleep timer start button
        layout.playerExtendedSleepTimerStartButtonView.setOnClickListener {
            when (controller?.isPlaying) {
                true -> {
                    controller?.startSleepTimer()
                    playerState.sleepTimerRunning = true
                }
                else -> Toast.makeText(this, R.string.toast_message_sleep_timer_unable_to_start,Toast.LENGTH_LONG).show()
            }
        }

        // set up sleep timer cancel button
        layout.playerExtendedSleepTimerCancelButtonView.setOnClickListener {
            controller?.cancelSleepTimer()
            layout.updateSleepTimer(this, 0L)
            playerState.sleepTimerRunning = false
        }
    }

    /* Sets up the general playback controls - Note: episode specific controls and views are updated in updatePlayerViews() */
    @SuppressLint("ClickableViewAccessibility") // it is probably okay to suppress this warning - the OnTouchListener on the time played view does only toggle the time duration / remaining display
    private fun setupPlaybackControls() {

        // bottom sheet start button for Up Next queue
        layout.upNextNameView.setOnClickListener {
            CoroutineScope(IO).launch {
                val upNextEpisode: Episode? = collectionDatabase.episodeDao().findByMediaId(playerState.upNextEpisodeMediaId)
                if (upNextEpisode != null) {
                    withContext(Main) {
                        handleStartPlayback(upNextEpisode)
                    }
                }
            }
            Toast.makeText(this, R.string.toast_message_up_next_start_playback, Toast.LENGTH_LONG).show()
        }

        // bottom sheet clear button for Up Next queue
        layout.upNextClearButtonView.setOnClickListener {
            if (controllerFuture.isDone && controller != null && controller?.hasMediaItems() == true) {
                // clear Up Next in Player Service - and afterwards in onTimelineChanged in Activity
                controller?.clearUpNext() // updateUpNext() is called in onTimelineChanged
            } else {
                // clear Up Next just in Activity
                PreferencesHelper.saveUpNextMediaId()
                updateUpNext()
            }
            Toast.makeText(this, R.string.toast_message_up_next_removed_episode, Toast.LENGTH_LONG).show()
        }

        // bottom sheet skip back button
        layout.playerExtendedSkipBackButtonView.setOnClickListener {
            when (playerState.isPlaying) {
                true -> controller?.seekBack() /* seeks back in the current MediaItem by getSeekBackIncrement() milliseconds. */
                false -> Toast.makeText(this, R.string.toast_message_skipping_disabled, Toast.LENGTH_LONG).show()
            }
        }

        // bottom sheet skip forward button
        layout.playerExtendedSkipForwardButtonView.setOnClickListener {
            when (playerState.isPlaying) {
                true -> controller?.seekForward() /* seeks forward in the current MediaItem by getSeekForwardIncrement() milliseconds. */
                false -> Toast.makeText(this, R.string.toast_message_skipping_disabled, Toast.LENGTH_LONG).show()
            }
        }

        // bottom sheet playback progress bar
        layout.playerExtendedProgressBarView.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                // Responds to when slider's touch event is being started
            }

            override fun onStopTrackingTouch(slider: Slider) {
                // Responds to when slider's touch event is being stopped
                if (slider.valueTo > 0L) controller?.seekTo(slider.value.toLong())
            }
        })

//        layout.sheetProgressBarView.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
//            var position: Int = 0
//            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
//            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//                position = progress
//            }
//            override fun onStopTrackingTouch(seekBar: SeekBar?) {
//                if (layout.sheetProgressBarView.max > 0L) controller?.seekTo(position.toLong())
//            }
//        })

        // bottom sheet time played display
        layout.playerExtendedTimePlayedView.setOnTouchListener { view, motionEvent ->
            view.performClick()
            when (motionEvent.action) {
                // show time remaining while touching the time played view
                MotionEvent.ACTION_DOWN -> layout.displayTimeRemaining = true
                // show episode duration when not touching the time played view anymore
                MotionEvent.ACTION_UP -> layout.displayTimeRemaining = false
                else -> return@setOnTouchListener false
            }
            updateProgressBar()
            return@setOnTouchListener true
        }

        // bottom sheet playback speed button
        layout.playerExtendedPlaybackSpeedButtonView.setOnClickListener {
            // change playback speed
            val newSpeed: Float = this.controller?.changePlaybackSpeed() ?: 1f
            // update state and UI
            playerState.playbackSpeed = newSpeed
            layout.updatePlaybackSpeedView(this, playerState.playbackSpeed)
        }

        // bottom sheet playback speed button
        layout.playerExtendedPlaybackSpeedButtonView.setOnLongClickListener {
            if (playerState.playbackSpeed != 1f) {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                Toast.makeText(this, R.string.toast_message_playback_speed_reset, Toast.LENGTH_LONG).show()
                // reset playback speed
                val newSpeed: Float = this.controller?.resetPlaybackSpeed() ?: 1f
                // update state and UI
                playerState.playbackSpeed = newSpeed
                layout.updatePlaybackSpeedView(this, playerState.playbackSpeed)
            }
            return@setOnLongClickListener true
        }
    }


    /* Updates episode specific controls and views. Note: general playback controls are set up in setupPlaybackControls() */
    private fun updatePlayerViews(checkEpisodeIsFinished: Boolean = true) {
        CoroutineScope(IO).launch {
            val currentEpisode = collectionDatabase.episodeDao().findByMediaId(playerState.currentEpisodeMediaId)
            // setup player views and buttons
            withContext(Main) {
                if (currentEpisode == null || (checkEpisodeIsFinished && currentEpisode.isFinished())) {
                    layout.hidePlayer()
                } else {
                    layout.showPlayer()
                    // update episode title, cover, etc.
                    layout.updatePlayerViews(this@BaseMainActivity, currentEpisode)
                    layout.updatePlaybackSpeedView(this@BaseMainActivity, playerState.playbackSpeed)
                    // main play/pause button and bottom sheet play/pause button
                    layout.playButtonView.setOnClickListener { (this@BaseMainActivity as PodcastListener).onPlayButtonTapped(currentEpisode) }
                    layout.playerExtendedPlayButtonView.setOnClickListener { (this@BaseMainActivity as PodcastListener).onPlayButtonTapped(currentEpisode) }
                    layout.playButtonView.setOnLongClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        (this@BaseMainActivity as PodcastListener).onMarkListenedButtonTapped(currentEpisode)
                        return@setOnLongClickListener true
                    }
                    layout.playerExtendedPlayButtonView.setOnLongClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        (this@BaseMainActivity as PodcastListener).onMarkListenedButtonTapped(currentEpisode)
                        return@setOnLongClickListener true
                    }
                }
            }
        }
    }


    /* Handles start playback */
    private fun handleStartPlayback(selectedEpisode: Episode) {
        val selectedEpisodeMediaId: String = selectedEpisode.mediaId
        when (selectedEpisodeMediaId) {
            // CASE: Episode is already in player
            controller?.getCurrentMediaId() -> {
                controller?.continuePlayback()
            }
            // CASE: Episode is already in Up Next queue
            controller?.getNextMediaId() -> {
                controller?.startUpNextEpisode()
            }
            // CASE: Episode is Up Next, but not yet initialized / in Up Next queue
            playerState.upNextEpisodeMediaId -> {
                // save state and start playback
                playerState.upNextEpisodeMediaId = String() // todo check if necessary
                playerState.currentEpisodeMediaId = selectedEpisode.mediaId  // todo check if necessary
                startPlayback(selectedEpisode)
            }
            // CASE: Episode was selected
            else -> {
                // save state and start playback
                playerState.currentEpisodeMediaId = selectedEpisode.mediaId // todo check if necessary
                startPlayback(selectedEpisode)
            }
        }
    }


    /* Starts playback */
    private fun startPlayback(episode: Episode) {
        CoroutineScope(IO).launch {
            var upNextEpisode: Episode? = null
            if (playerState.upNextEpisodeMediaId.isNotEmpty()) {
                upNextEpisode = collectionDatabase.episodeDao().findByMediaId(playerState.upNextEpisodeMediaId)
            }
            var position: Long = collectionDatabase.episodeDao().getPlaybackPosition(episode.mediaId)
            if (position >= episode.duration) position = 0L // reset position, if episode is finished
            withContext(Main) { controller?.play(this@BaseMainActivity, Episode(episode, playbackPosition = position), upNextEpisode) }
        }
    }


    /* Updates the Up Next queue */
    private fun updateUpNext(upNextEpisodeMediaId: String = String(), forceUpdate: Boolean = false) {
        // if not forced, check if an update is actually needed.
        if (!forceUpdate && playerState.upNextEpisodeMediaId == upNextEpisodeMediaId) {
            return
        }
        // new media id or forced update
        playerState.upNextEpisodeMediaId = upNextEpisodeMediaId
        if (upNextEpisodeMediaId.isNotEmpty()) {
            CoroutineScope(IO).launch {
                val upNextEpisode: Episode? = collectionDatabase.episodeDao().findByMediaId(upNextEpisodeMediaId)
                withContext(Main) {
                    layout.updateUpNextViews(upNextEpisode)
                }
            }
        } else {
            layout.updateUpNextViews(null)
        }
    }


    /* Updates the progress bar */
    private fun updateProgressBar() {
        // update progress bar - only if controller is prepared with a media item
        if (controller?.hasMediaItems() == true) {
            layout.updateProgressbar(this, controller?.currentPosition ?: 0L, controller?.duration ?: 0L)
        }
    }


    /* Requests an update of the sleep timer from the player service */
    private fun updateSleepTimer() {
        if (playerState.sleepTimerRunning) {
            val resultFuture: ListenableFuture<SessionResult>? = controller?.requestSleepTimerRemaining()
            resultFuture?.addListener(kotlinx.coroutines.Runnable {
                val timeRemaining: Long =
                    resultFuture.get().extras.getLong(Keys.EXTRA_SLEEP_TIMER_REMAINING)
                layout.updateSleepTimer(this, timeRemaining)
            }, MoreExecutors.directExecutor())
        }
    }


    /* Observe view model of podcast collection */
    private fun observeListViewModel() {
        collectionViewModel.numberOfPodcastsLiveData.observe(this, Observer<Int> { numberOfPodcasts ->
            if (numberOfPodcasts == 0) {
                layout.hidePlayer()
            }
            CoroutineScope(IO).launch {
                CollectionHelper.exportCollectionOpml(this@BaseMainActivity, collectionDatabase.podcastDao().getAll())
            }
        })
    }


    /*
    * Runnable: Periodically requests playback position (and sleep timer if running)
    */
    private val periodicProgressUpdateRequestRunnable: Runnable = object : Runnable {
        override fun run() {
            // update progress bar
            updateProgressBar()
            // update sleep timer view
            updateSleepTimer()
            // use the handler to start runnable again after specified delay
            handler.postDelayed(this, 500)
        }
    }
    /*
     * End of declaration
     */


    /*
     * Defines the listener for changes in shared preferences
     */
    private val sharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            Keys.PREF_THEME_SELECTION -> {
                AppThemeHelper.setTheme(PreferencesHelper.loadThemeSelection())
            }
        }
    }
    /*
     * End of declaration
     */


    /*
     * Player.Listener: Called when one or more player states changed.
     */
    private var playerListener: Player.Listener = object : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            // store new episode
            playerState.currentEpisodeMediaId = mediaItem?.mediaId ?: String()
            // update episode specific views
            updatePlayerViews(checkEpisodeIsFinished = false) // assume playback is running
            // clear up next, if necessary
            if (playerState.upNextEpisodeMediaId == mediaItem?.mediaId || playerState.upNextEpisodeMediaId.isEmpty() || playerState.upNextEpisodeMediaId == playerState.currentEpisodeMediaId) {
                updateUpNext()
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            super.onTimelineChanged(timeline, reason)
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                // reason: a media item has been added, removed or moved
                if (controller?.mediaItemCount == 2) {
                    updateUpNext(controller?.getMediaItemAt(1)?.mediaId ?: String())
                } else {
                    updateUpNext()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            // store state of playback
            playerState.isPlaying = isPlaying
            // animate state transition of play button(s)
            layout.animatePlaybackButtonStateTransition(this@BaseMainActivity, isPlaying)
            // turn on/off periodic playback position updates
            togglePeriodicProgressUpdateRequest()

            if (isPlaying) {
                // playback is active
                layout.showPlayer()
                layout.showBufferingIndicator(buffering = false)
            } else {
                // playback is not active
                layout.updateSleepTimer(this@BaseMainActivity)
                //playerState.sleepTimerRunning = false // todo remove
                // Not playing because playback is paused, ended, suppressed, or the player
                // is buffering, stopped or failed. Check player.getPlayWhenReady,
                // player.getPlaybackState, player.getPlaybackSuppressionReason and
                // player.getPlaybackError for details.
                when (controller?.playbackState) {
                    // player is able to immediately play from its current position
                    Player.STATE_READY -> {
                        layout.showBufferingIndicator(buffering = false)
                    }
                    // buffering - data needs to be loaded
                    Player.STATE_BUFFERING -> {
                        layout.showBufferingIndicator(buffering = true)
                    }
                    // player finished playing all media
                    Player.STATE_ENDED -> {
                        layout.hidePlayer()
                        layout.showBufferingIndicator(buffering = false)
                    }
                    // initial state or player is stopped or playback failed
                    Player.STATE_IDLE -> {
                        layout.hidePlayer()
                        layout.showBufferingIndicator(buffering = false)
                    }
                }
            }

            // update the sleep timer running state // todo check if necessary
            val resultFuture: ListenableFuture<SessionResult>? = controller?.requestSleepTimerRunning()
            resultFuture?.addListener(kotlinx.coroutines.Runnable {
                playerState.sleepTimerRunning =
                    resultFuture.get().extras.getBoolean(Keys.EXTRA_SLEEP_TIMER_RUNNING, false)
            }, MoreExecutors.directExecutor())

        }

    }
    /*
     * End of declaration
     */

}