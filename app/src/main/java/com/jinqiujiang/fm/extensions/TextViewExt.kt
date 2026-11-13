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


package com.jinqiujiang.fm.extensions

import android.text.Html
import android.text.Spannable
import android.text.Spanned
import android.text.style.URLSpan
import android.widget.TextView
import com.jinqiujiang.fm.Keys
import com.jinqiujiang.fm.helpers.NetworkHelper


/* Puts cleaned up HTML to a text view */
fun TextView.setCleanHtml(htmlSource: String) {
    text = parseCleanHtml(htmlSource)
}

internal fun parseCleanHtml(htmlSource: String, imageGetter: Html.ImageGetter? = null): Spanned {
    // cap length before parsing so a multi-MB feed body can not stall the main thread (F-24)
    val boundedSource = htmlSource.take(Keys.RSS_MAX_FIELD_LENGTH)
    val source = if (imageGetter == null) boundedSource.replace(Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE), "") else boundedSource
    val cleanHtml = Html.fromHtml(source, Html.FROM_HTML_MODE_COMPACT, imageGetter, null)
    // neutralize tapable links with a non-http(s) scheme (file://, content://, deep links) - render them as plain text (F-23)
    if (cleanHtml is Spannable) {
        cleanHtml.getSpans(0, cleanHtml.length, URLSpan::class.java).forEach { span ->
            if (!NetworkHelper.isAllowedScheme(span.url ?: String())) {
                cleanHtml.removeSpan(span)
            }
        }
    }
    return cleanHtml
}
