/*
 * PodcastFragment.kt
 * Implements the PodcastFragment class
 * PodcastFragment is the fragment that displays the list of episodes for one podcast
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import org.y20k.escapepod.collection.EpisodeAdapter
import org.y20k.escapepod.collection.PodcastViewModel
import org.y20k.escapepod.collection.PodcastViewModelFactory
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.database.objects.PodcastDescription
import org.y20k.escapepod.dialogs.ErrorDialog
import org.y20k.escapepod.dialogs.YesNoDialog
import org.y20k.escapepod.helpers.DownloadHelper
import org.y20k.escapepod.helpers.NetworkHelper
import org.y20k.escapepod.interfaces.ListDragListener
import org.y20k.escapepod.interfaces.PlayerContainerStateChangeListener
import org.y20k.escapepod.interfaces.PodcastListener
import org.y20k.escapepod.interfaces.YesNoDialogListener
import org.y20k.escapepod.ui.PodcastFragmentLayoutHolder


/*
 * PodcastFragment class
 */
class PodcastFragment: Fragment(), PlayerContainerStateChangeListener, YesNoDialogListener {

    /* Main class variables */
    lateinit var layout: PodcastFragmentLayoutHolder
    private lateinit var collectionDatabase: CollectionDatabase
    private lateinit var podcastViewModel: PodcastViewModel
    private lateinit var episodeAdapter: EpisodeAdapter
    private lateinit var podcastName: String
    private lateinit var podcastCoverUriString: String
    private lateinit var remotePodcastFeedLocation: String
    private var episodeListDisplayFilter: Int = Keys.FILTER_SHOW_ALL
    private var filtered: Boolean = false
    private var episodeSearchTerm: String = String()
    private var adapterInitialized: Boolean = false


