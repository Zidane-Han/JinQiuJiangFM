/*
 * RssHelper.kt
 * Implements the RssHelper class
 * A RssHelper reads and parses podcast RSS feeds
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.xml

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.y20k.escapepod.Keys
import org.y20k.escapepod.extensions.stripHtml
import org.y20k.escapepod.helpers.DateTimeHelper
import org.y20k.escapepod.helpers.FileHelper
import org.y20k.escapepod.helpers.NetworkHelper
import java.io.IOException
import java.io.InputStream
import java.util.Date


/*
 * RssHelper class
 */
class RssHelper {

    /* Define log tag */
    private val TAG: String = RssHelper::class.java.simpleName


    /* Main class variables */
    private var podcast: RssPodcast = RssPodcast()


    /* Suspend function: Read RSS feed from given input stream - async using coroutine */
    fun read(context: Context, localFileUri: Uri, remotePodcastFeedLocation: String): RssPodcast {
        Log.v(TAG, "Reading RSS feed ($remotePodcastFeedLocation) - Thread: ${Thread.currentThread().name}")
        // create podcast object and store remote feed location
        podcast = RssPodcast(remotePodcastFeedLocation = remotePodcastFeedLocation)
        // try parsing
        val stream: InputStream? = FileHelper.getTextFileStream(context, localFileUri)
        try {
            // create XmlPullParser for InputStream
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            // NOTE: DTD/DOCTYPE processing is already OFF by default here - Android's KXmlParser does
            // not resolve external entities, so this parser is safe against XXE / entity-expansion.
            // Do NOT "harden" this by calling parser.setFeature(FEATURE_PROCESS_DOCDECL, ...): KXmlParser
            // throws XmlPullParserException for that feature, and because this runs inside the try/catch
            // below the throw would be swallowed and abort parsing of every feed. The safe default is
            // relied on intentionally (security audit F-5).
            parser.setInput(stream, null)
            parser.nextTag()
            // start reading rss feed
            parseFeed(parser)
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "Unable to parse RSS feed - malformed XML ($remotePodcastFeedLocation).", e)
        } catch (e: IOException) {
            Log.e(TAG, "Error reading RSS feed stream ($remotePodcastFeedLocation).", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while parsing RSS feed ($remotePodcastFeedLocation).", e)
        } finally {
            stream?.close()
        }

        // sort episodes - newest episode first
        podcast.episodes.sortByDescending { it.publicationDate }

        // return parsing result
        return podcast
    }


    /* Parses whole RSS feed */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseFeed(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, Keys.XML_NAME_SPACE, Keys.RSS_RSS)

