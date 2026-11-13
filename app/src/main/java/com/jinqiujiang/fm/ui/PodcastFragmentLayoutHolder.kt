/*
 * PodcastFragmentLayoutHolder.kt
 * Implements the PodcastFragmentLayoutHolder class
 * A PodcastFragmentLayoutHolder hold references to the views of the PodcastFragment
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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view.inputmethod.EditorInfo
import android.widget.Filterable
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jinqiujiang.fm.BaseMainActivity
import com.jinqiujiang.fm.Keys
import com.jinqiujiang.fm.R
import com.jinqiujiang.fm.collection.EpisodeAdapter
import com.jinqiujiang.fm.database.CollectionDatabase
import com.jinqiujiang.fm.database.objects.Podcast
import com.jinqiujiang.fm.database.objects.PodcastDescription
import com.jinqiujiang.fm.dialogs.DescriptionDialog
import com.jinqiujiang.fm.extensions.setCleanHtml
import com.jinqiujiang.fm.helpers.DownloadHelper
import com.jinqiujiang.fm.helpers.UiHelper
import com.jinqiujiang.fm.interfaces.ListDragListener
import java.io.File


/*
 * LayoutHolder class
 */
data class PodcastFragmentLayoutHolder(val rootView: View, val collectionDatabase: CollectionDatabase, private val initialFilteredState: Boolean) {

    /* Define log tag */
    private val TAG: String = PodcastFragmentLayoutHolder::class.java.simpleName


    /* Main class variables */
    private lateinit var systemBars: Insets
    val swipeRefreshLayout: SwipeRefreshLayout
    val toolbar: MaterialToolbar
    val episodesList: RecyclerView
    val searchButton: ImageButton
    val filterButton: ImageButton
    val searchTextField: TextInputEditText
    val searchTermChip: Chip
    val coverView: ImageView
    var listDragListener: ListDragListener? = null
    lateinit var podcast: Podcast
    private val layoutManager: LinearLayoutManager
    private val scroller: RecyclerView.SmoothScroller
    private val podcastBrandTitleView: MaterialTextView
    private var podcastCoverUriString: String = String()


    /* Init block */
    init {
        // find views
        swipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh_podcast_layout)
        toolbar = rootView.findViewById<MaterialToolbar>(R.id.toolbar)
        episodesList = rootView.findViewById(R.id.episodes_list)
        searchButton = rootView.findViewById(R.id.search_icon)
        filterButton = rootView.findViewById(R.id.filter_icon)
        searchTextField = rootView.findViewById(R.id.search_text_field)
        searchTermChip = rootView.findViewById(R.id.search_term_chip)
        coverView = rootView.findViewById(R.id.podcast_cover)
        podcastBrandTitleView = rootView.findViewById(R.id.podcast_brand_title)

        // set up RecyclerView
        layoutManager = CustomLayoutManager(rootView.context)
        episodesList.layoutManager = layoutManager
        val animator: SimpleItemAnimator = DefaultItemAnimator()
        animator.supportsChangeAnimations = true
        episodesList.itemAnimator = animator
        episodesList.setHasFixedSize(true)

