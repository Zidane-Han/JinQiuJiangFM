package com.jinqiujiang.fm.ui

import android.widget.ImageButton
import com.jinqiujiang.fm.R

/** Render both controls even when one player's parent is hidden. */
object PlaybackButtonState {
    fun render(mini: ImageButton, expanded: ImageButton, isPlaying: Boolean) {
        mini.setImageResource(if (isPlaying) R.drawable.ic_player_pause_symbol_48dp else R.drawable.ic_player_play_symbol_48dp)
        expanded.setImageResource(if (isPlaying) R.drawable.ic_player_pause_symbol_68dp else R.drawable.ic_player_play_symbol_68dp)
        val description = mini.context.getString(if (isPlaying) R.string.player_action_pause else R.string.player_action_play)
        mini.contentDescription = description
        expanded.contentDescription = description
    }
}
