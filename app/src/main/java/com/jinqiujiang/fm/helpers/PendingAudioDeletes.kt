package com.jinqiujiang.fm.helpers

import android.content.Context
import androidx.core.net.toUri
import androidx.core.net.toFile

/** Persists deferred removals across process death; the active file remains readable. */
object PendingAudioDeletes {
    @Volatile var activeMediaId: String = ""
    @Synchronized fun remove(context: Context, mediaId: String, uri: String) {
        if (uri.isBlank()) return
        val prefs = context.getSharedPreferences("pending_audio_deletes", Context.MODE_PRIVATE)
        if (mediaId == activeMediaId) prefs.edit().putString(mediaId, uri).apply()
        else runCatching { uri.toUri().toFile().delete() }
    }
    @Synchronized fun flush(context: Context, activeId: String) {
        activeMediaId = activeId
        val prefs = context.getSharedPreferences("pending_audio_deletes", Context.MODE_PRIVATE)
        for ((id, uri) in prefs.all) if (id != activeId) {
            runCatching { (uri as String).toUri().toFile().delete() }
            prefs.edit().remove(id).apply()
        }
    }
}
