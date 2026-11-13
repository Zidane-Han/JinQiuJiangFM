package com.jinqiujiang.fm.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.jinqiujiang.fm.PodcastConfig
import com.jinqiujiang.fm.R
import com.jinqiujiang.fm.extensions.parseCleanHtml

// Both Xiaoyuzhou's href-less <a class="timestamp"> and plain chapter labels
// appear at the start of a line in this feed after the HTML is parsed.
private val timestampAtLineStart = Regex("""(?m)^[\t \u00a0\u3000]*(\d{1,4}:[0-5]\d(?::[0-5]\d)?)(?![\d:])""")

/** Lays out text and RSS images in the same order as the HTML. */
class ShowNotesView(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    init {
        orientation = VERTICAL
    }

    fun clear() {
        for (index in 0 until childCount) {
            val image = getChildAt(index) as? ImageView ?: continue
            Glide.with(context.applicationContext).clear(image)
        }
        removeAllViews()
    }

    fun show(html: String, durationMs: Long = 0L, onTimestampClick: ((Long) -> Unit)? = null): Boolean {
        clear()
        // Ask Android's HTML parser for each image's source and position. The
        // placeholders only mark positions and are replaced by real views.
        val parsed = parseCleanHtml(html) { ColorDrawable(Color.TRANSPARENT) }
        val notes = if (onTimestampClick == null) parsed else linkTimestamps(parsed, durationMs, onTimestampClick)
        val images = notes.getSpans(0, notes.length, ImageSpan::class.java)
            .sortedBy(notes::getSpanStart)
        var start = 0
        for (image in images) {
            addText(notes.subSequence(start, notes.getSpanStart(image)))
            image.source?.let(::addImage)
            start = notes.getSpanEnd(image)
        }
        addText(notes.subSequence(start, notes.length))
        return childCount > 0
    }

    private fun linkTimestamps(source: Spanned, durationMs: Long, onTimestampClick: (Long) -> Unit): Spanned {
        val text = SpannableString(source)
        for (match in timestampAtLineStart.findAll(text)) {
            val timestamp = match.groups[1] ?: continue
            val start = timestamp.range.first
            val end = timestamp.range.last + 1
            if (text.getSpans(start, end, ClickableSpan::class.java).isNotEmpty()) continue
            val position = PodcastConfig.durationMillis(timestamp.value)
            if (durationMs > 0L && position >= durationMs) continue
            val link = object : ClickableSpan() {
                override fun onClick(widget: View) = onTimestampClick(position)
            }
            text.setSpan(link, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    private fun addText(source: CharSequence) {
        val text = source.trim('\n')
        if (text.isBlank()) return
        val view = LayoutInflater.from(context).inflate(R.layout.show_notes_text, this, false) as TextView
        view.text = text
        view.movementMethod = LinkMovementMethod.getInstance()
        addView(view)
    }

    private fun addImage(source: String) {
        // This single-feed app only fetches inline images from the feed's media CDN.
        val uri = source.toUri()
        if (uri.scheme != "https" || !"image.xyzcdn.net".equals(uri.host, ignoreCase = true) ||
            uri.userInfo != null || uri.port !in listOf(-1, 443) || uri.path.isNullOrBlank()) return

        val view = LayoutInflater.from(context).inflate(R.layout.show_notes_image, this, false) as ImageView
        addView(view)
        Glide.with(view).load(source).diskCacheStrategy(DiskCacheStrategy.DATA).into(view)
    }
}
