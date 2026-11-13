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


package com.jinqiujiang.fm

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.jinqiujiang.fm.helpers.PodcastWorkerFactory
import com.jinqiujiang.fm.helpers.AppThemeHelper
import com.jinqiujiang.fm.helpers.PreferencesHelper
import com.jinqiujiang.fm.helpers.PreferencesHelper.initPreferences


/**
 * Escapepod.class
 */
class Escapepod: Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(PodcastWorkerFactory()).build()

    /* Define log tag */
    private val TAG: String = Escapepod::class.java.simpleName


    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()
        Log.v(TAG, "Escapepod application started.")
        initPreferences()
        PreferencesHelper.clearRetiredPreferences()
        com.jinqiujiang.fm.helpers.PendingAudioDeletes.flush(this, "")

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
