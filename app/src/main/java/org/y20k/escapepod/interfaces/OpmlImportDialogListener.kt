/*
* OpmlImportDialogListener.kt
* Implements the OpmlImportDialogListener interface
* A OpmlImportDialogListener defines a standard way for to send a feed URLs back
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
 * OpmlImportDialogListener interface
 */
interface OpmlImportDialogListener {
    fun onOpmlImportDialog(feedUrls: Array<String>)
}