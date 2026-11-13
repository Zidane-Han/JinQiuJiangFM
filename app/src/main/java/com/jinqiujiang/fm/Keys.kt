/*
 * Keys.kt
 * Implements the keys used throughout the app
 * This object hosts all keys used to control Escapepod state
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jinqiujiang.fm

import java.util.Date


/*
 * Keys object
 */
object Keys {

    // application name
    const val APPLICATION_NAME: String = "JinQiuJiangFM"

    // version numbers
    const val CURRENT_LEGACY_COLLECTION_CLASS_VERSION_NUMBER: Int = 0

    // time values
    const val UPDATE_REPEAT_INTERVAL: Long = 4L                    // every 4 hours
    const val MINIMUM_TIME_BETWEEN_UPDATES: Long = 300000L         // 5 minutes in milliseconds 300000L
    const val SKIP_BACK_TIME_SPAN: Long = 10000L                   // 10 seconds in milliseconds
    const val SKIP_FORWARD_TIME_SPAN: Long = 30000L                // 30 seconds in milliseconds
    const val SLEEP_TIMER_DURATION: Long = 900000L                 // 15 minutes in  milliseconds
    const val SLEEP_TIMER_INTERVAL: Long = 1000L                   // 1 second in milliseconds
    const val DEFAULT_TRANSITION_ANIMATION_DURATION: Long = 100L  // 100 milliseconds
    const val PLAYBACK_FINISH_SLACK_MS: Long = 500L                // 0.5 seconds in milliseconds - tolerance below duration to consider an episode finished

    // ranges

    // notification
    const val NOW_PLAYING_NOTIFICATION_ID: Int = 42
    const val NOW_PLAYING_NOTIFICATION_CHANNEL_ID: String = "notificationChannelIdPlaybackChannel"

    // intent actions
    const val ACTION_STOP: String = "com.jinqiujiang.fm.action.STOP"

    // intent extras
    const val EXTRA_UP_NEXT_MEDIA_ID: String = "UP_NEXT_MEDIA_ID"
    const val EXTRA_SLEEP_TIMER_REMAINING: String = "SLEEP_TIMER_REMAINING"
    const val EXTRA_SLEEP_TIMER_RUNNING: String = "SLEEP_TIMER_RUNNING"

    // keys
    const val KEY_DOWNLOAD_WORK_REQUEST: String = "DOWNLOAD_WORK_REQUEST"
    const val KEY_SAVE_INSTANCE_STATE_PODCAST_LIST: String = "SAVE_INSTANCE_STATE_PODCAST_LIST"
    const val KEY_ORIGINAL_ARTWORK_URI = "ORIGINAL_ARTWORK_URI"
    const val KEY_ORIGINAL_EPISODE_URI = "ORIGINAL_EPISODE_URI"
    const val KEY_EPISODE_PLAYBACK_POSITION = "EPISODE_PLAYBACK_POSITION"
    const val KEY_EPISODE_DURATION = "EPISODE_DURATION"

    // custom MediaController commands
    const val CMD_UP_NEXT_UPDATED: String = "UP_NEXT_UPDATED"
    const val CMD_REQUEST_SLEEP_TIMER_REMAINING: String = "REQUEST_SLEEP_TIMER_REMAINING"
    const val CMD_REQUEST_SLEEP_TIMER_RUNNING: String = "REQUEST_SLEEP_TIMER_RUNNING"
    const val CMD_START_SLEEP_TIMER: String = "START_SLEEP_TIMER"
    const val CMD_CANCEL_SLEEP_TIMER: String = "CANCEL_SLEEP_TIMER"

