/*
* FindPodcastDialogListener.kt
* Implements the FindPodcastDialogListener interface
* A FindPodcastDialogListener defines a standard way for to send a selected podcast feed URL back
*
* This file is part of
* ESCAPEPOD - Free and Open Podcast App
*
* Copyright (c) 2018-26 - Y20K.org
* Licensed under the MIT-License
* http://opensource.org/licenses/MIT
*/


package org.y20k.escapepod.interfaces

/*
 * FindPodcastDialogListener interface
 */
interface FindPodcastDialogListener {
    fun onFindPodcastDialog(remotePodcastFeedLocation: String)
}