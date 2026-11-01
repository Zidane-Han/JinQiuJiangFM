/*
* SearchResultListener.kt
* Implements the SearchResultListener interface
* A SearchResultListener defines a standard way to handle a tap on a search result item
*
* This file is part of
* ESCAPEPOD - Free and Open Podcast App
*
* Copyright (c) 2018-26 - Y20K.org
* Licensed under the MIT-License
* http://opensource.org/licenses/MIT
*/


package org.y20k.escapepod.interfaces

/*
 * SearchResultListener interface
 */
interface SearchResultListener {
    fun onSearchResultTapped(url: String)
}