    // preferences
    const val PREF_THEME_SELECTION: String= "THEME_SELECTION"
    const val PREF_USER_INTERFACE_TRANSPARENCY_EFFECT: String = "USER_INTERFACE_TRANSPARENCY_EFFECT"
    const val PREF_LAST_UPDATE_COLLECTION: String = "LAST_UPDATE_COLLECTION"
    const val PREF_COLLECTION_SIZE: String = "COLLECTION_SIZE"
    const val PREF_COLLECTION_MODIFICATION_DATE: String = "COLLECTION_MODIFICATION_DATE"
    const val PREF_ACTIVE_DOWNLOADS: String = "ACTIVE_DOWNLOADS"
    const val PREF_BACKGROUND_REFRESH: String = "BACKGROUND_REFRESH"
    const val PREF_EPISODE_DOWNLOAD_OVER_MOBILE: String = "EPISODE_DOWNLOAD_OVER_MOBILE"
    const val PREF_NUMBER_OF_EPISODES_TO_KEEP: String = "NUMBER_OF_EPISODES_TO_KEEP"
    const val PREF_NUMBER_OF_AUDIO_FILES_TO_KEEP: String = "NUMBER_OF_AUDIO_FILES_TO_KEEP"
    const val PREF_KEEP_DEBUG_LOG: String = "KEEP_DEBUG_LOG"
    const val PREF_PODCAST_LIST_EXPANDED_FEED_LOCATION = "PODCAST_LIST_EXPANDED_FEED_LOCATION"
    const val PREF_PLAYER_STATE_EPISODE_MEDIA_ID: String = "PLAYER_STATE_EPISODE_MEDIA_ID"
    const val PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID: String = "PLAYER_STATE_UP_NEXT_MEDIA_ID"
    const val PREF_PLAYER_STATE_IS_PLAYING: String = "PLAYER_STATE_IS_PLAYING"
    // const val PREF_PLAYER_STATE_STREAMING: String = "PLAYER_STATE_STREAMING"
    const val PREF_PLAYER_STATE_PLAYBACK_SPEED: String = "PLAYER_STATE_PLAYBACK_SPEED"
    const val PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING: String = "PLAYER_STATE_SLEEP_TIMER_RUNNING"
    const val PREF_DYNAMIC_COLORS: String = "DYNAMIC_COLORS"
    const val PREF_DOWNLOAD_AUTO_CLEANUP_ENABLED: String = "_DOWNLOAD_AUTO_CLEANUP_ENABLED"

    // states
    const val STATE_SLEEP_TIMER_STOPPED: Int = 0

    // default const values
    const val DEFAULT_NUMBER_OF_AUDIO_FILES_TO_KEEP: Int = 2
    const val NUMBER_OF_DAYS_UNTIL_ABANDONED: Long = 14L
    //    const val DEFAULT_NUMBER_OF_EPISODES_TO_KEEP: Int = 5
    const val ACTIVE_DOWNLOADS_EMPTY: String = "zero"
    const val DEFAULT_BACKGROUND_REFRESH_MODE: Boolean = true
    const val DEFAULT_EPISODE_DOWNLOAD_OVER_MOBILE_MODE: Boolean = false
    const val DEFAULT_PODCAST_AUTO_DOWNLOAD_EPISODES: Boolean = false
    const val DEFAULT_DOWNLOAD_AUTO_CLEANUP_ENABLED: Boolean = false
    const val SEARCH_TERM_CHARACTER_LIMIT: Int = 100
    const val RSS_MAX_EPISODES: Int = 5000 // cap on parsed episodes per feed - bounds memory against a hostile feed
    const val RSS_MAX_FIELD_LENGTH: Int = 200000 // cap on stored text-field length (~3-4h transcript) - bounds memory

    // view types
    const val VIEW_TYPE_ADD_NEW: Int = 1
    const val VIEW_TYPE_PODCAST: Int = 2

    // view holder update types
    const val HOLDER_UPDATE_COVER: Int = 0
    const val HOLDER_UPDATE_NAME: Int = 1
    const val HOLDER_UPDATE_PLAYBACK_STATE: Int = 2
    const val HOLDER_UPDATE_DOWNLOAD_STATE: Int = 3
    const val HOLDER_UPDATE_PLAYBACK_PROGRESS: Int = 4

    // dialog types
    const val DIALOG_UPDATE_COLLECTION: Int = 1
    const val DIALOG_UPDATE_PODCAST: Int = 2
    const val DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI: Int = 3
    const val DIALOG_REMOVE_PODCAST: Int = 4
    const val DIALOG_DELETE_EPISODE: Int = 5
    const val DIALOG_ADD_UP_NEXT: Int = 6
    const val DIALOG_DELETE_DOWNLOADS: Int = 7
    const val DIALOG_MARK_EPISODE_PLAYED: Int = 9
    const val DIALOG_DOWNLOAD_ALL_EPISODES: Int = 10

