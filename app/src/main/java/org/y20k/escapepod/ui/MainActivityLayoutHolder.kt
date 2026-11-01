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


package org.y20k.escapepod.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
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
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.EpisodeDescription
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.dialogs.ShowNotesDialog
import org.y20k.escapepod.helpers.DateTimeHelper
import org.y20k.escapepod.helpers.PreferencesHelper
import org.y20k.escapepod.helpers.UiHelper
import org.y20k.escapepod.interfaces.ListDragListener
import org.y20k.escapepod.interfaces.PlayerContainerStateChangeListener
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
    private var playbackControlsContainer: LinearLayout
    var playerCardView: MaterialCardView
    var upNextCardView: MaterialCardView
    private var playerViews: Group
    private var playerExtendedViews: Group
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
    var playerExtendedPlaybackSpeedButtonView: Button
    var upNextNameView: TextView
    var upNextClearButtonView: ImageButton
    //    private var onboardingLayout: ConstraintLayout
    private var isBuffering: Boolean
    var displayTimeRemaining: Boolean
    var userInterfaceTransparencyEffectActive: Boolean
    var playerContainerStateChangeListener: PlayerContainerStateChangeListener? = null


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
        playerExtendedPlaybackSpeedButtonView = rootView.findViewById(R.id.playback_speed_button)
        upNextNameView = rootView.findViewById(R.id.up_next_name)
        upNextClearButtonView = rootView.findViewById(R.id.up_next_clear_button)
