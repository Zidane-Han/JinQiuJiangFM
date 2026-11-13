/*
 * PodcastWithDescriptionAndAllEpisodesWrapper.kt
 * Implements the PodcastWithDescriptionAndAllEpisodesWrapper class
 * A PodcastWithDescriptionAndAllEpisodesWrapper object holds the base data of a podcast, its description and a list of all episodes - a wrapper for Podcast + Description + Episode(s)
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jinqiujiang.fm.database.wrappers

import androidx.room.Embedded
import androidx.room.Relation
import com.jinqiujiang.fm.database.objects.Episode
import com.jinqiujiang.fm.database.objects.Podcast
import com.jinqiujiang.fm.database.objects.PodcastDescription


/*
 * PodcastWithDescriptionAndAllEpisodesWrapper class
 */
data class PodcastWithDescriptionAndAllEpisodesWrapper(
        @Embedded
        val data: Podcast,

        @Relation(parentColumn = "remote_podcast_feed_location", entityColumn = "remote_podcast_feed_location")
        val description: PodcastDescription,

        @Relation(parentColumn = "remote_podcast_feed_location", entityColumn = "episode_remote_podcast_feed_location")
        val episodes: List<Episode>

)