    // dialog results
    const val DIALOG_RESULT_DEFAULT: Int = -1
    const val DIALOG_EMPTY_PAYLOAD_STRING: String = ""
    const val DIALOG_EMPTY_PAYLOAD_INT: Int = -1

    // file types
    const val FILE_TYPE_DEFAULT: Int = 0
    const val FILE_TYPE_RSS: Int = 1
    const val FILE_TYPE_AUDIO: Int  = 2
    const val FILE_TYPE_IMAGE: Int  = 3

    // mime types and charsets and file extensions
    const val CHARSET_UNDEFINDED = "undefined"
    const val MIME_TYPE_JPG = "image/jpeg"
    const val MIME_TYPE_PNG = "image/png"
    const val MIME_TYPE_MP3 = "audio/mpeg"
    const val MIME_TYPE_MP4 = "audio/mp4"
    const val MIME_TYPE_OGG = "audio/ogg"
    const val MIME_TYPE_AAC = "audio/aac"
    const val MIME_TYPE_XML = "text/xml"
    const val MIME_TYPE_OCTET_STREAM = "application/octet-stream"
    const val MIME_TYPE_UNSUPPORTED = "unsupported"
    val MIME_TYPES_IMAGE = arrayOf("image/png", "image/jpeg", "image/jpg")
    val MIME_TYPES_AUDIO = arrayOf("audio/mpeg", "audio/mp4", "audio/mpeg3", "audio/mp3", "audio/x-mpeg-3", "audio/x-m4a", "audio/x-aac")
    // MIME_TYPES_AUDIO are the audio content types that Escapepod will handle: "audio/mpeg" (MP3, standard), "audio/mp4" (M4A/AAC, standard), "audio/mpeg3" (MP3, deprecated), "audio/mp3" (MP3, deprecated), "audio/x-mpeg-3" (MP3, deprecated), "audio/x-m4a" (M4A/AAC, deprecated), "audio/x-aac" (M4A/AAC, deprecated)
    val MIME_TYPES_RSS = arrayOf("text/xml", "application/rss+xml", "application/xml")
    val MIME_TYPES_ATOM = arrayOf("application/atom+xml")
    val MIME_TYPES_OCTET_STREAM = arrayOf("application/octet-stream", "binary/octet-stream")
    val FILE_EXTENSIONS_AUDIO = arrayOf("mp3", "m4a")
    val FILE_EXTENSIONS_IMAGE = arrayOf("png", "jpeg", "jpg")

    // folder names
    const val FOLDER_COLLECTION: String = "collection"
    const val FOLDER_AUDIO: String = "audio"
    const val FOLDER_IMAGES: String  = "images"
    const val FOLDER_TEMP: String  = "temp"

    // file names
    const val COLLECTION_FILE: String = "collection.json"
    const val PODCAST_COVER_FILE: String = "cover.jpg"
    const val PODCAST_SMALL_COVER_FILE: String = "cover-small.jpg"
    const val DEBUG_LOG_FILE: String = "log-can-be-deleted.txt"

    // locations
    const val LOCATION_DEFAULT_COVER: String = "android.resource://${BuildConfig.APPLICATION_ID}/drawable/ic_default_cover_rss_icon_24dp"
    const val LOCATION_RESOURCES: String = "android.resource://${BuildConfig.APPLICATION_ID}/"

    // sizes (in dp)
    const val SIZE_COVER_PODCAST: Int = 1024 // used to create cover.jpg
    const val SIZE_COVER_PODCAST_SMALL: Int = 640 // used to create cover-small.jpg
    const val PLAYER_HEIGHT: Int = 80 // height of card_player.xml
    const val PLAYER_BOTTOM_MARGIN: Int = 8 // value of android:layout_marginBottom for card_player in activity_main.xml
    const val UP_NEXT_TOP_MARGIN: Int = 8 // value of android:layout_marginTop for card_up_next in activity_main.xml
    const val UP_NEXT_HEIGHT: Int = 36 // height of card_up_next.xml
    const val UP_NEXT_BOTTOM_MARGIN: Int = 8 // value of android:layout_marginBottom for card_up_next in activity_main.xml
    const val LIST_DEFAULT_BOTTOM_PADDING: Int = 8

