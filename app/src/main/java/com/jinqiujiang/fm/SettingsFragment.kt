/*
 * SettingsFragment.kt
 * Implements the SettingsFragment fragment
 * A SettingsFragment displays the user accessible settings of the app
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jinqiujiang.fm

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.jinqiujiang.fm.extensions.setCleanHtml
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.contains
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.transition.MaterialSharedAxis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jinqiujiang.fm.database.CollectionDatabase
import com.jinqiujiang.fm.dialogs.YesNoDialog
import com.jinqiujiang.fm.helpers.AppThemeHelper
import com.jinqiujiang.fm.helpers.CollectionHelper
import com.jinqiujiang.fm.helpers.FileHelper
import com.jinqiujiang.fm.helpers.PreferencesHelper
import com.jinqiujiang.fm.helpers.UiHelper
import com.jinqiujiang.fm.interfaces.ListDragListener
import com.jinqiujiang.fm.interfaces.PlayerContainerStateChangeListener
import com.jinqiujiang.fm.interfaces.YesNoDialogListener


/*
 * SettingsFragment class
 */
class SettingsFragment: PreferenceFragmentCompat(), PlayerContainerStateChangeListener, YesNoDialogListener {

    /* Define log tag */
    private val TAG: String = SettingsFragment::class.java.simpleName


    /* Main class variables */
    private lateinit var preferenceDeleteAll: Preference
    private lateinit var toolbar: MaterialToolbar
    private lateinit var systemBars: Insets
    private var listDragListener: ListDragListener? = null


