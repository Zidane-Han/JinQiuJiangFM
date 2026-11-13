/*
 * PodcastViewModel.kt
 * Implements the PodcastViewModel class
 * A PodcastViewModel stores a podcast, its description and list of episodes as live data
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jinqiujiang.fm.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.jinqiujiang.fm.database.CollectionDatabase
import com.jinqiujiang.fm.database.objects.Episode
import com.jinqiujiang.fm.database.objects.Podcast
import com.jinqiujiang.fm.database.objects.PodcastDescription


/*
 * PodcastViewModel.class
 */
class PodcastViewModel(application: Application, remotePodcastFeedLocation: String) : AndroidViewModel(application) {

    /* Define log tag */
    private val TAG: String = PodcastViewModel::class.java.simpleName


    /* Main class variables */
    val podcastDetailLiveData: LiveData<Podcast?>
    val podcastDescriptionLiveData: LiveData<PodcastDescription?>
    val episodeListLiveData: LiveData<List<Episode>>
    private var collectionDatabase: CollectionDatabase = CollectionDatabase.getInstance(application)


    /* Init constructor */
    init {
        // initialize live data - note: no need for async/background thread execution, because LiveData updating already asynchronous
        podcastDetailLiveData = collectionDatabase.podcastDao().findByRemotePodcastFeedLocationLiveData(remotePodcastFeedLocation)
        podcastDescriptionLiveData = collectionDatabase.podcastDescriptionDao().findByRemotePodcastFeedLocationLiveData(remotePodcastFeedLocation)
        episodeListLiveData = collectionDatabase.episodeDao().findByEpisodeRemotePodcastFeedLocationLiveData(remotePodcastFeedLocation)
    }

}