    // default values
    val DEFAULT_DATE: Date = Date(0L)
    const val DEFAULT_RFC2822_DATE: String = "Thu, 01 Jan 1970 01:00:00 +0100" // --> Date(0)
    const val EMPTY_STRING_RESOURCE: Int = 0

    // media browser
    const val MEDIA_BROWSER_ROOT = "__ROOT__"
    const val MEDIA_BROWSER_ROOT_RECENT = "__RECENT__"
    const val MEDIA_BROWSER_ROOT_EMPTY = "@empty@"

    // requests
    const val REQUEST_UPDATE_COLLECTION: Int = 1

    // arguments
    const val ARG_PODCAST_FEED: String = "ArgPodcastFeed"
    const val ARG_PODCAST_NAME: String = "ArgPodcastName"
    const val ARG_PODCAST_COVER: String = "ArgPodcastCover"
    const val ARG_EPISODE_LIST_FILTER: String = "ArgEpisodeListFilter"
    const val ARG_EPISODE_SEARCH_TERM: String = "ArgEpisodeSearchTerm"

    // podcast validation states
    const val PODCAST_VALIDATION_SUCESS: Int = 0
    const val PODCAST_VALIDATION_MISSING_COVER: Int = 1
    const val PODCAST_VALIDATION_NO_VALID_EPISODES: Int = 2

    // podcast states
    const val PODCAST_STATE_NEW_PODCAST: Int = 0
    const val PODCAST_STATE_AUDIO_FILES_DOWNLOAD_NECESSARY: Int = 1
    const val PODCAST_STATE_PODCAST_UNCHANGED: Int = 2

    // search

    // default user agent
    val USER_AGENT: String = "$APPLICATION_NAME/${BuildConfig.VERSION_NAME}"
    // note: no web browser user agent fallback here (unlike Transistor) - no podcast host is known to reject USER_AGENT

    // theme states
    const val STATE_THEME_FOLLOW_SYSTEM: String = "stateFollowSystem"
    const val STATE_THEME_LIGHT_MODE: String = "stateLightMode"
    const val STATE_THEME_DARK_MODE: String = "stateDarkMode"

    // filter options
    const val FILTER_SHOW_ALL: Int = 0
    const val FILTER_SHOW_DOWNLOADED: Int = 1

    // background download options
    const val BACKGROUND_DOWNLOAD_DEFAULT: String = "backgroundDownloadDefault"
    const val BACKGROUND_DOWNLOAD_MANUAL: String = "backgroundDownloadManual"
    const val BACKGROUND_DOWNLOAD_UNRESTRICTED: String = "backgroundDownloadUnrestricted"

    // unique names
    const val NAME_PERIODIC_COLLECTION_UPDATE_WORK: String = "PERIODIC_COLLECTION_UPDATE_WORK"

    // rss tags
    val XML_NAME_SPACE: String? = null
    const val RSS_RSS = "rss"
    const val RSS_PODCAST = "channel"
    const val RSS_PODCAST_NAME = "title"
    const val RSS_PODCAST_DESCRIPTION = "description"
    const val RSS_PODCAST_DESCRIPTION_ITUNES= "itunes:summary"
    const val RSS_PODCAST_WEBSITE = "link"
    const val RSS_PODCAST_COVER = "image"
    const val RSS_PODCAST_COVER_HREF = "href"
    const val RSS_PODCAST_COVER_URL = "url"
    const val RSS_PODCAST_COVER_ITUNES = "itunes:image"
    const val RSS_EPISODE = "item"
    const val RSS_EPISODE_GUID = "guid"
    const val RSS_EPISODE_TITLE = "title"
    const val RSS_EPISODE_DESCRIPTION = "description"
    const val RSS_EPISODE_DESCRIPTION_ITUNES= "itunes:summary"
    const val RSS_EPISODE_CONTENT = "content:encoded"
    const val RSS_EPISODE_PUBLICATION_DATE = "pubDate"
    const val RSS_EPISODE_AUDIO_LINK = "enclosure"
    const val RSS_EPISODE_AUDIO_LINK_TYPE = "type"
    const val RSS_EPISODE_AUDIO_LINK_URL = "url"

    // http status codes which are not supported by HttpURLConnection
    const val HTTP_PERM_REDIR: Int = 308
}