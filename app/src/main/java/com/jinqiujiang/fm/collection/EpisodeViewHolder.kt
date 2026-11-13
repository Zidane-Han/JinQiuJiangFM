package com.jinqiujiang.fm.collection

import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.Group
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.jinqiujiang.fm.R
import com.jinqiujiang.fm.database.objects.Episode
import com.jinqiujiang.fm.helpers.DateTimeHelper
import com.jinqiujiang.fm.interfaces.PodcastListener

/*
 * EpisodeViewHolder class
 */
class EpisodeViewHolder (layout: View): RecyclerView.ViewHolder(layout) {
// class EpisodeViewHolder (val layout: View): RecyclerView.ViewHolder(layout) {  // testing ListItemLayout todo remove

    /* Main class variables */
    private val episodeCoverView: android.widget.ImageView = layout.findViewById(R.id.episode_cover)
    private val episodeDurationView: TextView = layout.findViewById(R.id.episode_duration)
    val episodeDateView: TextView = layout.findViewById(R.id.episode_date)
    val episodeTitleView: TextView = layout.findViewById(R.id.episode_title)
    val episodeDownloadButtonView: ImageButton = layout.findViewById(R.id.episode_download_button)
    val episodeDeleteButtonView: ImageButton = layout.findViewById(R.id.episode_delete_button)
    val episodePlayButtonView: ImageButton = layout.findViewById(R.id.episode_play_button)
    val episodePlaybackProgressView: ProgressBar = layout.findViewById(R.id.episode_playback_progress)
    val episodePlaybackViews: Group = layout.findViewById(R.id.episode_playback_views)
    lateinit var episode: Episode


    /* Initializes the view holder - call only after an episode has been set, the listeners read it */
    fun initialize(podcastListener: PodcastListener) {
        val openEpisode = View.OnClickListener { podcastListener.onEpisodeTapped(episode) }
        itemView.setOnClickListener(openEpisode)
        episodeCoverView.setOnClickListener(openEpisode)
        episodeDateView.setOnClickListener(openEpisode)
        episodeTitleView.setOnClickListener(openEpisode)
        episodeDurationView.setOnClickListener(openEpisode)
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
        val progress: Double = if (episode.duration <= 0L) 0.0 else (episode.duration.toDouble() - episode.playbackPosition.toDouble()) / episode.duration.toDouble() * 12
        episodePlaybackProgressView.progress = progress.toInt()
    }


    /* Sets up an episode's title views */
    fun setEpisodeTitle() {
        episodeDateView.text = DateTimeHelper.getDateString(episode.publicationDate)
        episodeTitleView.text = episode.title
        episodeDurationView.text = if (episode.duration > 0L) DateTimeHelper.convertToMinutesAndSeconds(episode.duration) else "—"
        episodeCoverView.contentDescription = episode.title
        com.bumptech.glide.Glide.with(episodeCoverView)
            .load(episode.remoteCoverFileLocation.ifBlank { episode.cover })
            .placeholder(R.drawable.podcast_cover).error(R.drawable.podcast_cover)
            .into(episodeCoverView)
    }


    /* Sets up an episode's play, download and delete button views (for CollectionAdapter) */
    fun setEpisodeButtons() {
        when (episode.isPlaying) {
            true -> episodePlayButtonView.setImageResource(R.drawable.ic_pause_symbol_24dp)
            false -> episodePlayButtonView.setImageResource(R.drawable.ic_play_symbol_24dp)
        }
        val downloaded = runCatching { java.io.File(android.net.Uri.parse(episode.audio).path ?: "").isFile }.getOrDefault(false)
        episodePlaybackViews.isVisible = true
        episodeDownloadButtonView.isVisible = !downloaded
        episodeDeleteButtonView.isVisible = downloaded

    }


}
