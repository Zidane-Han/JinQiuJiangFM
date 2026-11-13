package com.jinqiujiang.fm.ui

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher

/** Collapse the player first, while allowing normal navigation when it is collapsed. */
class PlayerBackCallback(
    private val dispatcher: OnBackPressedDispatcher,
    private val collapsePlayer: () -> Boolean
) : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
        if (collapsePlayer()) return
        // Disable only during delegation, otherwise reopening the player would bypass us.
        isEnabled = false
        try {
            dispatcher.onBackPressed()
        } finally {
            isEnabled = true
        }
    }
}