    /* Overrides onCreate from Fragment */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // get instance of database
        collectionDatabase = CollectionDatabase.getInstance(activity as Context)
    }


    /* Overrides onCreateView from Fragment */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        // find views and set them up
        val rootView: View = inflater.inflate(R.layout.fragment_podcast, container, false)
        layout = PodcastFragmentLayoutHolder(rootView, collectionDatabase, filtered)
        // set up the filter
        layout.filterButton.setOnClickListener { layout.toggleFilter() }
        layout.filterButton.setOnClickListener { layout.toggleFilter() }

        // enable for swipe to refresh
        layout.swipeRefreshLayout.setOnRefreshListener {
            // ask user to confirm update
            YesNoDialog(this@PodcastFragment as YesNoDialogListener).show(context = activity as Context, type = Keys.DIALOG_UPDATE_PODCAST, message = R.string.dialog_yes_no_message_update_episodes, yesButton = R.string.dialog_yes_no_positive_button_update_collection)
            layout.swipeRefreshLayout.isRefreshing = false
        }

        // set up toolbar
        val navController = findNavController()
        layout.toolbar.setupWithNavController(navController)
        return rootView
    }


    /* Overrides onViewCreated from Fragment */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }


    /* Overrides onResume from Fragment */
    override fun onResume() {
        super.onResume()
        handleNavigationArguments()
        // set up list drag listener
        val mainActivity = activity as? BaseMainActivity
        if (mainActivity != null) {
            layout.listDragListener = mainActivity.layout as ListDragListener
            mainActivity.layout.playerContainerStateChangeListener = this
        }
    }


    /* Overrides onPause from Fragment */
    override fun onPause() {
        super.onPause()
        // unregister list drag and player container state change listeners
        val mainActivity = activity as? BaseMainActivity
        if (mainActivity != null) {
            layout.listDragListener = null
            mainActivity.layout.playerContainerStateChangeListener = null
        }
    }

    
    /* Overrides onYesNoDialog from YesNoDialogListener */
    override fun onYesNoDialog(type: Int, dialogResult: Boolean, payload: Int, payloadString: String, dialogCancelled: Boolean) {
        when (type) {
            Keys.DIALOG_UPDATE_PODCAST -> {
                when (dialogResult) {
                    // user tapped update collection
                    true -> {
                        // note: check CollectionHelper.hasEnoughTimePassedSinceLastUpdate() is not necessary, users can update a single podcast as often as they want
                        updateEpisodes()
                    }
                    // user tapped cancel - for dev purposes: refresh the podcast list view // todo check if that can be helpful
                    false -> {
                        // collectionAdapter.notifyDataSetChanged() // can be removed
                    }
                }
            }
        }
    }


    /* Overrides onPlayerContainerStateChanged from PlayerContainerStateChangeListener */
    override fun onPlayerContainerStateChanged(playerVisible: Boolean, upNextVisible: Boolean) {
        layout.updateListBottomPadding(playerVisible, upNextVisible)
    }


    /* Handles arguments handed over by navigation (from PodcastListFragment) */
    private fun handleNavigationArguments() {
        // only initialize adapter once - subsequent onResume calls will be handled by LiveData observers
        if (!adapterInitialized) {
            if (arguments?.containsKey(Keys.ARG_PODCAST_NAME) == true) {
                podcastName = arguments?.getString(Keys.ARG_PODCAST_NAME) ?: String()
                if (podcastName.isNotEmpty()) {
                    layout.setPodcastName(podcastName)
                }
            }
            if (arguments?.containsKey(Keys.ARG_PODCAST_COVER) == true) {
                podcastCoverUriString = arguments?.getString(Keys.ARG_PODCAST_COVER) ?: String()
                if (podcastCoverUriString.isNotEmpty()) {
                    layout.setPodcastCover(podcastCoverUriString)
                }
            }
            if (arguments?.containsKey(Keys.ARG_EPISODE_LIST_FILTER) == true) {
                episodeListDisplayFilter =  arguments?.getInt(Keys.ARG_EPISODE_LIST_FILTER) ?: Keys.FILTER_SHOW_ALL
            }
            if (arguments?.containsKey(Keys.ARG_EPISODE_SEARCH_TERM) == true) {
                episodeSearchTerm = arguments?.getString(Keys.ARG_EPISODE_SEARCH_TERM) ?: String()
            }
            if (arguments?.containsKey(Keys.ARG_PODCAST_FEED) == true) {
                remotePodcastFeedLocation = arguments?.getString(Keys.ARG_PODCAST_FEED) ?: String()
                if (remotePodcastFeedLocation.isNotEmpty()) {
                    // create view model and observe changes in episode list view model
                    podcastViewModel = PodcastViewModelFactory((activity as Activity).application, remotePodcastFeedLocation).create(PodcastViewModel::class.java)
                    observeDetailViewModel(activity as LifecycleOwner)
                    episodeAdapter = EpisodeAdapter(activity as Activity, collectionDatabase, activity as PodcastListener, remotePodcastFeedLocation)
                    episodeAdapter.episodeListDisplayFilter = episodeListDisplayFilter
                    episodeAdapter.searchTerm = episodeSearchTerm
                    layout.episodesList.adapter = episodeAdapter
                    adapterInitialized = true
                }
            }
        }
    }


    /* Observe view model of podcast with details */
    private fun observeDetailViewModel(owner: LifecycleOwner) {
        podcastViewModel.podcastDetailLiveData.observe(owner, Observer<Podcast?> { newPodcast ->
            if (newPodcast != null) {
                // always sync state from database (single source of truth)
                episodeAdapter.episodeListDisplayFilter = newPodcast.episodeListDisplayFilter
                episodeAdapter.searchTerm = newPodcast.episodeListSearchTerm
                layout.restoreSearchState(newPodcast.episodeListSearchTerm)
                layout.podcast = newPodcast
                layout.updatePodcastViews()
            }
        })
        podcastViewModel.podcastDescriptionLiveData.observe(owner, Observer<PodcastDescription?> { newPodcastDescription ->
            if (newPodcastDescription != null) {
                layout.podcastDescription = newPodcastDescription
                layout.updatePodcastDescriptionView()
            }
        })
    }


    /* Updates the episodes of a podcast */
    private fun updateEpisodes() {
        if (NetworkHelper.isConnectedToNetwork(activity as Context)) {
            Toast.makeText(activity as Context, R.string.toast_message_updating_collection, Toast.LENGTH_LONG).show()
            DownloadHelper.updatePodcastEpisodes(activity as Context, remotePodcastFeedLocation)
        } else {
            ErrorDialog().show(activity as Context, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }

}
