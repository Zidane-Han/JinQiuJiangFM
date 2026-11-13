/*
 * EpisodeAdapter.kt
 * Implements the EpisodeAdapter class
 * An EpisodeAdapter is a custom adapter providing views for all episodes of a podcast for a RecyclerView
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jinqiujiang.fm.collection

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jinqiujiang.fm.Keys
import com.jinqiujiang.fm.R
import com.jinqiujiang.fm.database.CollectionDatabase
import com.jinqiujiang.fm.database.objects.Episode
import com.jinqiujiang.fm.helpers.PreferencesHelper
import com.jinqiujiang.fm.interfaces.PodcastListener


/*
 * EpisodeAdapter class
 */
class EpisodeAdapter (private val activity: Activity, private val collectionDatabase: CollectionDatabase, private val podcastListener: PodcastListener, private val remotePodcastFeedLocation: String) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), Filterable {

    /* Define log tag */
    private val TAG: String = EpisodeAdapter::class.java.simpleName


    /* Main class variables */
    private lateinit var podcastViewModel: PodcastViewModel
    private var episodeList: List<Episode> = listOf()
    private var episodeListUnfiltered: List<Episode> = listOf()
    var episodeListDisplayFilter: Int = Keys.FILTER_SHOW_ALL
    var searchTerm: String = String()


    /* Overrides onAttachedToRecyclerView from RecyclerView.Adapter  */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        // create view model and observe changes in episode list view model
        podcastViewModel = PodcastViewModelFactory(activity.application, remotePodcastFeedLocation).create(PodcastViewModel::class.java)
        observeCollectionViewModel(activity as LifecycleOwner)
    }


    /* Overrides onCreateViewHolder from RecyclerView.Adapter  */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
//        val episodeView: ListItemLayout = LayoutInflater.from(parent.context).inflate(R.layout.list_element_episode, parent, false) as ListItemLayout // testing ListItemLayout todo remove
        val episodeView = LayoutInflater.from(parent.context).inflate(R.layout.element_episode, parent, false)
        val episodeViewHolder: EpisodeViewHolder = EpisodeViewHolder(episodeView)
        episodeViewHolder.initialize(podcastListener)
        return episodeViewHolder
    }


    /* Overrides onBindViewHolder from RecyclerView.Adapter  */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // get episode from position
        val episode = episodeList[position]
        // get reference to EpisodeViewHolder
        val episodeViewHolder: EpisodeViewHolder = holder as EpisodeViewHolder
        // set up episode
        episodeViewHolder.episode = episode
        episodeViewHolder.setEpisodeTitle()
        episodeViewHolder.setEpisodeButtons()
        episodeViewHolder.setEpisodePlaybackProgress()