//        onboardingLayout = rootView.findViewById(R.id.onboarding_layout)
        displayTimeRemaining = false
        isBuffering = false
        userInterfaceTransparencyEffectActive = PreferencesHelper.loadUserInterfaceTransparencyEffect()

        // make background of download progress bar semitransparent
        downloadProgressIndicator.background.alpha = 128

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
            val coverImageFile: File = File(episode.cover.toUri().path ?: "")
            Glide.with(context)
                .load(episode.cover)
                .signature(ObjectKey(coverImageFile.lastModified()))
                .error(R.drawable.ic_default_cover_rss_icon_192dp)
                .into(coverView)
            coverView.contentDescription = "${context.getString(R.string.descr_player_podcast_cover)}: ${episode.podcastName}"
            podcastNameView.text = episode.podcastName
            episodeTitleView.text = episode.title
            Glide.with(context)
                .load(episode.cover)
                .signature(ObjectKey(coverImageFile.lastModified()))
                .error(R.drawable.ic_default_cover_rss_icon_192dp)
                .into(playerExtendedCoverView)
            // sheetCoverView.clipToOutline = true // apply rounded corner mask to covers
            playerExtendedCoverView.contentDescription = "${context.getString(R.string.descr_expanded_player_podcast_cover)}: ${episode.podcastName}"
            playerExtendedEpisodeTitleView.text = episode.title
            playerExtendedDurationView.text = duration
            playerExtendedDurationView.contentDescription = "${context.getString(R.string.descr_expanded_episode_length)}: $duration"
            playerExtendedProgressBarView.valueTo = episode.duration.toFloat() + 1 // stupid hack to handle episodes with no known duration

            // setup progress bar
            updateProgressbar(context, episode.playbackPosition, episode.duration)

            // update click listeners
            playerExtendedCoverView.setOnClickListener {
                displayShowNotes(context, episode)
            }
            playerExtendedEpisodeTitleView.setOnClickListener {
                displayShowNotes(context, episode)
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


    /* Toggles visibility of the download progress indicator */
    fun toggleDownloadProgressIndicator() {
        when (PreferencesHelper.loadActiveDownloads()) {
            Keys.ACTIVE_DOWNLOADS_EMPTY -> downloadProgressIndicator.isGone = true
            else -> downloadProgressIndicator.isVisible = true
        }
    }


    /* Loads and displays show notes */
    private fun displayShowNotes(context: Context, episode: Episode) {
        CoroutineScope(IO).launch {
            val podcast: Podcast? = collectionDatabase.podcastDao().findByRemotePodcastFeedLocation(episode.episodeRemotePodcastFeedLocation)
            val episodeDescription: EpisodeDescription? = collectionDatabase.episodeDescriptionDao().findByMediaId(episode.mediaId)
            if (episodeDescription != null && podcast != null) {
                withContext(Main) { ShowNotesDialog().show(context, podcast, episode, episodeDescription) }
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


    /* Updates the playback speed view */
    fun updatePlaybackSpeedView(context: Context, speed: Float = 1f) {
        val playbackSpeedButtonText: String = "$speed x"
        playerExtendedPlaybackSpeedButtonView.text = playbackSpeedButtonText
        playerExtendedPlaybackSpeedButtonView.contentDescription = "$playbackSpeedButtonText - ${context.getString(R.string.descr_expanded_player_playback_speed_button)}"
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


    /* Toggles play/pause buttons */
    fun togglePlayButtons(isPlaying: Boolean) {
        when (isPlaying) {
            true -> {
                playButtonView.setImageResource(R.drawable.ic_player_pause_symbol_48dp)
                playerExtendedPlayButtonView.setImageResource(R.drawable.ic_player_pause_symbol_68dp)
            }
            false -> {
                playButtonView.setImageResource(R.drawable.ic_player_play_symbol_48dp)
                playerExtendedPlayButtonView.setImageResource(R.drawable.ic_player_play_symbol_68dp)
            }
        }
    }


    /* Toggles buffering indicator */
    fun showBufferingIndicator(buffering: Boolean) {
        bufferingIndicator.isVisible = buffering
        isBuffering = buffering
    }


    /* Initiates the rotation animation of the play button  */
    fun animatePlaybackButtonStateTransition(context: Context, isPlaying: Boolean) {
        var morphDrawable: AnimatedVectorDrawable? = null
        if (isPlaying) {
            // set up play/pause button animation
            when (playerViews.isVisible) {
                true -> {
                    playButtonView.setImageResource(R.drawable.anim_play_to_pause_48dp)
                    morphDrawable = playButtonView.drawable as AnimatedVectorDrawable
                }

                false -> {
                    playerExtendedPlayButtonView.setImageResource(R.drawable.anim_play_to_pause_68dp)
                    morphDrawable = playerExtendedPlayButtonView.drawable as AnimatedVectorDrawable
                }
            }
        } else {
            when (playerViews.isVisible) {
                true -> {
                    playButtonView.setImageResource(R.drawable.anim_pause_to_play_48dp)
                    morphDrawable = playButtonView.drawable as AnimatedVectorDrawable
                }
                false -> {
                    playerExtendedPlayButtonView.setImageResource(R.drawable.anim_pause_to_play_68dp)
                    morphDrawable = playerExtendedPlayButtonView.drawable as AnimatedVectorDrawable
                }
            }
        }

        if (morphDrawable != null) {
            // rotate and morph to play/pause icon
            morphDrawable.start()
            morphDrawable.registerAnimationCallback(object : Animatable2.AnimationCallback() {
                override fun onAnimationEnd(drawable: Drawable?) {
                    togglePlayButtons(isPlaying)
                }
            })
        } else {
            // just toggle the play/pause icon
            togglePlayButtons(isPlaying)
        }
    }


    /* Shows player */
    fun showPlayer(): Boolean {
        playerCardView.isVisible = true
        playerContainerStateChangeListener?.onPlayerContainerStateChanged(playerVisible = true, upNextVisible = upNextCardView.isVisible)
        return true
    }


    /* Hides player */
    fun hidePlayer(): Boolean {
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
    private fun showPlayerExtendedViews() {
        val transition = AutoTransition().apply {
            duration = Keys.DEFAULT_TRANSITION_ANIMATION_DURATION
        }
        TransitionManager.beginDelayedTransition(playerCardView, transition)
        playerExtendedViews.isVisible = true
        sleepTimerRunningViews.isGone = playerExtendedSleepTimerRemainingTimeView.text.isEmpty()
        playerViews.isGone = true
    }


    /* Hides the large player sheet views and shows the player views */
    private fun hidePlayerExtendedViews() {
        val transition = AutoTransition().apply {
            duration = Keys.DEFAULT_TRANSITION_ANIMATION_DURATION
        }
        TransitionManager.beginDelayedTransition(playerCardView, transition)
        playerExtendedViews.isGone = true
        sleepTimerRunningViews.isGone = true
        playerViews.isVisible = true
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