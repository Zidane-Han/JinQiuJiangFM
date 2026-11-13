/*
 * MainFragment.kt
 * Implements the MainFragment class
 * MainFragment is the fragment that hosts Escapepod's list of podcasts
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jinqiujiang.fm

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jinqiujiang.fm.collection.CollectionAdapter
import com.jinqiujiang.fm.collection.CollectionViewModel
import com.jinqiujiang.fm.database.CollectionDatabase
import com.jinqiujiang.fm.database.objects.Episode
import com.jinqiujiang.fm.dialogs.ErrorDialog
import com.jinqiujiang.fm.dialogs.YesNoDialog
import com.jinqiujiang.fm.helpers.CollectionHelper
import com.jinqiujiang.fm.helpers.DownloadHelper
import com.jinqiujiang.fm.helpers.NetworkHelper
import com.jinqiujiang.fm.helpers.PreferencesHelper
import com.jinqiujiang.fm.helpers.UiHelper
import com.jinqiujiang.fm.interfaces.ListDragListener
import com.jinqiujiang.fm.interfaces.PlayerContainerStateChangeListener
import com.jinqiujiang.fm.interfaces.PodcastListener
import com.jinqiujiang.fm.interfaces.YesNoDialogListener
import com.jinqiujiang.fm.ui.MainFragmentLayoutHolder
import com.jinqiujiang.fm.ui.PlayerState


/*
 * MainFragment class
 */
class MainFragment : Fragment(), PlayerContainerStateChangeListener, SharedPreferences.OnSharedPreferenceChangeListener, YesNoDialogListener {


    /* Define log tag */
    private val TAG: String = MainFragment::class.java.simpleName


    /* Main class variables */
    lateinit var layout: MainFragmentLayoutHolder
    private lateinit var collectionDatabase: CollectionDatabase
    private lateinit var collectionViewModel: CollectionViewModel
    private lateinit var collectionAdapter: CollectionAdapter
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController? get() = if (this::controllerFuture.isInitialized && controllerFuture.isDone) controllerFuture.get() else null // defines the Getter for the MediaController
    private var listLayoutState: Parcelable? = null


    /* Overrides onCreate from Fragment */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // get instance of database
        collectionDatabase = CollectionDatabase.getInstance(requireContext())

        // create view model and observe changes in collection view model
        collectionViewModel = ViewModelProvider(this)[CollectionViewModel::class.java]