//        // update the appearance of the list item (SINGLE_STATE_SET, FIRST_STATE_SET, LAST_STATE_SET, MIDDLE_STATE_SET) // testing ListItemLayout todo remove
//        if (itemCount != 0) {
//            (episodeViewHolder.layout as ListItemLayout).updateAppearance(position, itemCount)
//        }
    }


    /* Overrides getItemCount from RecyclerView.Adapter */
    override fun getItemCount(): Int {
        return episodeList.size
    }


    /* Overrides getFilter from Filterable */
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun convertResultToString(resultValue: Any?): CharSequence {
                return super.convertResultToString(resultValue)
            }

            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val charString: String = constraint?.toString() ?: String()
                when (charString) {
                    Keys.FILTER_SHOW_DOWNLOADED.toString() -> {
//                        filtered = true
                        episodeListDisplayFilter = Keys.FILTER_SHOW_DOWNLOADED
                        episodeList = applyAllFilters(episodeListUnfiltered)
                    }

                    else -> {
//                        filtered = false
                        episodeListDisplayFilter = Keys.FILTER_SHOW_ALL
                        episodeList = applyAllFilters(episodeListUnfiltered)
                    }
                }
                return FilterResults().apply { values = episodeList }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (results?.values != null) {
                    episodeList = results.values as List<Episode>
                    notifyDataSetChanged() // todo implement diffutil
                }
            }

        }
    }


    /* Applies search term filter and triggers redraw */
    fun applySearchTermFilter(newSearchTerm: String) {
        searchTerm = newSearchTerm
        val oldEpisodeList: List<Episode> = episodeList.map { it.copy() }
        episodeList = applyAllFilters(episodeListUnfiltered)
        val diffResult = DiffUtil.calculateDiff(EpisodeListDiffCallback(episodeList, oldEpisodeList), true)
        diffResult.dispatchUpdatesTo(this@EpisodeAdapter)
    }


    /* Applies all active filters (download state + search term) to the given list */
    private fun applyAllFilters(episodes: List<Episode>): List<Episode> {
        var result = episodes
        // apply download filter
        if (episodeListDisplayFilter == Keys.FILTER_SHOW_DOWNLOADED) {
            result = filterDownloadedEpisodes(result)
        }
        // apply search term filter
        if (searchTerm.isNotEmpty()) {
            result = filterBySearchTerm(result, searchTerm)
        }
        return result
    }


    /* Observe view model of podcast with details */
    private fun observeCollectionViewModel(owner: LifecycleOwner) {
        podcastViewModel.episodeListLiveData.observe(owner, Observer<List<Episode>> { newEpisodeList ->
            val sortedEpisodeList: List<Episode> = newEpisodeList.sortedByDescending { episode -> episode.publicationDate }
            updateRecyclerView(sortedEpisodeList)
        })
    }


    /* Filters episodes that have been downloaded */
    private fun filterDownloadedEpisodes(episodeList: List<Episode>): List<Episode> {
        return episodeList.filter { episode ->
            episode.audio.isNotEmpty()
        }
    }


    /* Filters episodes by search term in title */
    private fun filterBySearchTerm(episodeList: List<Episode>, searchTerm: String): List<Episode> {
        val lowerCaseSearchTerm = searchTerm.lowercase()
        return episodeList.filter { episode ->
            episode.title.lowercase().contains(lowerCaseSearchTerm)
        }
    }


    /* Updates the episode list - redraws the views with changed content */
    private fun updateRecyclerView(updatedEpisodeList: List<Episode>) {
        if (episodeListUnfiltered.isNotEmpty()) {
            val newEpisodeList: List<Episode>
            // store old episode list temporarily
            val oldEpisodeList: List<Episode> = episodeList.map { it.copy() }

            // set the unfiltered episode list
            episodeListUnfiltered = updatedEpisodeList

            // filter episode list, if necessary
            newEpisodeList = applyAllFilters(updatedEpisodeList)

            // set the filtered episode list
            episodeList = newEpisodeList

            // calculate differences between current episode list and old episode list - and inform this adapter about the changes
            val diffResult = DiffUtil.calculateDiff(EpisodeListDiffCallback(newEpisodeList, oldEpisodeList), true)
            diffResult.dispatchUpdatesTo(this@EpisodeAdapter)
        } else {
            // fist run: data set has been initialized - filter episode list, if necessary
            episodeListUnfiltered = updatedEpisodeList
            episodeList = applyAllFilters(updatedEpisodeList)
            notifyDataSetChanged()
        }
    }


    /*
     * Inner class: DiffUtil.Callback that determines changes in data - improves list performance
     */
    private inner class EpisodeListDiffCallback(val newEpisodeList: List<Episode>, val oldEpisodeList: List<Episode>): DiffUtil.Callback() {

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldEpisode: Episode = oldEpisodeList[oldItemPosition]
            val newEpisode: Episode = newEpisodeList[newItemPosition]
            return oldEpisode.guid == newEpisode.guid
        }

        override fun getOldListSize(): Int {
            return oldEpisodeList.size
        }

        override fun getNewListSize(): Int {
            return newEpisodeList.size
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldEpisode: Episode = oldEpisodeList[oldItemPosition]
            val newEpisode: Episode = newEpisodeList[newItemPosition]

            // compare relevant contents of podcast - check most likely changes fist
            if (oldEpisode.isPlaying != newEpisode.isPlaying) return false
            if (oldEpisode.playbackPosition != newEpisode.playbackPosition) return false
            if (oldEpisode.manuallyDownloaded != newEpisode.manuallyDownloaded) return false
            if (oldEpisode.audio != newEpisode.audio) return false
            // check the rest afterward
            if (oldEpisode.title != newEpisode.title) return false
            if (oldEpisode.publicationDate != newEpisode.publicationDate) return false
            if (oldEpisode.duration != newEpisode.duration) return false
            if (oldEpisode.remoteCoverFileLocation != newEpisode.remoteCoverFileLocation) return false
            if (oldEpisode.remoteAudioFileLocation != newEpisode.remoteAudioFileLocation) return false
//            // the next two lines are commented out, because episode covers are currently not displayed in list
//            if (FileHelper.getFileLastModified(oldEpisode.cover.toUri()) != FileHelper.getFileLastModified(newEpisode.cover.toUri())) return false
//            if (FileHelper.getFileLastModified(oldEpisode.smallCover.toUri()) != FileHelper.getFileLastModified(newEpisode.smallCover.toUri())) return false
            // none of the above -> contents are the same
            return true
        }
    }
    /*
     * End of inner class
     */
}
