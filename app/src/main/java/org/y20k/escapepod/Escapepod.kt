/*
 * Escapepod.kt
 * Implements the Escapepod class
 * Escapepod is the base Application class that sets up day and night theme
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod

import android.app.Application
import android.util.Log
import org.y20k.escapepod.helpers.AppThemeHelper
import org.y20k.escapepod.helpers.PreferencesHelper
import org.y20k.escapepod.helpers.PreferencesHelper.initPreferences


/**
 * Escapepod.class
 */
class Escapepod: Application () {

    /* Define log tag */
    private val TAG: String = Escapepod::class.java.simpleName


    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()
        Log.v(TAG, "Escapepod application started.")
        initPreferences()

        // set Dark / Light theme state
        AppThemeHelper.setTheme(PreferencesHelper.loadThemeSelection())

        // apply dynamic colors, if user enabled it
        AppThemeHelper.setupDynamicColors(this)
    }


    /* Implements onTerminate */
    override fun onTerminate() {
        super.onTerminate()
        Log.v(TAG, "Escapepod application terminated.")
    }

}