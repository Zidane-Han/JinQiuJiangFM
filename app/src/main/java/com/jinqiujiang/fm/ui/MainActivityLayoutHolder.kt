/*
 * MainActivityLayoutHolder.kt
 * MainActivityLayoutHolder the LayoutHolder class
 * A MainActivityLayoutHolder holds references to the views of the MainActivity
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jinqiujiang.fm.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.content.Context
import androidx.lifecycle.lifecycleScope
import androidx.core.widget.NestedScrollView
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.core.graphics.Insets
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentContainerView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jinqiujiang.fm.Keys
import com.jinqiujiang.fm.R
import com.jinqiujiang.fm.database.CollectionDatabase
import com.jinqiujiang.fm.database.objects.Episode
import com.jinqiujiang.fm.database.objects.EpisodeDescription
import com.jinqiujiang.fm.database.objects.Podcast
import com.jinqiujiang.fm.dialogs.ShowNotesDialog
import com.jinqiujiang.fm.helpers.DateTimeHelper
import com.jinqiujiang.fm.helpers.PreferencesHelper
import com.jinqiujiang.fm.helpers.UiHelper
import com.jinqiujiang.fm.interfaces.ListDragListener
import com.jinqiujiang.fm.interfaces.PlayerContainerStateChangeListener
import java.io.File


/*
 * MainActivityLayoutHolder class
 */
data class MainActivityLayoutHolder(val activity: AppCompatActivity, val rootView: View, val collectionDatabase: CollectionDatabase) : ListDragListener {

    /* Define log tag */
    private val TAG: String = MainActivityLayoutHolder::class.java.simpleName


    /* Main class variables */
    private lateinit var systemBars: Insets
    private var mainHostContainer: FragmentContainerView
    private var downloadProgressIndicator: ProgressBar
    private var downloadsInProgress = false
    private var playbackControlsContainer: LinearLayout
    var playerCardView: MaterialCardView
    var upNextCardView: MaterialCardView
    private var playerViews: Group
    private var playerExtendedViews: View
    var sleepTimerRunningViews: Group
    private var coverView: ImageView
    private var podcastNameView: TextView
    private var episodeTitleView: TextView
    var playButtonView: ImageButton
    var bufferingIndicator: ProgressBar
    private var playerExtendedCoverView: ImageView
    var playerExtendedProgressBarView: Slider
    var playerExtendedTimePlayedView: TextView
    var playerExtendedDurationView: TextView
    private var playerExtendedEpisodeTitleView: TextView
    var playerExtendedPlayButtonView: ImageButton
    var playerExtendedSkipBackButtonView: ImageButton
    var playerExtendedSkipForwardButtonView: ImageButton
    var playerExtendedSleepTimerStartButtonView: ImageButton
    var playerExtendedSleepTimerCancelButtonView: ImageButton
    var playerExtendedSleepTimerRemainingTimeView: TextView
    private val contentScroll: NestedScrollView = rootView.findViewById(R.id.player_content_scroll)
    private val hero: View = rootView.findViewById(R.id.player_hero)
    private val notesSection: View = rootView.findViewById(R.id.player_notes_section)
    private val notesBody: TextView = rootView.findViewById(R.id.player_notes_body)
    private val notesContent: ShowNotesView = rootView.findViewById(R.id.player_notes_content)
    private val notesRetry: View = rootView.findViewById(R.id.player_notes_retry)
    private var notesJob: Job? = null
    private var currentEpisode: Episode? = null
    private val currentEpisodeId: String? get() = currentEpisode?.mediaId
    private var restoredScroll: Int? = null
    private var restoredMediaId: String? = null
    var upNextNameView: TextView
    var upNextClearButtonView: ImageButton
    //    private var onboardingLayout: ConstraintLayout
    private var isBuffering: Boolean
    var displayTimeRemaining: Boolean
    var userInterfaceTransparencyEffectActive: Boolean
    var playerContainerStateChangeListener: PlayerContainerStateChangeListener? = null
    var onTimestampClick: ((Episode, Long) -> Unit)? = null


