/*
 * TextViewExt.kt
 * Implements TextViewExt extension methods
 * Useful extension methods for TextView
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.extensions

import android.text.Html
import android.text.Spannable
import android.text.style.URLSpan
import android.widget.TextView
import org.y20k.escapepod.Keys
import org.y20k.escapepod.helpers.NetworkHelper


private val TAG: String = "TextViewExt"


/* Puts cleaned up HTML to a text view */
fun TextView.setCleanHtml(htmlSource: String) {
    // cap length before parsing so a multi-MB feed body can not stall the main thread (F-24)
    val boundedSource: String = if (htmlSource.length > Keys.RSS_MAX_FIELD_LENGTH) htmlSource.substring(0, Keys.RSS_MAX_FIELD_LENGTH) else htmlSource
    val cleanHtml = Html.fromHtml(boundedSource.replace(Regex("<img.+?>"), ""), Html.FROM_HTML_MODE_COMPACT) /* regex removes placeholder images */
    // neutralize tapable links with a non-http(s) scheme (file://, content://, deep links) - render them as plain text (F-23)
    if (cleanHtml is Spannable) {
        cleanHtml.getSpans(0, cleanHtml.length, URLSpan::class.java).forEach { span ->
            if (!NetworkHelper.isAllowedScheme(span.url ?: String())) {
                cleanHtml.removeSpan(span)
            }
        }
    }
    text = cleanHtml
}