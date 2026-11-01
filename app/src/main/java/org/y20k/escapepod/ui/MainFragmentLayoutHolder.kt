/*
 * MainFragmentLayoutHolder.kt
 * Implements the MainFragmentLayoutHolder class
 * A MainFragmentLayoutHolder holds references to the views of the MainFragment
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.ui

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.y20k.escapepod.BaseMainActivity
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.helpers.UiHelper
import org.y20k.escapepod.interfaces.ListDragListener


/*
 * MainFragmentLayoutHolder class
 */
data class MainFragmentLayoutHolder(val rootView: View, val collectionDatabase: CollectionDatabase) {

    /* Define log tag */
    private val TAG: String = MainFragmentLayoutHolder::class.java.simpleName


    /* Main class variables */
    private lateinit var systemBars: Insets
    val swipeRefreshLayout: SwipeRefreshLayout
    val podcastList: RecyclerView
    val onboardingLayout: ConstraintLayout
    val layoutManager: LinearLayoutManager
    var listDragListener: ListDragListener? = null
    private val scroller: RecyclerView.SmoothScroller


    /* Init block */
    init {
        // find views
        swipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh_collection_layout)
        podcastList = rootView.findViewById(R.id.podcast_list)
        onboardingLayout = rootView.findViewById(R.id.onboarding_layout)

        // set up RecyclerView
        layoutManager = CustomLayoutManager(rootView.context)
        podcastList.layoutManager = layoutManager
        val animator: SimpleItemAnimator = DefaultItemAnimator()
        animator.supportsChangeAnimations = true
        podcastList.itemAnimator = animator
        podcastList.setHasFixedSize(true)
        // podcastList.setItemViewCacheSize(10)

        // customize scroller
        scroller = object : LinearSmoothScroller(rootView.context) {
            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }
        }

        // add scroll listener to detect when list is being dragged
        podcastList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                listDragListener?.onListDragStateChanged(newState)
            }
        })

        // set up edge to edge display
        setupEdgeToEdge()
    }


    /* Scrolls recycler list to top */
    fun scrollToTop() {
        scroller.targetPosition = 0
        layoutManager.startSmoothScroll(scroller)
    }


    /* Toggle the onboarding layout */
    fun toggleOnboarding(collectionSize: Int): Boolean {
        if (collectionSize == 0) {
            onboardingLayout.isVisible = true
            return true
        } else {
            onboardingLayout.isGone = true
            return false
        }
    }


    /* Updates the bottom padding of the podcast list based on player and up next visibility */
    fun updateListBottomPadding(playerVisible: Boolean, upNextVisible: Boolean) {
        if (this::systemBars.isInitialized) {
            podcastList.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom + UiHelper.getPlayerContainerHeight(rootView.context, playerVisible, upNextVisible)
            )
        }
    }


    /* Sets up margins/paddings for edge to edge view */
    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            // get measurements for status and navigation bar
            systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // update the onboarding layout margin to position it below the status bar
            onboardingLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                topMargin = systemBars.top
            }
            // apply measurements to podcast list
            val mainActivity = rootView.context as? BaseMainActivity
            val playerVisible = mainActivity?.layout?.playerCardView?.isVisible ?: true
            val upNextVisible = mainActivity?.layout?.upNextCardView?.isVisible ?: false
            podcastList.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom + UiHelper.getPlayerContainerHeight(rootView.context, playerVisible, upNextVisible)
            )
            // return the insets
            insets
        }
    }


    /*
     * Inner class: Custom ItemDecoration (usage: podcastList.addItemDecoration(CustomItemDecoration(bars.top, bars.bottom)))
     */
    private inner class CustomItemDecoration(private val topMargin: Int, private val bottomMargin: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            // apply margins only to the first and the last item
            if (position == 0) {
                outRect.top = topMargin
            } else if (position == state.itemCount - 1) {
                outRect.bottom = bottomMargin + (Keys.PLAYER_HEIGHT * UiHelper.getDensityScalingFactor(parent.context)).toInt()
            }
        }
    }
    /*
     * End of inner class
     */



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