        // create podcast list adapter
        collectionAdapter = CollectionAdapter(requireContext(), collectionDatabase, activity as PodcastListener, activity as YesNoDialogListener)
    }


    /* Overrides onCreateView from Fragment*/
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // hide action bar
        (activity as AppCompatActivity).supportActionBar?.hide()
        // find views and set them up
        val rootView: View = inflater.inflate(R.layout.fragment_main, container, false)
        layout = MainFragmentLayoutHolder(rootView, collectionDatabase)
        // set up views and connect tap listeners
        initializeViews()

        return rootView
    }


    /* Overrides onSaveInstanceState from Fragment */
    override fun onSaveInstanceState(outState: Bundle) {
        if (this::layout.isInitialized) {
            // save current state of podcast list
            listLayoutState = layout.layoutManager.onSaveInstanceState()
            outState.putParcelable(Keys.KEY_SAVE_INSTANCE_STATE_PODCAST_LIST, listLayoutState)
        }
        // always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(outState)
    }


    /* Overrides onRestoreInstanceState from Activity */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // always call the superclass so it can restore the view hierarchy
        listLayoutState = savedInstanceState?.getParcelable(Keys.KEY_SAVE_INSTANCE_STATE_PODCAST_LIST)
    }


    /* Overrides onResume from Fragment */
    override fun onResume() {
        super.onResume()
        updatePodcastListState()
        // handle navigation arguments
        // begin looking for changes in podcast list
        observeListViewModel()
        // start watching for changes in shared preferences
        PreferencesHelper.registerPreferenceChangeListener(this as SharedPreferences.OnSharedPreferenceChangeListener)
        // register list drag and player container state change listeners
        val mainActivity = activity as? BaseMainActivity
        if (mainActivity != null) {
            layout.listDragListener = mainActivity.layout as ListDragListener
            mainActivity.layout.playerContainerStateChangeListener = this
        }
    }


    /* Overrides onPause from Fragment */
    override fun onPause() {
        super.onPause()
        // stop watching for changes in shared preferences
        PreferencesHelper.unregisterPreferenceChangeListener(this as SharedPreferences.OnSharedPreferenceChangeListener)
        // unregister list drag and player container state change listeners
        val mainActivity = activity as? BaseMainActivity
        if (mainActivity != null) {
            layout.listDragListener = null
            mainActivity.layout.playerContainerStateChangeListener = null
        }
    }


    /* Overrides onSharedPreferenceChanged from OnSharedPreferenceChangeListener */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Keys.PREF_ACTIVE_DOWNLOADS -> {
                // scroll to top when after 2 seconds there are no active downloads
                Handler(Looper.getMainLooper()).postDelayed({
                    if (PreferencesHelper.isActiveDownloadsEmpty()) layout.scrollToTop()
                }, 2000)
            }
        }
    }


    /* Overrides onYesNoDialog from YesNoDialogListener */
    override fun onYesNoDialog(type: Int, dialogResult: Boolean, payload: Int, payloadString: String, dialogCancelled: Boolean) {
        when (type) {
            Keys.DIALOG_UPDATE_COLLECTION -> {
                when (dialogResult) {
                    // user tapped update collection
                    true -> {
                        if (CollectionHelper.hasEnoughTimePassedSinceLastUpdate()) {
                            updatePodcastCollection()
                        } else {
                            Toast.makeText(requireContext(), R.string.toast_message_collection_update_not_necessary, Toast.LENGTH_LONG).show()
                        }
                    }
                    // user tapped cancel - for dev purposes: refresh the podcast list view // todo check if that can be helpful
                    false -> {
                        // collectionAdapter.notifyDataSetChanged() // can be removed
                    }
                }
            }
            // handle result of remove dialog // todo check if move to MainActivity makes sense
            Keys.DIALOG_REMOVE_PODCAST -> {
                when (dialogResult) {
                    // user tapped remove podcast
                    true -> {
                        // if (controller?.isPlaying == true) controller?.pause() // todo pause playback if currently playing episode belongs to the to be removed removed podcast
                        collectionAdapter.removePodcast(requireContext(), payload)
                    }
                    // user tapped cancel
                    false -> {
                        collectionAdapter.notifyItemChanged(payload)
                    }
                }
            }
        }
    }


    /* Overrides onPlayerContainerStateChanged from PlayerContainerStateChangeListener */
    override fun onPlayerContainerStateChanged(playerVisible: Boolean, upNextVisible: Boolean) {
        layout.updateListBottomPadding(playerVisible, upNextVisible)
    }


    /* Sets up views and connects tap listeners - first run */
    private fun initializeViews() {
        // set adapter data source
        layout.podcastList.adapter = collectionAdapter

        // enable swipe to delete
        val swipeHandler = object : UiHelper.SwipeToDeleteCallback(requireContext()) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val adapterPosition: Int = viewHolder.adapterPosition
                val podcast = collectionAdapter.getPodcast(adapterPosition)
                // stop playback, if necessary // todo check if correct
                CoroutineScope(IO).launch {
                    val playerState: PlayerState = (activity as MainActivity).playerState
                    val currentEpisode: Episode? = collectionDatabase.episodeDao().findByMediaId(playerState.currentEpisodeMediaId)
                    withContext(Main) {
                        // todo check if this works
                        if (currentEpisode?.episodeRemotePodcastFeedLocation.equals(podcast.data.remotePodcastFeedLocation)) {
                            controller?.pause()
                        }
                    }
                }
                // ask user
                collectionAdapter.showRemovePodcastDialog(this@MainFragment as YesNoDialogListener, adapterPosition)
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(layout.podcastList)

        // enable for swipe to refresh
        layout.swipeRefreshLayout.setOnRefreshListener {
            // ask user to confirm update
            YesNoDialog(this@MainFragment as YesNoDialogListener).show(context = requireContext(), type = Keys.DIALOG_UPDATE_COLLECTION, message = R.string.dialog_yes_no_message_update_episodes, yesButton = R.string.dialog_yes_no_positive_button_update_collection)
            layout.swipeRefreshLayout.isRefreshing = false
        }
    }


    /* Sets up state of list podcast list */
    private fun updatePodcastListState() {
        if (listLayoutState != null) {
            layout.layoutManager.onRestoreInstanceState(listLayoutState)
        }
    }


    /* Updates podcast collection */
    private fun updatePodcastCollection() {
        if (NetworkHelper.isConnectedToNetwork(requireContext())) {
            Toast.makeText(requireContext(), R.string.toast_message_updating_collection, Toast.LENGTH_LONG).show()
            DownloadHelper.updateCollection(requireContext())
        } else {
            ErrorDialog().show(requireContext(), R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Observe view model of podcast collection */
    private fun observeListViewModel() {
        collectionViewModel.numberOfPodcastsLiveData.observe(this, Observer<Int> { numberOfPodcasts ->
            layout.toggleOnboarding(numberOfPodcasts)
        })
    }

}