package org.y20k.escapepod.collection

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.Group
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.y20k.escapepod.R
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.EpisodeDescription
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.dialogs.ShowNotesDialog
import org.y20k.escapepod.helpers.DateTimeHelper
import org.y20k.escapepod.interfaces.PodcastListener

/*
 * EpisodeViewHolder class
 */
class EpisodeViewHolder (layout: View): RecyclerView.ViewHolder(layout) {
// class EpisodeViewHolder (val layout: View): RecyclerView.ViewHolder(layout) {  // testing ListItemLayout todo remove

    /* Main class variables */
    val episodeDateView: TextView = layout.findViewById(R.id.episode_date)
    val episodeTitleView: TextView = layout.findViewById(R.id.episode_title)
    val episodeDownloadButtonView: ImageButton = layout.findViewById(R.id.episode_download_button)
    val episodeDeleteButtonView: ImageButton = layout.findViewById(R.id.episode_delete_button)
    val episodePlayButtonView: ImageButton = layout.findViewById(R.id.episode_play_button)
    val episodePlaybackProgressView: ProgressBar = layout.findViewById(R.id.episode_playback_progress)
    val episodePlaybackViews: Group = layout.findViewById(R.id.episode_playback_views)
    var tapAnywherePlaybackEnabled: Boolean = false
    lateinit var episode: Episode


    /* Initializes the view holder - call only after an episode has been set, the listeners read it */
    fun initialize(context: Context, collectionDatabase: CollectionDatabase, podcastListener: PodcastListener) {
        // show the episode description on long click
        episodeTitleView.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showEpisodeDescription(context, collectionDatabase)
            return@setOnLongClickListener true
        }
        episodeDateView.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showEpisodeDescription(context, collectionDatabase)
            return@setOnLongClickListener true
        }
        // play/pause on click
        episodeDateView.setOnClickListener {
            if (tapAnywherePlaybackEnabled && episodePlayButtonView.isVisible) {
                episodePlayButtonView.performClick()
            }
        }
        episodeTitleView.setOnClickListener {
            if (tapAnywherePlaybackEnabled && episodePlayButtonView.isVisible) {
                episodePlayButtonView.performClick()
            }
        }
        episodePlayButtonView.setOnClickListener {
            podcastListener.onPlayButtonTapped(episode)
        }
        // mark episode listened on long click
        episodePlayButtonView.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            podcastListener.onMarkListenedButtonTapped(episode)
            return@setOnLongClickListener true
        }
        // download episode on click
        episodeDownloadButtonView.setOnClickListener {
            podcastListener.onDownloadButtonTapped(episode)
        }
        // download all episodes on long click
        episodeDownloadButtonView.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            podcastListener.onDownloadAllButtonTapped(episode.episodeRemotePodcastFeedLocation)
            return@setOnLongClickListener true
        }
        // delete episode on click
        episodeDeleteButtonView.setOnClickListener {
            podcastListener.onDeleteButtonTapped(episode)
        }
    }


    /* Sets up the circular progress bar */
    fun setEpisodePlaybackProgress() {
        // start => 12 => playbackPosition = 0
        // finish => 0 => playbackPosition = duration
        val progress: Double = (episode.duration.toDouble() - episode.playbackPosition.toDouble()) / episode.duration.toDouble() * 12
        episodePlaybackProgressView.progress = progress.toInt()
    }


    /* Sets up an episode's title views */
    fun setEpisodeTitle() {
        episodeDateView.text = DateTimeHelper.getDateString(episode.publicationDate)
        episodeTitleView.text = episode.title
    }


    /* Sets up an episode's play, download and delete button views (for CollectionAdapter) */
    fun setEpisodeButtons() {
        when (episode.isPlaying) {
            true -> episodePlayButtonView.setImageResource(R.drawable.ic_pause_symbol_24dp)
            false -> episodePlayButtonView.setImageResource(R.drawable.ic_play_symbol_24dp)
        }
        if (episode.audio.isNotEmpty()) {
            episodeDownloadButtonView.isGone = true
            episodePlaybackViews.isVisible = true
        } else {
            episodeDownloadButtonView.isVisible = true
            episodePlaybackViews.isGone = true
        }
    }


    /* Show the episode description */
    private fun showEpisodeDescription(context: Context, collectionDatabase: CollectionDatabase) {
        CoroutineScope(Dispatchers.IO).launch {
            val episodeDescription: EpisodeDescription? = collectionDatabase.episodeDescriptionDao().findByMediaId(episode.mediaId)
            val podcast: Podcast? = collectionDatabase.podcastDao().findByRemotePodcastFeedLocation(episode.episodeRemotePodcastFeedLocation)
            if (episodeDescription != null && podcast != null) {
                withContext(Dispatchers.Main) {
                    ShowNotesDialog().show(context, podcast, episode, episodeDescription)
                }
            }
        }
    }

}