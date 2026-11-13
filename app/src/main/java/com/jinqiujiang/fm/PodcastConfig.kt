package com.jinqiujiang.fm

/** The only catalogue supported by this application. */
object PodcastConfig {
    const val ID = "jinqiujiang"
    const val NAME = "今球讲"
    const val FEED_URL = "https://feed.xyzfm.space/jkr43da47kq7"
    fun episodeId(guid: String, audioUrl: String): String = "$ID:${guid.ifBlank { audioUrl }}"
    fun durationMillis(value: String): Long = runCatching {
        val parts = value.trim().split(":")
        require(parts.size in 1..3)
        val seconds = parts.fold(0L) { total, part ->
            val n = part.toLong()
            require(n >= 0)
            Math.addExact(Math.multiplyExact(total, 60L), n)
        }
        Math.multiplyExact(seconds, 1000L)
    }.getOrDefault(0L)
}