    /* Init block */
    init {
        // find views
        mainHostContainer = rootView.findViewById(R.id.main_host_container)
        downloadProgressIndicator = rootView.findViewById(R.id.download_progress_indicator)
        playbackControlsContainer = rootView.findViewById(R.id.playback_controls_container)
        playerCardView = rootView.findViewById(R.id.player_card)
        upNextCardView = rootView.findViewById(R.id.up_next_card)
        playerViews = rootView.findViewById(R.id.playback_views)
        playerExtendedViews = rootView.findViewById(R.id.player_extended_views)
        sleepTimerRunningViews = rootView.findViewById(R.id.sleep_timer_running_views)
        coverView = rootView.findViewById(R.id.player_podcast_cover)
        podcastNameView = rootView.findViewById(R.id.podcast_name)
        episodeTitleView = rootView.findViewById(R.id.player_episode_title)
        playButtonView = rootView.findViewById(R.id.player_play_button)
        bufferingIndicator = rootView.findViewById(R.id.player_buffering_indicator)
        playerExtendedCoverView = rootView.findViewById(R.id.player_extended_large_podcast_cover)
        playerExtendedProgressBarView = rootView.findViewById(R.id.player_extended_playback_slider)
        playerExtendedTimePlayedView = rootView.findViewById(R.id.player_extended_time_played_view)
        playerExtendedDurationView = rootView.findViewById(R.id.player_extended_duration_view)
        playerExtendedEpisodeTitleView = rootView.findViewById(R.id.player_extended_episode_title)
        playerExtendedPlayButtonView = rootView.findViewById(R.id.player_extended_play_button)
        playerExtendedSkipBackButtonView = rootView.findViewById(R.id.player_extended_skip_back_button)
        playerExtendedSkipForwardButtonView = rootView.findViewById(R.id.player_extended_skip_forward_button)
        playerExtendedSleepTimerStartButtonView = rootView.findViewById(R.id.sleep_timer_start_button)
        playerExtendedSleepTimerCancelButtonView = rootView.findViewById(R.id.sleep_timer_cancel_button)
        playerExtendedSleepTimerRemainingTimeView = rootView.findViewById(R.id.sleep_timer_remaining_time)
        rootView.findViewById<View>(R.id.player_collapse_button).setOnClickListener { hidePlayerExtendedViews() }
        rootView.findViewById<View>(R.id.player_shownote_button).setOnClickListener {
            contentScroll.smoothScrollTo(0, notesSection.top, 500)
        }
        notesRetry.setOnClickListener { currentEpisode?.let(::loadInlineNotes) }
        contentScroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val landscape = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val available = if (landscape) contentScroll.width / 3 else contentScroll.width - dp(48)
            val size = minOf(available, if (landscape) dp(220) else maxOf(dp(120), contentScroll.height - dp(200)), dp(340))
            if (playerExtendedCoverView.layoutParams.width != size && size > 0) {
                playerExtendedCoverView.updateLayoutParams { width = size; height = size }
            }
            if (hero.minimumHeight != contentScroll.height) hero.minimumHeight = contentScroll.height
        }
        upNextNameView = rootView.findViewById(R.id.up_next_name)
        upNextClearButtonView = rootView.findViewById(R.id.up_next_clear_button)
//        onboardingLayout = rootView.findViewById(R.id.onboarding_layout)
        displayTimeRemaining = false
        isBuffering = false
        userInterfaceTransparencyEffectActive = PreferencesHelper.loadUserInterfaceTransparencyEffect()

        // make background of download progress bar semitransparent
        downloadProgressIndicator.background.mutate().alpha = 128

        // set up RecyclerView
        val animator: SimpleItemAnimator = DefaultItemAnimator()
        animator.supportsChangeAnimations = false

        // display minutes + seconds in progress bar thumb label
        playerExtendedProgressBarView.setLabelFormatter(LabelFormatter { value ->
            DateTimeHelper.convertToMinutesAndSeconds(value.toLong())
        })

        // set up edge to edge display
        setupEdgeToEdge()

