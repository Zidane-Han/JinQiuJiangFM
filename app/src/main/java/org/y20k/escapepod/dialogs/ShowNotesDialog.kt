/*
 * ShowNotesDialog
 * Implements the ShowNotesDialog class
 * A ShowNotesDialog displays the show notes for a podcast episode
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.y20k.escapepod.R
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.EpisodeDescription
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.extensions.setCleanHtml
import org.y20k.escapepod.helpers.DateTimeHelper


/*
 * ShowNotesDialog class
 */
class ShowNotesDialog {

    /* Define log tag */
    private val TAG = ShowNotesDialog::class.java.simpleName


    /* Construct and show dialog */
    fun show(context: Context, podcast: Podcast, episode: Episode, episodeDescription: EpisodeDescription) {
        // prepare dialog builder
        val builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(context)

        // get views
        val inflater: LayoutInflater = LayoutInflater.from(context)
        val view: View = inflater.inflate(R.layout.dialog_episodes_shownotes, null)
        val podcastNameView: TextView = view.findViewById(R.id.podcast_name)
        val podcastWebsiteView: TextView = view.findViewById(R.id.podcast_website)
        val podcastLinksDivider: ImageView = view.findViewById(R.id.divider_centered_dot)
        val podcastFeedView: TextView = view.findViewById(R.id.podcast_feed)
        val episodeDateView: TextView = view.findViewById(R.id.episode_date)
        val episodeTitleView: TextView = view.findViewById(R.id.episode_title)
        val episodeShowNotesView: TextView = view.findViewById(R.id.podcast_description)

        // set dialog view
        builder.setView(view)

        // set views
        podcastNameView.text = podcast.name
        episodeDateView.text = DateTimeHelper.getDateString(episode.publicationDate)
        episodeTitleView.text = episode.title
        episodeTitleView.isSelected = true // triggers the marquee
        episodeShowNotesView.setCleanHtml(episodeDescription.description)
        episodeShowNotesView.movementMethod = LinkMovementMethod.getInstance() // make link tapable

        // podcast website: set up open browser
        if (podcast.website.isNotEmpty()) {
            podcastWebsiteView.isVisible = true
            podcastLinksDivider.isVisible = true
            podcastWebsiteView.setOnClickListener {
                startActivity(context, Intent(Intent.ACTION_VIEW, podcast.website.toUri()), null)
            }
        } else {
            podcastWebsiteView.isGone = true
            podcastLinksDivider.isGone = true
        }

        // podcast feed: set up clipboard copy
        podcastFeedView.setOnClickListener {
            val clip: ClipData = ClipData.newPlainText("simple text", podcast.remotePodcastFeedLocation)
            val cm: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU){
                Toast.makeText(context, R.string.toast_message_copied_to_clipboard, Toast.LENGTH_LONG).show()
            }
        }

        // add close button
        builder.setPositiveButton(R.string.dialog_generic_button_close) { _, _ ->
            // listen for click on close button
            // do nothing
        }

        // display dialog
        builder.show()
    }

}