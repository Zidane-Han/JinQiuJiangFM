/*
 * PodcastWithLatestEpisodeWrapper.kt
 * Implements the PodcastWithLatestEpisodeWrapper class
 * A PodcastWithLatestEpisodeWrapper object holds the base data of a podcast and its latest episode - a wrapper for Podcast & Episode
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
import com.jinqiujiang.fm.database.objects.Podcast


/*
 * PodcastWithLatestEpisodeWrapper class
 */
data class PodcastWithLatestEpisodeWrapper(
        @Embedded
        val data: Podcast,

        @Relation(parentColumn = "remote_podcast_feed_location", entityColumn = "episode_remote_podcast_feed_location")
        val episode: EpisodeLatestView?
)