        // set layout for player
        setupPlayer()
    }


    /* Overrides onListDragStateChanged from ListDragListener */
    override fun onListDragStateChanged(newState: Int) {
        setPlayerTransparencyDuringDrag(newState)
    }


    /* Updates the player views */
    @SuppressLint("ClickableViewAccessibility")
    fun updatePlayerViews(context: Context, episode: Episode?) {
        if (episode != null) {
            val duration: String
            if (episode.duration > 0L) duration = DateTimeHelper.convertToMinutesAndSeconds(episode.duration) else duration = "∞"
            val artwork = episode.remoteCoverFileLocation.ifBlank { episode.cover }
            Glide.with(context)
                .load(artwork)
                .placeholder(R.drawable.podcast_cover)
                .error(R.drawable.podcast_cover)
                .into(coverView)
            coverView.contentDescription = "${context.getString(R.string.descr_player_podcast_cover)}: ${episode.title}"
            podcastNameView.text = episode.podcastName
            episodeTitleView.text = episode.title
            Glide.with(context)
                .load(artwork)
                .placeholder(R.drawable.podcast_cover)
                .error(R.drawable.podcast_cover)
                .into(playerExtendedCoverView)
            // sheetCoverView.clipToOutline = true // apply rounded corner mask to covers
            playerExtendedCoverView.contentDescription = "${context.getString(R.string.descr_expanded_player_podcast_cover)}: ${episode.title}"
            playerExtendedEpisodeTitleView.text = episode.title
            playerExtendedDurationView.text = duration
            playerExtendedDurationView.contentDescription = "${context.getString(R.string.descr_expanded_episode_length)}: $duration"
            playerExtendedProgressBarView.valueTo = episode.duration.toFloat() + 1 // stupid hack to handle episodes with no known duration

            // setup progress bar
            updateProgressbar(context, episode.playbackPosition, episode.duration)

            rootView.findViewById<TextView>(R.id.player_episode_metadata).text = "${episode.podcastName} · ${DateTimeHelper.getDateString(episode.publicationDate)}"
            val episodeChanged = currentEpisodeId != episode.mediaId
            currentEpisode = episode
            if (episodeChanged) {
                contentScroll.scrollTo(0, 0)
                if (restoredMediaId != episode.mediaId) restoredScroll = null
                if (playerExtendedViews.isVisible) {
                    loadInlineNotes(episode)
                } else {
                    notesJob?.cancel()
                    notesJob = null
                    notesContent.clear()
                }
            }
            podcastNameView.setOnLongClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                displayShowNotes(context, episode)
                return@setOnLongClickListener true
            }
            episodeTitleView.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                displayShowNotes(context, episode)
                return@setOnLongClickListener true
            }
        }
    }


    fun setDownloadsInProgress(inProgress: Boolean) {
        downloadsInProgress = inProgress
        updateDownloadProgressVisibility()
    }

    private fun updateDownloadProgressVisibility() {
        downloadProgressIndicator.isVisible = downloadsInProgress && !playerExtendedViews.isVisible
    }


    /* Loads and displays show notes */
    private fun displayShowNotes(context: Context, episode: Episode) {
        CoroutineScope(IO).launch {
            val podcast: Podcast? = collectionDatabase.podcastDao().findByRemotePodcastFeedLocation(episode.episodeRemotePodcastFeedLocation)
            val episodeDescription: EpisodeDescription? = collectionDatabase.episodeDescriptionDao().findByMediaId(episode.mediaId)
            if (episodeDescription != null && podcast != null) {
                withContext(Main) {
                    ShowNotesDialog().show(context, podcast, episode, episodeDescription) { position ->
                        onTimestampClick?.invoke(episode, position)
                    }
                }
            }
        }
    }


    /* Updates the progress bar */
    fun updateProgressbar(context: Context, position: Long, duration: Long) {
        val timePlayed = DateTimeHelper.convertToMinutesAndSeconds(position)
        playerExtendedTimePlayedView.text = timePlayed
        playerExtendedTimePlayedView.contentDescription = "${context.getString(R.string.descr_expanded_player_time_played)}: $timePlayed"
        if (position >= playerExtendedProgressBarView.valueFrom && position <= playerExtendedProgressBarView.valueTo) {
            playerExtendedProgressBarView.value = position.toFloat()
        }
        if (displayTimeRemaining && duration > 0L) {
            val timeRemaining = DateTimeHelper.convertToMinutesAndSeconds((duration - position), negativeValue = true)
            playerExtendedDurationView.text = timeRemaining
            playerExtendedDurationView.contentDescription = "${context.getString(R.string.descr_expanded_player_time_remaining)}: $timeRemaining"
        } else if (duration > 0L || playerExtendedDurationView.text == "∞") {
            playerExtendedDurationView.text = DateTimeHelper.convertToMinutesAndSeconds(duration)
            playerExtendedProgressBarView.valueTo = duration.toFloat() + 1 // stupid hack to handle episodes with no known duration
            playerExtendedDurationView.contentDescription = context.getString(R.string.descr_expanded_episode_length)
        }
    }


    /* Updates the Up Next views */
    fun updateUpNextViews(upNextEpisode: Episode?) {
        when (upNextEpisode != null) {
            true -> {
                // show the Up Next queue if queue is not empty
                upNextCardView.isGone = true // stupid hack - try to remove this line ASAP (https://stackoverflow.com/a/47893965) // todo check if still necessary
                upNextCardView.isVisible = true
                // update Up Next view
                val upNextName = "${upNextEpisode.podcastName} - ${upNextEpisode.title}"
                upNextNameView.text = upNextName
                playerContainerStateChangeListener?.onPlayerContainerStateChanged(playerVisible = playerCardView.isVisible, upNextVisible = true)
            }
            false -> {
                // hide the Up Next queue if queue is empty
                upNextCardView.isGone = true // stupid hack - try to remove this line ASAP (https://stackoverflow.com/a/47893965) // todo check if still necessary
                upNextCardView.isVisible = false
                playerContainerStateChangeListener?.onPlayerContainerStateChanged(playerVisible = playerCardView.isVisible, upNextVisible = false)
            }
        }
    }


    /* Updates sleep timer views */
    fun updateSleepTimer(context: Context, timeRemaining: Long = 0L) {
        when (timeRemaining) {
            0L -> {
                if (!sleepTimerRunningViews.isGone) {
                    sleepTimerRunningViews.isGone = true
                    playerExtendedSleepTimerRemainingTimeView.text = String()
                }
            }
            else -> {
                if (playerExtendedViews.isVisible) {
                    sleepTimerRunningViews.isVisible = true
                    val sleepTimerTimeRemaining: String = DateTimeHelper.convertToMinutesAndSeconds(timeRemaining)
                    playerExtendedSleepTimerRemainingTimeView.text = sleepTimerTimeRemaining
                    playerExtendedSleepTimerRemainingTimeView.contentDescription = "${context.getString(R.string.descr_expanded_player_sleep_timer_remaining_time)}: $sleepTimerTimeRemaining"
                }
            }
        }
    }


    /* Synchronize both controls without waiting for an animation on a hidden view. */
    fun togglePlayButtons(isPlaying: Boolean) {
        PlaybackButtonState.render(playButtonView, playerExtendedPlayButtonView, isPlaying)
    }

    /* Toggles buffering indicator */
    fun showBufferingIndicator(buffering: Boolean) {
        bufferingIndicator.isVisible = buffering
        isBuffering = buffering
    }


    /* Shows player */
    fun showPlayer(): Boolean {
        playerCardView.isVisible = true
        playerContainerStateChangeListener?.onPlayerContainerStateChanged(playerVisible = true, upNextVisible = upNextCardView.isVisible)
        return true
    }


    /* Hides player */
    fun hidePlayer(): Boolean {
        hidePlayerExtendedViews()
        playerCardView.isGone = true
        playerContainerStateChangeListener?.onPlayerContainerStateChanged(playerVisible = false, upNextVisible = upNextCardView.isVisible)
        return true
    }


    /* Hides the info views if they are visible */
    fun hidePlayerExtendedViewsIfVisible(): Boolean {
        return if (playerExtendedViews.isVisible) {
            hidePlayerExtendedViews()
            true // = info view was visible had to be hidden (= no need to interpret back press as a navigation)
        } else {
            false
        }
    }


    /* Shows the large player sheet views and hides the player views */
    fun showPlayerExtendedViews() {
        playerExtendedViews.isVisible = true
        updateDownloadProgressVisibility()
        playbackControlsContainer.isGone = true
        mainHostContainer.visibility = View.INVISIBLE
        mainHostContainer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        contentScroll.scrollTo(0, 0)
        if (notesJob == null) currentEpisode?.let(::loadInlineNotes)
    }

    private fun hidePlayerExtendedViews() {
        playerExtendedViews.isGone = true
        updateDownloadProgressVisibility()
        playbackControlsContainer.isVisible = true
        mainHostContainer.isVisible = true
        mainHostContainer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private fun loadInlineNotes(episode: Episode) {
        val mediaId = episode.mediaId
        notesJob?.cancel()
        notesContent.clear()
        notesBody.isVisible = true
        notesBody.setText(R.string.player_notes_loading)
        notesRetry.isGone = true
        notesJob = activity.lifecycleScope.launch {
            try {
                val description = withContext(IO) { collectionDatabase.episodeDescriptionDao().findByMediaId(mediaId)?.description }
                if (currentEpisodeId != mediaId) return@launch
                val hasContent = !description.isNullOrBlank() && notesContent.show(description, episode.duration) { position ->
                    onTimestampClick?.invoke(episode, position)
                }
                notesBody.isVisible = !hasContent
                if (!hasContent) notesBody.setText(R.string.player_notes_empty)
                restoredScroll?.let { y -> contentScroll.post { contentScroll.scrollTo(0, y) }; restoredScroll = null }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (currentEpisodeId == mediaId) {
                    notesContent.clear()
                    notesBody.isVisible = true
                    notesBody.setText(R.string.player_notes_error)
                    notesRetry.isVisible = true
                }
            }
        }
    }

    fun savePlayerUiState(state: Bundle) {
        state.putBoolean("player_expanded", playerExtendedViews.isVisible)
        state.putInt("player_notes_scroll", contentScroll.scrollY)
        state.putString("player_notes_media_id", currentEpisodeId)
    }

    fun restorePlayerUiState(state: Bundle?) {
        if (state?.getBoolean("player_expanded") == true) {
            showPlayerExtendedViews()
            restoredScroll = state.getInt("player_notes_scroll")
            restoredMediaId = state.getString("player_notes_media_id")
        }
    }

    /* Toggles between showing the playback views (default) and the station info views */
    private fun togglePlayerExtendedViews() {
        if (playerExtendedViews.isGone) {
            showPlayerExtendedViews()
        } else if (playerExtendedViews.isVisible) {
            hidePlayerExtendedViews()
        }
    }


    /* Sets up the player */
    private fun setupPlayer() {
        playerCardView.setOnClickListener { togglePlayerExtendedViews() }
        coverView.setOnClickListener { togglePlayerExtendedViews() }
        podcastNameView.setOnClickListener { togglePlayerExtendedViews() }
        episodeTitleView.setOnClickListener { togglePlayerExtendedViews() }
    }


    /* Resets player transparency to fully opaque */
    fun resetPlayerTransparency() {
        if (userInterfaceTransparencyEffectActive) {
            playbackControlsContainer.alpha = 1.0f
        }
    }


    /* Make player semi-transparent during list drag */
    private fun setPlayerTransparencyDuringDrag(newState: Int) {
        if (userInterfaceTransparencyEffectActive) {
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING -> {
                    playbackControlsContainer.alpha = 0.5f
                    hidePlayerExtendedViews()
                }
                RecyclerView.SCROLL_STATE_IDLE -> {
                    // animate the alpha transition from 0.5f to 1.0f
                    playbackControlsContainer.animate()
                        .alpha(1.0f)
                        .setDuration(Keys.DEFAULT_TRANSITION_ANIMATION_DURATION)
                        .start()
                }
                RecyclerView.SCROLL_STATE_SETTLING -> {
                    // animation handled in IDLE state
                }
            }
        }
    }


    /* Sets up margins/paddings for edge to edge view */
    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            // get measurements for status and navigation bar
            systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            playerExtendedViews.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            // apply measurements
            downloadProgressIndicator.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = systemBars.top
            }
            playbackControlsContainer.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = (Keys.UP_NEXT_TOP_MARGIN * UiHelper.getDensityScalingFactor(rootView.context)).toInt() + systemBars.bottom
                bottomMargin = (Keys.PLAYER_BOTTOM_MARGIN * UiHelper.getDensityScalingFactor(rootView.context)).toInt() + systemBars.bottom
            }
            // return the insets
            insets
        }
    }

}