        // customize scroller
        scroller = object : LinearSmoothScroller(rootView.context) {
            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }
        }

        // add scroll listener to detect when list is being dragged
        episodesList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                listDragListener?.onListDragStateChanged(newState)
            }
        })

        // set up filter and search UI
        updateFilterButtonIcon(filtered = initialFilteredState, showToast = false)
        setupSearchUI()

        // set up edge to edge display
        setupEdgeToEdge()
    }


    /* Scrolls recycler list to top */
    fun scrollToTop() {
        scroller.targetPosition = 0
        layoutManager.startSmoothScroll(scroller)
    }


    /* Updates the podcast views */
    @SuppressLint("ClickableViewAccessibility")
    fun updatePodcastViews() {
        // set podcast name and cover (if they have not been set earlier)
        setPodcastName(podcast.name)
        setPodcastCover(podcast.cover)
        // set up podcast cover view
        coverView.contentDescription = "${rootView.context.getString(R.string.descr_player_podcast_cover)}: ${podcast.name}"
        coverView.transitionName = "podcast_cover_transition_${podcast.remotePodcastFeedLocation}"
        coverView.setOnLongClickListener {
            DownloadHelper.refreshCover(rootView.context, podcast)
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return@setOnLongClickListener true
        }
        // update the filter button icon
        updateFilterButtonIcon(filtered = podcast.episodeListDisplayFilter == Keys.FILTER_SHOW_DOWNLOADED)
    }


    /* Toggles the downloaded episodes filter */
    fun toggleFilter() {
        if (this::podcast.isInitialized) {
            // determine the new filter
            val displayDownloadOnly = podcast.episodeListDisplayFilter == Keys.FILTER_SHOW_DOWNLOADED
            val newFilter = if (displayDownloadOnly) Keys.FILTER_SHOW_ALL else Keys.FILTER_SHOW_DOWNLOADED

            // update in-memory state immediately to prevent race conditions
            podcast = podcast.copy(episodeListDisplayFilter = newFilter)

            // store filter state in database and update UI
            CoroutineScope(Main).launch {
                withContext(IO) {
                    collectionDatabase.podcastDao()
                        .updateEpisodeListFilter(podcast.remotePodcastFeedLocation, newFilter)
                }
                updateFilterButtonIcon(filtered = !displayDownloadOnly, showToast = true)
                filterEpisodeList(newFilter)
            }
        }
    }


    /* Restores the search UI state from a persisted search term */
    fun restoreSearchState(searchTerm: String) {
        if (searchTerm.isNotEmpty()) {
            searchTermChip.text = searchTerm
            searchTermChip.isVisible = true
            searchTextField.setText(searchTerm)
        }
    }


    /* Update the downloaded episodes filter buton icon */
    private fun updateFilterButtonIcon(filtered: Boolean, showToast: Boolean = false) {
        if (filtered) {
            filterButton.setImageResource(R.drawable.ic_filter_filled_24dp)
            if (showToast) Toast.makeText(rootView.context, R.string.toast_message_episode_list_filter_downloaded, Toast.LENGTH_LONG).show()
        } else {
            filterButton.setImageResource(R.drawable.ic_filter_24dp)
            if (showToast) Toast.makeText(rootView.context, R.string.toast_message_episode_list_filter_all, Toast.LENGTH_LONG).show()
        }
    }


    /* Filter the episode list */
    private fun filterEpisodeList(filter: Int) {
        val adapter: Filterable = episodesList.adapter as Filterable
        adapter.filter.filter(filter.toString())
    }


    /* Search/filter the episode list by search term */
    private fun searchEpisodeList(searchTerm: String) {
        val adapter = episodesList.adapter
        if (adapter is EpisodeAdapter) {
            adapter.applySearchTermFilter(searchTerm)
        }
    }


    /* Sets up search UI */
    private fun setupSearchUI() {
        // toggle search text field visibility
        searchButton.setOnClickListener {
            if (searchTextField.isVisible || searchTermChip.isVisible) {
                searchTextField.isInvisible = true
                searchTermChip.isInvisible = true
                searchTextField.text?.clear()
                UiHelper.hideSoftKeyboard(rootView.context, searchTextField)
                searchEpisodeList(String())
                storeSearchTermInDatabase(String())
            } else {
                searchTermChip.isInvisible = true
                searchTextField.isVisible = true
                searchTextField.requestFocus()
                UiHelper.showSoftKeyboard(rootView.context, searchTextField)
            }
        }

        // submit search term via IME search action
        searchTextField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearchTerm()
                true
            } else {
                false
            }
        }

        // submit search term via hardware keyboard Enter key
        searchTextField.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                submitSearchTerm()
                true
            } else {
                false
            }
        }

        // close chip clears search term
        searchTermChip.setOnCloseIconClickListener {
            searchTermChip.isInvisible = true
            searchTextField.text?.clear()
            searchEpisodeList(String())
            storeSearchTermInDatabase(String())
        }
    }


    /* Submits the search term from text field to chip and filters episode list */
    private fun submitSearchTerm() {
        val searchTerm = searchTextField.text?.toString().orEmpty().trim().take(Keys.SEARCH_TERM_CHARACTER_LIMIT)
        if (searchTerm.isNotEmpty()) {
            searchTermChip.text = searchTerm
            searchTermChip.isVisible = true
            searchTextField.isInvisible = true
            searchEpisodeList(searchTerm)
            storeSearchTermInDatabase(searchTerm)
        }
        UiHelper.hideSoftKeyboard(rootView.context, searchTextField)
    }


    /* Persists the search term to the database */
    private fun storeSearchTermInDatabase(searchTerm: String) {
        if (this::podcast.isInitialized) {
            CoroutineScope(IO).launch {
                collectionDatabase.podcastDao().updateEpisodeListSearchTerm(podcast.remotePodcastFeedLocation, searchTerm)
            }
        }
    }


    /* The brand name belongs beside the logo; the toolbar only contains actions. */
    fun setPodcastName(podcastName: String) {
        podcastBrandTitleView.text = com.jinqiujiang.fm.PodcastConfig.NAME
        toolbar.title = ""
    }


    /* Sets the podcast cover */
    fun setPodcastCover(newPodcastCoverUriString: String) {
        val coverImageFile: File = File(newPodcastCoverUriString.toUri().path ?: "")
        Glide.with(rootView.context)
            .load(newPodcastCoverUriString)
            .signature(ObjectKey(coverImageFile.lastModified()))
            .error(R.drawable.podcast_cover)
            .into(coverView)
        podcastCoverUriString = newPodcastCoverUriString
    }


    /* Updates the bottom padding of the episodes list based on player and up next visibility */
    fun updateListBottomPadding(playerVisible: Boolean, upNextVisible: Boolean) {
        if (this::systemBars.isInitialized) {
            episodesList.updatePadding(bottom = systemBars.bottom + UiHelper.getPlayerContainerHeight(rootView.context, playerVisible, upNextVisible))
        }
    }


    /* Sets up margins/paddings for edge to edge view */
    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            // get measurements for status and navigation bar
            systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // apply measurements to toolbar
            toolbar.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = systemBars.top
            }
            // apply measurements to episodes list
            val mainActivity = rootView.context as? BaseMainActivity
            val playerVisible = mainActivity?.layout?.playerCardView?.isVisible ?: true
            val upNextVisible = mainActivity?.layout?.upNextCardView?.isVisible ?: false
            episodesList.updatePadding(bottom = systemBars.bottom + UiHelper.getPlayerContainerHeight(rootView.context, playerVisible, upNextVisible))
            // return the insets
            insets
        }
    }


    /*
     * Inner class: Custom LinearLayoutManager
     */
    private inner class CustomLayoutManager(context: Context): LinearLayoutManager(context, VERTICAL, false) {
        override fun supportsPredictiveItemAnimations(): Boolean {
            return true
        }
    }
    /*
     * End of inner class
     */
}