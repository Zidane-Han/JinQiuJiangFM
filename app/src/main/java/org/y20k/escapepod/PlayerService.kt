/*
 * PlayerService.kt
 * Implements the PlayerService class
 * PlayerService adds a local player to BasePlayerService which otherwise holds most of the music service logic
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package org.y20k.escapepod

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi


/*
 * PlayerService class
 */
@UnstableApi
class PlayerService: BasePlayerService() {


    /* Main class variables */
    override val player: Player by lazy { localPlayer }


    /* Overrides initializePlayer from BasePlayerService */
    override fun initializePlayer() {}


}