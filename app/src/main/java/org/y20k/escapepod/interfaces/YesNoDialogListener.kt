/*
* YesNoDialogListener.kt
* Implements the YesNoDialogListener interface
* A YesNoDialogListener defines a standard way to to receive a response from a confirmation dialog
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
 * YesNoDialogListener interface
 */
interface YesNoDialogListener {
    fun onYesNoDialog(type: Int, dialogResult: Boolean, payload: Int, payloadString: String, dialogCancelled: Boolean)
}