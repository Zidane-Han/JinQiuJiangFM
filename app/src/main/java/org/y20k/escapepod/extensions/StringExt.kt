/*
 * StringExt.kt
 * Implements StringExt extension methods
 * Useful extension methods for String
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


private val TAG: String = "StringExt"


/* Removes HTML tags and entities from string */
fun String.stripHtml(): String {
    return Html.fromHtml(this, Html.FROM_HTML_MODE_COMPACT).toString()
}