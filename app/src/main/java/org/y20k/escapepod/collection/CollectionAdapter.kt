/*
 * CollectionAdapter.kt
 * Implements the CollectionAdapter class
 * A CollectionAdapter is a custom adapter providing podcast card views for a RecyclerView
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.collection

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.database.wrappers.EpisodeLatestView
import org.y20k.escapepod.database.wrappers.PodcastWithLatestEpisodeWrapper
import org.y20k.escapepod.dialogs.YesNoDialog
import org.y20k.escapepod.helpers.CollectionHelper
import org.y20k.escapepod.helpers.DownloadHelper
import org.y20k.escapepod.helpers.PreferencesHelper
import org.y20k.escapepod.interfaces.PodcastListener
import org.y20k.escapepod.interfaces.YesNoDialogListener
import java.io.File


/*
 * CollectionAdapter class
 */
class CollectionAdapter(private val context: Context, private val collectionDatabase: CollectionDatabase, private val podcastListener: PodcastListener, private val yesNoDialogListener: YesNoDialogListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /* Define log tag */
    private val TAG: String = CollectionAdapter::class.java.simpleName


    /* Main class variables */
    private lateinit var collectionViewModel: CollectionViewModel
    private var collection: List<PodcastWithLatestEpisodeWrapper> = listOf()
    private var expandedPodcastFeedLocation: String = PreferencesHelper.loadPodcastListExpandedFeedLocation()
    private var expandedPodcastPosition: Int = -1
    private var tapAnywherePlaybackEnabled: Boolean = PreferencesHelper.loadTapAnyWherePlayback()
    private var podcastHolderDrawn: Boolean = false
    // private var podcastCoverSize: Int = 0


    /* Overrides onAttachedToRecyclerView from RecyclerView.Adapter  */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        // create view model and observe changes in podcast list view model
        collectionViewModel = ViewModelProvider(context as AppCompatActivity)[CollectionViewModel::class.java]
        observeCollectionViewModel(context as LifecycleOwner)
        // start listening for changes in shared preferences
        PreferencesHelper.registerPreferenceChangeListener(sharedPreferenceChangeListener)
    }


    /* Overrides onDetachedFromRecyclerView from RecyclerView.Adapter  */
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        // stop listening for changes in shared preferences
        PreferencesHelper.unregisterPreferenceChangeListener(sharedPreferenceChangeListener)
    }


    /* Overrides onCreateViewHolder from RecyclerView.Adapter  */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        when (viewType) {
            Keys.VIEW_TYPE_ADD_NEW -> {
                // get view, put view into holder and return
                val addNewView: View = LayoutInflater.from(parent.context).inflate(R.layout.card_add_new_podcast, parent, false)
                return AddNewViewHolder(addNewView)
            }
            else -> {
                // get view, initialize view, put view into holder and return
                val podcastView: View = LayoutInflater.from(parent.context).inflate(R.layout.card_podcast, parent, false)
                val podcastViewHolder: PodcastViewHolder = PodcastViewHolder(podcastView)
                return podcastViewHolder
            }
        }
    }


    /* Overrides onBindViewHolder from RecyclerView.Adapter  */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        when (holder) {

            // CASE ADD NEW CARD
            is AddNewViewHolder -> {
                // AddNewViewHolder is set up in onCreateViewHolder
            }

            // CASE PODCAST CARD
            is PodcastViewHolder -> {
                // get podcast from position
                val podcast: PodcastWithLatestEpisodeWrapper = collection[position]
                // get reference to PodcastViewHolder
                val podcastViewHolder: PodcastViewHolder = holder as PodcastViewHolder
                // set up podcast
                setPodcastButtons(podcastViewHolder, podcast.data)
                podcastViewHolder.podcastNameView.text = podcast.data.name
                podcastViewHolder.podcastImageView.apply { transitionName = "podcast_cover_transition_${podcast.data.remotePodcastFeedLocation}" }
                // set podcast cover
                val coverImageFile: File = File(podcast.data.cover.toUri().path ?: "")
                Glide.with(context)
                    .load(podcast.data.cover)
                    .signature(ObjectKey(coverImageFile.lastModified()))
                    .placeholder(R.drawable.ic_default_cover_rss_icon_192dp)
                    .error(R.drawable.ic_default_cover_rss_icon_192dp)
                    .into(podcastViewHolder.podcastImageView)
                // set up recent episode
                if (podcast.episode != null) {
                    podcastViewHolder.currentEpisodeView.episode = podcast.episode.data
                    // the episode exists now - safe to attach the listeners that read it
                    podcastViewHolder.currentEpisodeView.initialize(context, collectionDatabase, podcastListener)
                    podcastViewHolder.currentEpisodeView.tapAnywherePlaybackEnabled = tapAnywherePlaybackEnabled
                    podcastViewHolder.currentEpisodeView.setEpisodeTitle()
                    podcastViewHolder.currentEpisodeView.setEpisodeButtons()
                    podcastViewHolder.currentEpisodeView.setEpisodePlaybackProgress()
                }
            }
        }
    }


    /* Overrides onBindViewHolder from RecyclerView.Adapter  */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else if (holder is PodcastViewHolder) {
            val podcast: PodcastWithLatestEpisodeWrapper = collection[position]
            val podcastViewHolder: PodcastViewHolder = holder as PodcastViewHolder
            // process each payload
            for (payload in payloads) {
                if (payload is List<*>) {
                    payload.forEach { changeType ->
                        when (changeType as? Int) {
                            Keys.HOLDER_UPDATE_COVER -> {
                                val coverImageFile: File = File(podcast.data.cover.toUri().path ?: "")
                                Glide.with(context)
                                    .load(podcast.data.cover)
                                    .signature(ObjectKey(coverImageFile.lastModified()))
                                    .placeholder(R.drawable.ic_default_cover_rss_icon_192dp)
                                    .error(R.drawable.ic_default_cover_rss_icon_192dp)
                                    .into(podcastViewHolder.podcastImageView)
                            }
                            Keys.HOLDER_UPDATE_NAME -> {
                                podcastViewHolder.podcastNameView.text = podcast.data.name
                            }
                            Keys.HOLDER_UPDATE_PLAYBACK_STATE -> {
                                if (podcast.episode != null) {
                                    podcastViewHolder.currentEpisodeView.episode = podcast.episode.data
                                    podcastViewHolder.currentEpisodeView.setEpisodeButtons()
                                }
                            }
                            Keys.HOLDER_UPDATE_PLAYBACK_PROGRESS -> {
                                if (podcast.episode != null) {
                                    podcastViewHolder.currentEpisodeView.episode = podcast.episode.data
                                    podcastViewHolder.currentEpisodeView.setEpisodePlaybackProgress()
                                }
                            }
                            Keys.HOLDER_UPDATE_DOWNLOAD_STATE -> {
                                if (podcast.episode != null) {
                                    podcastViewHolder.currentEpisodeView.episode = podcast.episode.data
                                    podcastViewHolder.currentEpisodeView.setEpisodeButtons()
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    /* Overrides getItemViewType from RecyclerView.Adapter  */
    override fun getItemViewType(position: Int): Int {
        when (isPositionFooter(position)) {
            true -> return Keys.VIEW_TYPE_ADD_NEW
            false -> return Keys.VIEW_TYPE_PODCAST
        }
    }


    /* Overrides getItemCount from RecyclerView.Adapter  */
    override fun getItemCount(): Int {
        // +1 ==> the add podcast card
        return collection.size + 1
    }


    /* Removes a podcast from collection */
    fun removePodcast(context: Context, position: Int) {
        // delete folders and assets
        CollectionHelper.deletePodcastFolders(context, collection[position].data)
        // remove podcast from collection
        collectionViewModel.removePodcast(collection[position].data.remotePodcastFeedLocation)
    }


    /* Show the remove podcast dialog */
    fun showRemovePodcastDialog(yesNoDialogListener: YesNoDialogListener, position: Int) {
        val dialogMessage: String = "${context.getString(R.string.dialog_yes_no_message_remove_podcast)}\n\n- ${collection[position].data.name}"
        YesNoDialog(yesNoDialogListener).show(context = context, type = Keys.DIALOG_REMOVE_PODCAST, messageString = dialogMessage, yesButton = R.string.dialog_yes_no_positive_button_remove_podcast, payload = position)
    }


//    /* Deletes an episode download from collection */
//    fun deleteEpisode(mediaId: String) {
//        Log.v(TAG, "Deleting episode: $mediaId")
//        // remove audio reference in database
//        listViewModel.deleteEpisodeAudio(mediaId)
//    }


    /* Marks an episode as played in collection */
    fun markEpisodePlayed(mediaId: String) {
        Log.v(TAG, "Marking as played episode: $mediaId")
        // mark episode als played and update collection
        collectionViewModel.markEpisodePlayed(mediaId)
    }


    /* Get podcast for given position */
    fun getPodcast(position: Int): PodcastWithLatestEpisodeWrapper {
        return collection[position]
    }


    /* Determines if position is last */
    private fun isPositionFooter(position: Int): Boolean {
        return position == collection.size
    }


    /* Sets up click listeners */
    private fun setPodcastButtons(podcastViewHolder: PodcastViewHolder, podcast: Podcast) {
        // listen for tap on the podcast card
        podcastViewHolder.podcastCardView.setOnClickListener {
            podcastListener.onPodcastCardTapped()
        }
        // listen for right arrow key
        podcastViewHolder.podcastCardView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        showRemovePodcastDialog(yesNoDialogListener, podcastViewHolder.adapterPosition)
                        return@setOnKeyListener true
                    }
                    else -> return@setOnKeyListener false
                }
            } else return@setOnKeyListener false
        }
        // refresh podcast cover on long click
        podcastViewHolder.podcastImageView.setOnLongClickListener {
            DownloadHelper.refreshCover(context, podcast)
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return@setOnLongClickListener true
        }
        // go to all episodes list on click
        // podcast_cover_transition and podcast_name_transition are defined in fragment_podcast.xml // todo remove if this costs performance
        val navigateToPodcastFragmentsArgs = bundleOf(
            Keys.ARG_PODCAST_FEED to podcast.remotePodcastFeedLocation,
            Keys.ARG_PODCAST_NAME to podcast.name,
            Keys.ARG_PODCAST_COVER to podcast.cover,
            Keys.ARG_EPISODE_LIST_FILTER to podcast.episodeListDisplayFilter,
            Keys.ARG_EPISODE_SEARCH_TERM to podcast.episodeListSearchTerm
        )
        val fragmentNavigatorExtras = FragmentNavigatorExtras(podcastViewHolder.podcastImageView to "podcast_cover_transition_${podcast.remotePodcastFeedLocation}")
        podcastViewHolder.allEpisodesButtonView.setOnClickListener {it.findNavController().navigate(R.id.podcast_fragment_destination, navigateToPodcastFragmentsArgs, null, fragmentNavigatorExtras)}
        podcastViewHolder.podcastImageView.setOnClickListener {it.findNavController().navigate(R.id.podcast_fragment_destination, navigateToPodcastFragmentsArgs, null, fragmentNavigatorExtras)}
    }


    /* Updates the podcast list - redraws the views with changed content */
    private fun updateRecyclerView(newCollection: List<PodcastWithLatestEpisodeWrapper>) {
        if (collection.isEmpty() && newCollection.isNotEmpty()) {
            // data set has been initialized - redraw the whole list
            collection = newCollection
            notifyDataSetChanged()
        } else {
            // store old collection temporarily
            val oldCollection: List<PodcastWithLatestEpisodeWrapper> = collection.map { it.copy() }
            // set new collection
            collection = newCollection
            // calculate differences between current collection and old collection - and inform this adapter about the changes
            val diffResult = DiffUtil.calculateDiff(CollectionDiffCallback(oldCollection), true)
            diffResult.dispatchUpdatesTo(this@CollectionAdapter)
        }
    }


    /* Updates and saves state of expanded podcast card in list */
    private fun savePodcastListExpandedState(position: Int = -1, remotePodcastFeedLocation: String = String()) {
        expandedPodcastFeedLocation = remotePodcastFeedLocation
        expandedPodcastPosition = position
        PreferencesHelper.savePodcastListExpandedFeedLocation(expandedPodcastFeedLocation)
    }


    /* Observe view model of podcast list */
    private fun observeCollectionViewModel(owner: LifecycleOwner) {
        collectionViewModel.podcastListLiveData.observe(owner, Observer<List<PodcastWithLatestEpisodeWrapper>> { newCollection ->
            //Log.v(TAG, "Time to load episode list from database: ${System.currentTimeMillis() - collectionViewModel.initializedTimestamp} ms.")
            val sortedCollection: List<PodcastWithLatestEpisodeWrapper> = newCollection.sortedByDescending { podcast -> podcast.data.latestEpisodeDate }
            updateRecyclerView(sortedCollection)
        } )
    }


    /*
     * Defines the listener for changes in shared preferences
     */
    private val sharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            Keys.PREF_TAP_ANYWHERE_PLAYBACK -> tapAnywherePlaybackEnabled = PreferencesHelper.loadTapAnyWherePlayback()
        }
    }
    /*
     * End of declaration
     */


    /*
     * Inner class: ViewHolder for the Add New podcast action
     */
    private inner class AddNewViewHolder (listItemAddNewLayout: View) : RecyclerView.ViewHolder(listItemAddNewLayout) {
        val addNewPodcastButtonView: MaterialButton = listItemAddNewLayout.findViewById(R.id.card_add_new_podcast)
        val settingsButtonView: MaterialButton = listItemAddNewLayout.findViewById(R.id.card_settings)

        init {
            addNewPodcastButtonView.setOnClickListener {
                // show the add podcast dialog
                podcastListener.onAddNewButtonTapped()
            }
            settingsButtonView.setOnClickListener {
                // navigate to the settings screen
                settingsButtonView.findNavController().navigate(R.id.settings_fragment_destination)
            }
        }

    }
    /*
     * End of inner class
     */


    /*
     * Inner class: ViewHolder for the podcast card
     */
    private inner class PodcastViewHolder (podcastCardLayout: View): RecyclerView.ViewHolder(podcastCardLayout) {
        val podcastCardView: MaterialCardView = podcastCardLayout.findViewById(R.id.podcast_card)
        val podcastImageView: ImageView = podcastCardLayout.findViewById(R.id.podcast_cover)
        val podcastNameView: TextView = podcastCardLayout.findViewById(R.id.podcast_name)
        val currentEpisodeView: EpisodeViewHolder = EpisodeViewHolder(podcastCardLayout)
        val allEpisodesButtonView: MaterialButton = podcastCardLayout.findViewById(R.id.older_episodes_toggle)
    }
    /*
     * End of inner class
     */


    /*
     * Inner class: DiffUtil.Callback that determines changes in data - improves list performance
     */
    private inner class CollectionDiffCallback(val oldCollection: List<PodcastWithLatestEpisodeWrapper>): DiffUtil.Callback() {

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldPodcast: PodcastWithLatestEpisodeWrapper = oldCollection[oldItemPosition]
            val newPodcast: PodcastWithLatestEpisodeWrapper = collection[newItemPosition]
            return oldPodcast.data.remotePodcastFeedLocation == newPodcast.data.remotePodcastFeedLocation
        }

        override fun getOldListSize(): Int {
            return oldCollection.size
        }

        override fun getNewListSize(): Int {
            return collection.size
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            
            var result: Boolean = true
            val oldPodcast: PodcastWithLatestEpisodeWrapper = oldCollection[oldItemPosition]
            val newPodcast: PodcastWithLatestEpisodeWrapper = collection[newItemPosition]

            // compare relevant contents of podcast
            if (oldPodcast.data.name != newPodcast.data.name) result = false
            if (oldPodcast.data.website != newPodcast.data.website) result = false
            if (oldPodcast.data.remoteImageFileLocation != newPodcast.data.remoteImageFileLocation) result = false
            if (oldPodcast.data.remotePodcastFeedLocation == expandedPodcastFeedLocation) result = false // todo test and remove again if necessary
            if (oldPodcast.data.coverVersion != newPodcast.data.coverVersion) result = false

            // compare relevant contents of recent episode - check most likely changes fist
            if (oldPodcast.episode != null && newPodcast.episode != null) {
                val oldEpisode: EpisodeLatestView = oldPodcast.episode
                val newEpisode: EpisodeLatestView = newPodcast.episode
                // check most likely changes fist
                if (oldEpisode.data.isPlaying != newEpisode.data.isPlaying) result = false
                if (oldEpisode.data.playbackPosition != newEpisode.data.playbackPosition) result = false
                if (oldEpisode.data.manuallyDownloaded != newEpisode.data.manuallyDownloaded) result = false
                if (oldEpisode.data.audio != newEpisode.data.audio) result = false
                // check the rest afterwards
                if (oldEpisode.data.guid != newEpisode.data.guid) result = false
                if (oldEpisode.data.title != newEpisode.data.title) result = false
                //if (oldEpisode.chapters != newEpisode.chapters) result = false
                if (oldEpisode.data.publicationDate != newEpisode.data.publicationDate) result = false
                if (oldEpisode.data.duration != newEpisode.data.duration) result = false
                if (oldEpisode.data.remoteCoverFileLocation != newEpisode.data.remoteCoverFileLocation) result = false
                if (oldEpisode.data.remoteAudioFileLocation != newEpisode.data.remoteAudioFileLocation) result = false
                if (oldEpisode.data.coverVersion != newEpisode.data.coverVersion) result = false
            }

            // none of the above -> contents are the same
            return result
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            val oldPodcast: PodcastWithLatestEpisodeWrapper = oldCollection[oldItemPosition]
            val newPodcast: PodcastWithLatestEpisodeWrapper = collection[newItemPosition]

            val diffBundle = mutableListOf<Int>()

            // check what changed and add payloads
            if (oldPodcast.data.coverVersion != newPodcast.data.coverVersion) {
                diffBundle.add(Keys.HOLDER_UPDATE_COVER)
            }
            if (oldPodcast.data.name != newPodcast.data.name) {
                diffBundle.add(Keys.HOLDER_UPDATE_NAME)
            }
            if (oldPodcast.episode != null && newPodcast.episode != null) {
                val oldEpisode = oldPodcast.episode
                val newEpisode = newPodcast.episode
                // episode changed entirely - force full rebind
                if (oldEpisode.data.guid != newEpisode.data.guid) return null
                // same episode - determine which parts of the view need to update
                if (oldEpisode.data.isPlaying != newEpisode.data.isPlaying) {
                    diffBundle.add(Keys.HOLDER_UPDATE_PLAYBACK_STATE)
                }
                if (oldEpisode.data.playbackPosition != newEpisode.data.playbackPosition || oldEpisode.data.duration != newEpisode.data.duration) {
                    diffBundle.add(Keys.HOLDER_UPDATE_PLAYBACK_PROGRESS)
                }
                if (oldEpisode.data.audio != newEpisode.data.audio || oldEpisode.data.manuallyDownloaded != newEpisode.data.manuallyDownloaded) {
                    diffBundle.add(Keys.HOLDER_UPDATE_DOWNLOAD_STATE)
                }
            }
            return if (diffBundle.isEmpty()) null else diffBundle
        }
    }
    /*
     * End of inner class
     */
    
}