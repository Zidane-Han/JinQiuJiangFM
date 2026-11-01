/*
* PlayerContainerStateChangeListener.kt
* Implements the PlayerContainerStateChangeListener interface
* A PlayerContainerStateChangeListener defines a standard way to communicate back a change in the player container state (player is visible / not visible etc.)
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
 * PlayerContainerStateChangeListener interface
 */
interface PlayerContainerStateChangeListener {
    fun onPlayerContainerStateChanged(playerVisible: Boolean, upNextVisible: Boolean)
}