        while (parser.next() != XmlPullParser.END_TAG) {
            // skip this round early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // read only relevant tags
            when (parser.name) {
                // found a podcast
                Keys.RSS_PODCAST -> readPodcast(parser)
                // skip any other un-needed tag within document
                else -> XmlHelper.skip(parser)
            }
        }
    }


    /* Reads podcast element - within feed */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readPodcast(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, Keys.XML_NAME_SPACE, Keys.RSS_PODCAST)

        // count encountered episodes to bound parsing (see Keys.RSS_MAX_EPISODES)
        var episodesSeen: Int = 0

        while (parser.next() != XmlPullParser.END_TAG) {
            // skip this round early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // read only relevant tags
            when (parser.name) {
                // found podcast name
                Keys.RSS_PODCAST_NAME -> podcast.name = readPodcastName(parser, Keys.XML_NAME_SPACE)
                // found podcast description
                Keys.RSS_PODCAST_DESCRIPTION -> podcast.description = getLongerString(podcast.description, readPodcastDescription(parser, Keys.XML_NAME_SPACE))
                // found podcast description
                Keys.RSS_EPISODE_DESCRIPTION_ITUNES -> podcast.description = getLongerString(podcast.description, readPodcastDescriptionItunes(parser, Keys.XML_NAME_SPACE))
                // found podcast website
                Keys.RSS_PODCAST_WEBSITE -> podcast.website = readPodcastWebsite(parser, Keys.XML_NAME_SPACE)
                // found podcast remoteImageFileLocation
                Keys.RSS_PODCAST_COVER_ITUNES -> podcast.remoteImageFileLocation = schemeAllowedOrEmpty(readPodcastCoverItunes(parser, Keys.XML_NAME_SPACE))
                Keys.RSS_PODCAST_COVER -> podcast.remoteImageFileLocation = schemeAllowedOrEmpty(readPodcastCover(parser, Keys.XML_NAME_SPACE))
                // found episode
                Keys.RSS_EPISODE -> {
                    episodesSeen++
                    // stop building episodes past the cap - skip cheaply to bound memory
                    if (episodesSeen > Keys.RSS_MAX_EPISODES) {
                        XmlHelper.skip(parser)
                    } else {
                        val episode: RssEpisode = readEpisode(parser)
                        if (episode.title.isNotEmpty() && episode.remoteAudioFileLocation.isNotEmpty() && episode.publicationDate != Keys.DEFAULT_DATE) {
                            // add podcast name and podcast cover to episode (if not already set)
                            episode.podcastName = podcast.name
                            if (episode.remoteCoverFileLocation.isEmpty()) {
                                episode.remoteCoverFileLocation = podcast.remoteImageFileLocation
                            }
                            // add episode to podcast
                            podcast.episodes.add(episode)
                            if (episode.publicationDate.after(podcast.latestEpisodeDate)) {
                                podcast.latestEpisodeDate = episode.publicationDate
                            }
                        }
                    }
                }
                // skip any other un-needed tag within "channel" ( = podcast)
                else -> XmlHelper.skip(parser)
            }
        }
    }


    /* Reads episode element - within podcast element (within feed) */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readEpisode(parser: XmlPullParser): RssEpisode {
        parser.require(XmlPullParser.START_TAG, Keys.XML_NAME_SPACE, Keys.RSS_EPISODE)

        // initialize episode
        val episode: RssEpisode = RssEpisode(episodeRemotePodcastFeedLocation = podcast.remotePodcastFeedLocation)

        while (parser.next() != XmlPullParser.END_TAG) {
            // skip this round early if no start tag
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            // read only relevant tags
            when (parser.name) {
                // found episode title
                Keys.RSS_EPISODE_GUID -> episode.guid = readEpisodeGuid(parser, Keys.XML_NAME_SPACE)
                // found episode title
                Keys.RSS_EPISODE_TITLE -> episode.title = readEpisodeTitle(parser, Keys.XML_NAME_SPACE)
                // found episode description
                Keys.RSS_EPISODE_DESCRIPTION -> episode.description = getLongerString(episode.description, readEpisodeDescription(parser, Keys.XML_NAME_SPACE))
                // found episode description
                Keys.RSS_EPISODE_DESCRIPTION_ITUNES -> episode.description = getLongerString(episode.description, readEpisodeDescriptionItunes(parser, Keys.XML_NAME_SPACE))
                // found episode full-HTML content
                Keys.RSS_EPISODE_CONTENT -> episode.description = getLongerString(episode.description, readEpisodeContent(parser, Keys.XML_NAME_SPACE))
                // found episode publication date
                Keys.RSS_EPISODE_PUBLICATION_DATE -> episode.publicationDate = readEpisodePublicationDate(parser, Keys.XML_NAME_SPACE)
                // found episode audio link
                Keys.RSS_EPISODE_AUDIO_LINK -> episode.remoteAudioFileLocation = readEpisodeAudioLink(parser, Keys.XML_NAME_SPACE)
                // skip any other un-needed tag within "item" ( = episode)
                else -> XmlHelper.skip(parser)
            }
        }
        return episode
    }


    /* PODCAST: read name */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPodcastName(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_NAME)
        val name = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_NAME)
        return name.stripHtml().take(Keys.RSS_MAX_FIELD_LENGTH)
    }


    /* PODCAST: read description */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPodcastDescription(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_DESCRIPTION)
        val summary = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_DESCRIPTION)
        return summary
    }


    /* PODCAST: read description / summary - iTunes variant */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPodcastDescriptionItunes(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_DESCRIPTION_ITUNES)
        val summary = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_DESCRIPTION_ITUNES)
        return summary
    }


    /* PODCAST: read website */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPodcastWebsite(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_WEBSITE)
        val website = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_WEBSITE)
        return website
    }


    /* EPISODE: read GUID */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEpisodeGuid(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_GUID)
        val guid = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_GUID)
        return guid
    }


    /* EPISODE: read title */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEpisodeTitle(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_TITLE)
        val title = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_TITLE)
        return title.stripHtml().take(Keys.RSS_MAX_FIELD_LENGTH)
    }


    /* EPISODE: read description */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEpisodeDescription(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_DESCRIPTION)
        val summary = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_DESCRIPTION)
        return summary
    }


    /* EPISODE: read description / summary - iTunes variant */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEpisodeDescriptionItunes(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_DESCRIPTION_ITUNES)
        val summary = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_DESCRIPTION_ITUNES)
        return summary
    }


    /* EPISODE: read description / summary - full-HTML comment variant */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEpisodeContent(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_CONTENT)
        val summary = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_CONTENT)
        return summary
    }


    /* EPISODE: read publication date */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEpisodePublicationDate(parser: XmlPullParser, nameSpace: String?): Date {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_PUBLICATION_DATE)
        val publicationDate: Date = DateTimeHelper.convertFromRfc2822(XmlHelper.readText(parser))
