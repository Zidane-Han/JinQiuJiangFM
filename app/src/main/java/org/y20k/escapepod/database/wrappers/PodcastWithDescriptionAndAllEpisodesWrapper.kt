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


package org.y20k.escapepod.database.wrappers

import androidx.room.Embedded
import androidx.room.Relation
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.database.objects.PodcastDescription


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