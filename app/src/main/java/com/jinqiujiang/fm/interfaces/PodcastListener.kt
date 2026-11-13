/*
* PodcastListener.kt
* Implements the PodcastListener interface
* A PodcastListener is standardizes the interactions that the podcast and episode ui offers
*
* This file is part of
* ESCAPEPOD - Free and Open Podcast App
*
* Copyright (c) 2018-26 - Y20K.org
* Licensed under the MIT-License
* http://opensource.org/licenses/MIT
*/

package com.jinqiujiang.fm.interfaces

import com.jinqiujiang.fm.database.objects.Episode

/*
 * PodcastListener interface
 */
interface PodcastListener {
    fun onEpisodeTapped(selectedEpisode: Episode)
    fun onPlayButtonTapped(selectedEpisode: Episode)
    fun onMarkListenedButtonTapped(selectedEpisode: Episode)
    fun onDownloadButtonTapped(selectedEpisode: Episode)
    fun onDownloadAllButtonTapped(episodeRemotePodcastFeedLocation: String)
    fun onDeleteButtonTapped(selectedEpisode: Episode)
    fun onPodcastCardTapped()
    fun onAddNewButtonTapped()
}