/*
* ListDragListener.kt
* Implements the ListDragListener interface
* A ListDragListener defines a standard way to communicate back a change in the list drag state
*
* This file is part of
* ESCAPEPOD - Free and Open Podcast App
*
* Copyright (c) 2018-26 - Y20K.org
* Licensed under the MIT-License
* http://opensource.org/licenses/MIT
*/


package com.jinqiujiang.fm.interfaces

/*
 * ListDragListener interface
 */
interface ListDragListener {
    fun onListDragStateChanged(newState: Int)
}