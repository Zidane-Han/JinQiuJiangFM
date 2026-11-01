/*
 * PodcastViewModelFactory.kt
 * Implements the PodcastViewModelFactory class
 * A PodcastViewModelFactory is a custom factory to create DetailViewModel
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.collection

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider


/*
 * PodcastViewModelFactory class
 */
class PodcastViewModelFactory (private val application: Application, private val remotePodcastFeedLocation: String): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PodcastViewModel(application, remotePodcastFeedLocation) as T
    }
}