//        if (publicationDate != Keys.DEFAULT_DATE && DateTimeHelper.isSignificantlyInTheFuture(publicationDate)) {
//            Log.e(TAG, "Parsing issue. Publication date is significantly in the future: ${DateTimeHelper.getDateString(publicationDate)}")
//            publicationDate = Keys.DEFAULT_DATE
//        }
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_PUBLICATION_DATE)
        return publicationDate
    }


    /* PODCAST: read remoteImageFileLocation - standard tag variant */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPodcastCover(parser: XmlPullParser, nameSpace: String?): String {
        var link = String()
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_COVER)
        val href: String? = parser.getAttributeValue(null, Keys.RSS_PODCAST_COVER_HREF)
        // CASE: Get cover URL from <image href="https:/..." />
        if (!href.isNullOrEmpty()) {
            link = href
            parser.nextTag()
        }
        // CASE: Get cover URL from <image><url>https:/...</url></image>
        else {
            while (parser.next() != XmlPullParser.END_TAG) {
                // abort loop early if no start tag
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }
                // read only relevant tags
                when (parser.name) {
                    // found episode cover
                    Keys.RSS_PODCAST_COVER_URL -> link = readPodcastCoverUrl(parser, nameSpace)
                    // skip any other un-needed tag within "image" ( = Cover)
                    else -> XmlHelper.skip(parser)
                }
            }
        }
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_COVER)
        return link
    }


    /* PODCAST: read remoteImageFileLocation URL - within remoteImageFileLocation*/
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPodcastCoverUrl(parser: XmlPullParser, nameSpace: String?): String {
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_COVER_URL)
        val link = XmlHelper.readText(parser)
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_COVER_URL)
        return link
    }


    /* PODCAST: read remoteImageFileLocation - itunes tag variant */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPodcastCoverItunes(parser: XmlPullParser, nameSpace: String?): String {
        var link = String()
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_PODCAST_COVER_ITUNES)
        val href: String? = parser.getAttributeValue(null, Keys.RSS_PODCAST_COVER_HREF)
        if (!href.isNullOrEmpty()) {
            link = href
        }
        parser.nextTag()
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_PODCAST_COVER_ITUNES)
        return link
    }


    /* EPISODE: read audio link */
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEpisodeAudioLink(parser: XmlPullParser, nameSpace: String?): String {
        var link = String()
        parser.require(XmlPullParser.START_TAG, nameSpace, Keys.RSS_EPISODE_AUDIO_LINK)
        val tag = parser.name
        val type = parser.getAttributeValue(null, Keys.RSS_EPISODE_AUDIO_LINK_TYPE)
        if (tag == Keys.RSS_EPISODE_AUDIO_LINK) {
            // url attribute may be absent -> guard against NPE, skip the enclosure gracefully; validate scheme (F-4)
            val value: String? = parser.getAttributeValue(null, Keys.RSS_EPISODE_AUDIO_LINK_URL)
            if (value != null && NetworkHelper.isAllowedScheme(value)) {
                val fileExtension: String = value.substring(value.lastIndexOf(".") + 1)
                if (Keys.MIME_TYPES_AUDIO.contains(type) || Keys.FILE_EXTENSIONS_AUDIO.contains(fileExtension)) {
                    link = value
                }
            }
            parser.nextTag()
        }
        parser.require(XmlPullParser.END_TAG, nameSpace, Keys.RSS_EPISODE_AUDIO_LINK)
        return link
    }


    /* Compares the length of two stings and returns the longer string - capped to bound memory */
    private fun getLongerString(currentString: String, newString: String): String {
//        if (currentString.stripHtml().length >= newString.stripHtml().length) { // todo consider stripping html before comparing
        val longer: String = if (currentString.length >= newString.length) currentString else newString
        // cap stored field length (see Keys.RSS_MAX_FIELD_LENGTH)
        return longer.take(Keys.RSS_MAX_FIELD_LENGTH)
    }


    /* Returns the given URL only if it uses an allowed http/https scheme, else empty - parse-boundary defense (F-4) */
    private fun schemeAllowedOrEmpty(url: String): String {
        return if (NetworkHelper.isAllowedScheme(url)) url else String()
    }


    /*
     * Inner class to collect podcast data
     */
    inner class RssPodcast(
        var name: String = String(),
        var description: String = String(),
        var website: String = String(),
        var cover: String = String(),
        var smallCover: String = String(),
        var latestEpisodeDate: Date = Keys.DEFAULT_DATE,
        var remoteImageFileLocation: String = String(),
        var remotePodcastFeedLocation: String = String(),
        var episodes: MutableList<RssEpisode> = mutableListOf()
    )
    /*
     * End of inner class
     */


    /*
     * Inner class to collect episode data
     */
    inner class RssEpisode(
        var guid: String = String(),
        var title: String = String(),
        var description: String = String(),
        var audio: String = String(),
        var cover: String = String(),
        var smallCover: String = String(),
        var publicationDate: Date = Keys.DEFAULT_DATE,
        var isPlaying: Boolean = false,
        var playbackPosition: Long = 0L,
        var duration: Long = 0L,
        var manuallyDownloaded: Boolean = false,
        var manuallyDeleted: Boolean = false,
        var podcastName: String = String(),
        var remoteCoverFileLocation: String = String(),
        var remoteAudioFileLocation: String = String(),
        var episodeRemotePodcastFeedLocation: String = String()
    )
    /*
     * End of inner class
     */

}