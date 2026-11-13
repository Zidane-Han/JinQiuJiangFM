package com.jinqiujiang.fm.helpers

import android.content.Context
import androidx.core.net.toUri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.jinqiujiang.fm.Keys
import com.jinqiujiang.fm.PodcastConfig
import com.jinqiujiang.fm.database.CollectionDatabase
import com.jinqiujiang.fm.xml.RssHelper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Serializes foreground and worker refreshes; success means the catalogue is committed. */
object PodcastSync {
    private val mutex = Mutex()
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File.createTempFile("feed", ".xml", context.cacheDir)
            val connection = URL(PodcastConfig.FEED_URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty("User-Agent", Keys.USER_AGENT)
                check(connection.responseCode in 200..299) { "RSS HTTP ${connection.responseCode}" }
                connection.inputStream.use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        total += size
                        check(total <= 10 * 1024 * 1024) { "RSS too large" }
                        output.write(buffer, 0, size)
                    }
                } }
                val rss = RssHelper().read(context, file.toUri(), PodcastConfig.FEED_URL)
                check(rss.name.isNotBlank() && rss.episodes.isNotEmpty()) { "RSS has no valid episodes" }
                CollectionDatabase.getInstance(context).withTransaction {
                    DownloadHelper.addPodcast(context, rss)
                }
                PreferencesHelper.saveLastUpdateCollection()
            } finally {
                connection.disconnect()
                file.delete()
            }
        }
    }
}