    /* Overrides onViewCreated from PreferenceFragmentCompat */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // use material shared axis transitions
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)

        // set up toolbar
        toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val navController = findNavController()
        toolbar.setupWithNavController(navController)

        // add scroll listener to detect when list is being dragged
        val preferencesList: RecyclerView = listView
        preferencesList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                listDragListener?.onListDragStateChanged(newState)
            }
        })

        // set up edge to edge display
        setupEdgeToEdge(view)
    }


    /* Overrides onResume from Fragment */
    override fun onResume() {
        super.onResume()
        // set up list drag listener
        val mainActivity = activity as? BaseMainActivity
        if (mainActivity != null) {
            listDragListener = mainActivity.layout as ListDragListener
            mainActivity.layout.playerContainerStateChangeListener = this
        }
    }


    /* Overrides onPause from Fragment */
    override fun onPause() {
        super.onPause()
        // unregister list drag and player container state change listeners
        val mainActivity = activity as? BaseMainActivity
        if (mainActivity != null) {
            listDragListener = null
            mainActivity.layout.playerContainerStateChangeListener = null
        }
    }

    /* Overrides onCreatePreferences from PreferenceFragmentCompat */
    @android.annotation.SuppressLint("UnsafeOptInUsageError")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        val screen = preferenceManager.createPreferenceScreen(preferenceManager.context)

        // set up "App Theme" preference
        val preferenceThemeSelection: ListPreference = ListPreference(activity as Context)
        preferenceThemeSelection.title = getString(R.string.pref_theme_selection_title)
        preferenceThemeSelection.setIcon(R.drawable.ic_smartphone_24dp)
        preferenceThemeSelection.key = Keys.PREF_THEME_SELECTION
        preferenceThemeSelection.summary = "${getString(R.string.pref_theme_selection_summary)} ${PreferencesHelper.getCurrentTheme(preferenceManager.context)}"
        preferenceThemeSelection.entries = arrayOf(getString(R.string.pref_theme_selection_mode_device_default), getString(R.string.pref_theme_selection_mode_light), getString(R.string.pref_theme_selection_mode_dark))
        preferenceThemeSelection.entryValues = arrayOf(Keys.STATE_THEME_FOLLOW_SYSTEM, Keys.STATE_THEME_LIGHT_MODE, Keys.STATE_THEME_DARK_MODE)
        preferenceThemeSelection.setDefaultValue(Keys.STATE_THEME_FOLLOW_SYSTEM)
        preferenceThemeSelection.setOnPreferenceChangeListener { preference, newValue ->
            if (preference is ListPreference) {
                val index: Int = preference.entryValues.indexOf(newValue)
                preferenceThemeSelection.summary = "${getString(R.string.pref_theme_selection_summary)} ${preference.entries[index]}"
                return@setOnPreferenceChangeListener true
            } else {
                return@setOnPreferenceChangeListener false
            }
        }

        // set up "Dynamic Colors" preference (Android 12+ only)
        val preferenceEnableDynamicColors: SwitchPreferenceCompat = SwitchPreferenceCompat(activity as Context)
        preferenceEnableDynamicColors.title = getString(R.string.pref_dynamic_colors_title)
        preferenceEnableDynamicColors.setIcon(R.drawable.ic_color_palette_24dp)
        preferenceEnableDynamicColors.key = Keys.PREF_DYNAMIC_COLORS
        preferenceEnableDynamicColors.summaryOn = getString(R.string.pref_dynamic_colors_summary_enabled)
        preferenceEnableDynamicColors.summaryOff = getString(R.string.pref_dynamic_colors_summary_disabled)
        preferenceEnableDynamicColors.isVisible = DynamicColors.isDynamicColorAvailable()
        preferenceEnableDynamicColors.setDefaultValue(PreferencesHelper.loadDynamicColorsEnabled())
        preferenceEnableDynamicColors.setOnPreferenceChangeListener { preference, newValue ->
            val dynamicColorsEnabled = newValue as Boolean
            PreferencesHelper.saveDynamicColorsEnabled(dynamicColorsEnabled) // todo check if necessary
            AppThemeHelper.setupDynamicColors(requireActivity().application)
            requireActivity().recreate()
            listView.findNavController().navigate(R.id.main_fragment_destination)
            true
        }

        // set up "User Interface Transparency Effect" preference
        val preferenceUserInterfaceTransparencyEffect: SwitchPreferenceCompat = SwitchPreferenceCompat(activity as Context)
        preferenceUserInterfaceTransparencyEffect.title = getString(R.string.pref_user_interface_transparency_effect_title)
        preferenceUserInterfaceTransparencyEffect.setIcon(R.drawable.ic_transparency_24dp) // or another appropriate icon
        preferenceUserInterfaceTransparencyEffect.key = Keys.PREF_USER_INTERFACE_TRANSPARENCY_EFFECT
        preferenceUserInterfaceTransparencyEffect.summaryOn = getString(R.string.pref_user_interface_transparency_effect_summary_enabled)
        preferenceUserInterfaceTransparencyEffect.summaryOff = getString(R.string.pref_user_interface_transparency_effect_summary_disabled)
        preferenceUserInterfaceTransparencyEffect.setDefaultValue(PreferencesHelper.loadUserInterfaceTransparencyEffect())

        // set up "Background Refresh" preference
        val preferenceBackgroundRefresh: SwitchPreferenceCompat = SwitchPreferenceCompat(activity as Context)
        preferenceBackgroundRefresh.title = getString(R.string.pref_background_refresh_title)
        preferenceBackgroundRefresh.setIcon(R.drawable.ic_autorenew_24dp)
        preferenceBackgroundRefresh.key = Keys.PREF_BACKGROUND_REFRESH
        preferenceBackgroundRefresh.summaryOn = getString(R.string.pref_background_refresh_summary_enabled)
        preferenceBackgroundRefresh.summaryOff = getString(R.string.pref_background_refresh_summary_disabled)
        preferenceBackgroundRefresh.setDefaultValue(Keys.DEFAULT_BACKGROUND_REFRESH_MODE)

        // set up "episode Download over mobile" preference
        val preferenceEpisodeDownloadOverMobile: SwitchPreferenceCompat = SwitchPreferenceCompat(activity as Context)
        preferenceEpisodeDownloadOverMobile.title = getString(R.string.pref_episode_download_over_mobile_title)
        preferenceEpisodeDownloadOverMobile.setIcon(R.drawable.ic_signal_cellular_24dp)
        preferenceEpisodeDownloadOverMobile.key = Keys.PREF_EPISODE_DOWNLOAD_OVER_MOBILE
        preferenceEpisodeDownloadOverMobile.summaryOn = getString(R.string.pref_episode_download_over_mobile_summary_enabled)
        preferenceEpisodeDownloadOverMobile.summaryOff = getString(R.string.pref_episode_download_over_mobile_summary_disabled)
        preferenceEpisodeDownloadOverMobile.setDefaultValue(Keys.DEFAULT_EPISODE_DOWNLOAD_OVER_MOBILE_MODE)

        // set up "Delete All" preference
        preferenceDeleteAll = Preference(activity as Context)
        preferenceDeleteAll.title = getString(R.string.pref_delete_all_title)
        preferenceDeleteAll.setIcon(R.drawable.ic_delete_24dp)
        preferenceDeleteAll.summary = "${getString(R.string.pref_delete_all_summary)} ${getAvailableSpace()}"
        preferenceDeleteAll.setOnPreferenceClickListener {
            // stop playback using intent (we have no media controller reference here)
            val intent = Intent(activity, PlayerService::class.java)
            intent.action = Keys.ACTION_STOP
            (preferenceManager.context).startService(intent)
            // show dialog
            YesNoDialog(this).show(context = preferenceManager.context, type = Keys.DIALOG_DELETE_DOWNLOADS, message = R.string.dialog_yes_no_message_delete_downloads, yesButton = R.string.dialog_yes_no_positive_button_delete_downloads)
            return@setOnPreferenceClickListener true
        }

        // set up "Auto Cleanup" preference
        val preferenceDownloadAutoCleanup = SwitchPreferenceCompat(activity as Context).apply {
            title = getString(R.string.pref_download_auto_cleanup_title)
            setIcon(R.drawable.ic_auto_delete_24dp)
            key = Keys.PREF_DOWNLOAD_AUTO_CLEANUP_ENABLED
            summaryOn = getString(R.string.pref_download_auto_cleanup_enabled)
            summaryOff = getString(R.string.pref_download_auto_cleanup_disabled)
            setDefaultValue(Keys.DEFAULT_DOWNLOAD_AUTO_CLEANUP_ENABLED)
        }

        // set up "App Version" preference
        val preferenceAppVersion: Preference = object : Preference(activity as Context) {
            // enable long click on preference (credit: https://www.joehxblog.com/how-to-add-a-long-click-to-an-androidx-preference/)
            override fun onBindViewHolder(holder: PreferenceViewHolder) {
                super.onBindViewHolder(holder)
                val itemView: View = holder.itemView
                itemView.setOnLongClickListener {
                    // display the total number of episodes stored in database
                    displayTotalNumberOfEpisodesInDatabase()
                    return@setOnLongClickListener true
                }
            }
        }
        preferenceAppVersion.summary = "今球讲 0.9.0 · Based on Escapepod · MIT © Y20K.org 2018–26"
        preferenceAppVersion.title = getString(R.string.pref_app_version_title)
        preferenceAppVersion.setIcon(R.drawable.ic_info_24dp)
        preferenceAppVersion.summary = getString(R.string.brand_about)
        preferenceAppVersion.setOnPreferenceClickListener {
            // copy to clipboard
            val clip: ClipData = ClipData.newPlainText("simple text", preferenceAppVersion.summary)
            val cm: ClipboardManager = preferenceManager.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU){
                Toast.makeText(activity as Context, R.string.toast_message_copied_to_clipboard, Toast.LENGTH_LONG).show()
            }
            return@setOnPreferenceClickListener true
        }

        // set preference categories
        val preferenceCategoryGeneral: PreferenceCategory = PreferenceCategory(activity as Context)
        preferenceCategoryGeneral.title = getString(R.string.pref_general_title)
        preferenceCategoryGeneral.contains(preferenceThemeSelection)
        preferenceCategoryGeneral.contains(preferenceEnableDynamicColors)
        preferenceCategoryGeneral.contains(preferenceUserInterfaceTransparencyEffect)
        preferenceCategoryGeneral.contains(preferenceBackgroundRefresh)
        preferenceCategoryGeneral.contains(preferenceEpisodeDownloadOverMobile)

        val preferenceCategoryMaintenance: PreferenceCategory = PreferenceCategory(activity as Context)
        preferenceCategoryMaintenance.title = getString(R.string.pref_maintenance_title)
        preferenceCategoryMaintenance.contains(preferenceDeleteAll)

        val preferenceCategoryAdvanced: PreferenceCategory = PreferenceCategory(activity as Context)
        preferenceCategoryAdvanced.title = getString(R.string.pref_advanced_title)
        preferenceCategoryAdvanced.contains(preferenceDownloadAutoCleanup)

        val preferenceCategoryAbout: PreferenceCategory = PreferenceCategory(activity as Context)
        preferenceCategoryAbout.title = getString(R.string.pref_about_title)
        preferenceCategoryAbout.contains(preferenceAppVersion)

        // setup preference screen
        screen.addPreference(preferenceCategoryGeneral)
        screen.addPreference(preferenceThemeSelection)
        screen.addPreference(preferenceEnableDynamicColors)
        screen.addPreference(preferenceUserInterfaceTransparencyEffect)
        screen.addPreference(preferenceBackgroundRefresh)
        screen.addPreference(preferenceEpisodeDownloadOverMobile)
        screen.addPreference(preferenceCategoryMaintenance)


        screen.addPreference(preferenceDeleteAll)
        screen.addPreference(preferenceCategoryAdvanced)

        screen.addPreference(preferenceDownloadAutoCleanup)
        screen.addPreference(preferenceCategoryAbout)
        screen.addPreference(Preference(requireContext()).apply {
            key = "programme_about"
            title = getString(R.string.programme_about_title)
            summary = getString(R.string.programme_about_summary)
            setIcon(R.drawable.ic_info_24dp)
            setOnPreferenceClickListener {
                showProgrammeAbout()
                true
            }
        })
        screen.addPreference(preferenceAppVersion)
        screen.addPreference(Preference(requireContext()).apply {
            title = getString(R.string.open_source_license)
            summary = "Escapepod · MIT · Y20K.org"
            setOnPreferenceClickListener {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.open_source_license)
                    .setMessage(requireContext().assets.open("LICENSE.txt").bufferedReader().use { it.readText() })
                    .setPositiveButton(android.R.string.ok, null).show()
                true
            }
        })
        preferenceScreen = screen
    }


    private fun showProgrammeAbout() {
        val context = requireContext()
        lifecycleScope.launch {
            val description = withContext(IO) {
                CollectionDatabase.getInstance(context.applicationContext).podcastDescriptionDao()
                    .findByRemotePodcastFeedLocation(PodcastConfig.FEED_URL)?.description
            }.orEmpty().ifBlank { getString(R.string.programme_about_description) }
            if (!isAdded) return@launch
            val padding = (24 * resources.displayMetrics.density).toInt()
            val text = com.google.android.material.textview.MaterialTextView(context).apply {
                textSize = 16f
                setLineSpacing(6 * resources.displayMetrics.density, 1f)
                setPadding(padding, padding / 2, padding, padding)
                setCleanHtml(description)
            }
            val scroll = android.widget.ScrollView(context).apply { addView(text) }
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(R.string.programme_about_title).setView(scroll)
                .setPositiveButton(android.R.string.ok, null).show()
        }
    }


    /* Overrides onYesNoDialog from YesNoDialogListener */
    override fun onYesNoDialog(type: Int, dialogResult: Boolean, payload: Int, payloadString: String, dialogCancelled: Boolean) {
        when (type) {
            // handle delete downloads dialog
            Keys.DIALOG_DELETE_DOWNLOADS -> {
                if (dialogResult) deleteAllEpisodes()
            }
        }
    }


    /* Overrides onPlayerContainerStateChanged from PlayerContainerStateChangeListener */
    override fun onPlayerContainerStateChanged(playerVisible: Boolean, upNextVisible: Boolean) {
        updateListBottomPadding(playerVisible = playerVisible, upNextVisible = upNextVisible)
    }


    /* Deletes all episode audio files - deep clean */
    private fun deleteAllEpisodes() {
        // delete audio files for all episode

        // reset all local audio references in database
        CoroutineScope(IO).launch {
            val collectionDatabase = CollectionDatabase.getInstance(activity as Context)
            CollectionHelper.deleteAllAudioFiles(requireContext().applicationContext)
            collectionDatabase.episodeDao().resetLocalAudioReferencesForAllEpisodes()
        }
        // reset current and Up Next media id
        // Clearing downloads preserves the current episode and listening progress.
        // update summary
        preferenceDeleteAll.summary = "${getString(R.string.pref_delete_all_summary)} ${getAvailableSpace()}"
        Toast.makeText(activity as Context, R.string.toast_message_deleting_downloads, Toast.LENGTH_LONG).show()
    }


    /* Creates readable string containing available and used storage space */
    private fun getAvailableSpace(): String {
        val externalFilesDir = preferenceManager.context.getExternalFilesDir("")
        val audioFolder = preferenceManager.context.getExternalFilesDir(Keys.FOLDER_AUDIO)
        return "${getString(R.string.pref_delete_all_storage_space_available)}: ${FileHelper.getAvailableFreeSpaceForFolder(externalFilesDir)} | ${getString(R.string.pref_delete_all_storage_space_used)}: ${FileHelper.getFolderSize(audioFolder)}" }


    /* Displays the total number of episodes stored in the local database */
    private fun displayTotalNumberOfEpisodesInDatabase() {
        // reset all local audio references in database
        CoroutineScope(IO).launch {
            val collectionDatabase = CollectionDatabase.getInstance(activity as Context)
            val totalNumberOfEpisodesInDatabase: Int = collectionDatabase.episodeDao().getSize()
            withContext(Main) {
                Toast.makeText(activity as Context, (activity as Context).getString(R.string.toast_message_total_number_of_episodes) + " " + totalNumberOfEpisodesInDatabase, Toast.LENGTH_LONG).show()
            }
        }
    }


    /* Updates the bottom padding of the episodes list based on player and up next visibility */
    private fun updateListBottomPadding(playerVisible: Boolean, upNextVisible: Boolean) {
        val preferencesList: RecyclerView = listView
        preferencesList.updatePadding(bottom = systemBars.bottom + UiHelper.getPlayerContainerHeight(requireContext(), playerVisible, upNextVisible))
    }


    /* Sets up margins/paddings for edge to edge view */
    private fun setupEdgeToEdge(view: View) {
        val preferencesList: RecyclerView = listView
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            // get measurements for status and navigation bar
            systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // apply measurements to toolbar
            toolbar.updateLayoutParams<LinearLayout.LayoutParams> {
                topMargin = systemBars.top
            }
            // apply measurements to preferences list
            val mainActivity = activity as? BaseMainActivity
            val playerVisible = mainActivity?.layout?.playerCardView?.isVisible ?: true
            val upNextVisible = mainActivity?.layout?.upNextCardView?.isVisible ?: false
            preferencesList.updatePadding(bottom = systemBars.bottom + UiHelper.getPlayerContainerHeight(requireContext(), playerVisible, upNextVisible))
            // return the insets
            insets
        }
    }

}