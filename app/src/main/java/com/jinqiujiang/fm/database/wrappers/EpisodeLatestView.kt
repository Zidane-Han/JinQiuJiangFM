/*
 * EpisodeLatestView.kt
 * Implements the EpisodeLatestView class
 * A EpisodeLatestView is a view into the database that contains only the most recent episode for each podcast
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jinqiujiang.fm.database.wrappers

import androidx.room.DatabaseView
import androidx.room.Embedded
import com.jinqiujiang.fm.database.objects.Episode


/*
 * EpisodeLatestView class
 * Credit: https://stackoverflow.com/questions/64025864/android-room-looking-for-dao-that-limits-the-number-of-elements-in-embedded-li/64026959
 * ...and: https://stackoverflow.com/questions/64250896/sqlite-query-five-most-recent-episode-of-each-podcast/64250926
 */
@DatabaseView("SELECT * FROM episodes e WHERE (SELECT Count(*) FROM episodes e1 WHERE e1.episode_remote_podcast_feed_location = e.episode_remote_podcast_feed_location AND (e1.publication_date > e.publication_date OR (e1.publication_date = e.publication_date AND e1.title > e.title))) <= 0")
data class EpisodeLatestView (
        @Embedded
        val data: Episode
)
