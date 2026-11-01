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


package org.y20k.escapepod

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.graphics.Insets
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
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
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.dialogs.YesNoDialog
import org.y20k.escapepod.helpers.AppThemeHelper
import org.y20k.escapepod.helpers.CollectionHelper
import org.y20k.escapepod.helpers.DownloadHelper
import org.y20k.escapepod.helpers.FileHelper
import org.y20k.escapepod.helpers.PreferencesHelper
import org.y20k.escapepod.helpers.UiHelper
import org.y20k.escapepod.interfaces.ListDragListener
import org.y20k.escapepod.interfaces.PlayerContainerStateChangeListener
import org.y20k.escapepod.interfaces.YesNoDialogListener
import org.y20k.escapepod.xml.OpmlHelper


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

        // set up "Tap Anywhere" preference
        val preferenceEnableTapAnywherePlayback: SwitchPreferenceCompat = SwitchPreferenceCompat(activity as Context)
        preferenceEnableTapAnywherePlayback.title = getString(R.string.pref_tap_anywhere_playback_title)
        preferenceEnableTapAnywherePlayback.setIcon(R.drawable.ic_play_circle_outline_24dp)
        preferenceEnableTapAnywherePlayback.key = Keys.PREF_TAP_ANYWHERE_PLAYBACK
        preferenceEnableTapAnywherePlayback.summaryOn = getString(R.string.pref_tap_anywhere_playback_summary_enabled)
        preferenceEnableTapAnywherePlayback.summaryOff = getString(R.string.pref_tap_anywhere_playback_summary_disabled)
        preferenceEnableTapAnywherePlayback.setDefaultValue(PreferencesHelper.loadTapAnyWherePlayback())

        // set up "OPML Export" preference
        val preferenceOpmlExport: Preference = Preference(activity as Context)
        preferenceOpmlExport.title = getString(R.string.pref_opml_export_title)
        preferenceOpmlExport.setIcon(R.drawable.ic_save_24dp)
        preferenceOpmlExport.summary = getString(R.string.pref_opml_export_summary)
        preferenceOpmlExport.setOnPreferenceClickListener {
            openSaveOpmlDialog()
            return@setOnPreferenceClickListener true
        }

        // set up "OPML Import" preference
        val preferenceOpmlImport: Preference = Preference(activity as Context)
        preferenceOpmlImport.title = getString(R.string.pref_opml_import_title)
        preferenceOpmlImport.setIcon(R.drawable.ic_folder_24)
        preferenceOpmlImport.summary = getString(R.string.pref_opml_import_summary)
        preferenceOpmlImport.setOnPreferenceClickListener {
            openImportOpmlDialog()
            return@setOnPreferenceClickListener true
        }

        // set up "Update Covers" preference
        val preferenceUpdateCovers: Preference = Preference(activity as Context)
        preferenceUpdateCovers.title = getString(R.string.pref_update_covers_title)
        preferenceUpdateCovers.setIcon(R.drawable.ic_image_24dp)
        preferenceUpdateCovers.summary = getString(R.string.pref_update_covers_summary)
        preferenceUpdateCovers.setOnPreferenceClickListener {
            // show dialog
            YesNoDialog(this).show(context = preferenceManager.context, type = Keys.DIALOG_UPDATE_COVERS, message = R.string.dialog_yes_no_message_update_covers, yesButton = R.string.dialog_yes_no_positive_button_update_covers)
            return@setOnPreferenceClickListener true
        }

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

        // set up "Search Provider" preference
        val preferenceSearchProviderSelection: ListPreference = ListPreference(activity as Context)
        preferenceSearchProviderSelection.title = getString(R.string.pref_search_provider_selection_title)
        preferenceSearchProviderSelection.setIcon(R.drawable.ic_search_24dp)
        preferenceSearchProviderSelection.key = Keys.PREF_PODCAST_SEARCH_PROVIDER_SELECTION
        preferenceSearchProviderSelection.summary = "${getString(R.string.pref_search_provider_selection_summary)} ${PreferencesHelper.getCurrentPodcastSearchProvider(preferenceManager.context)}"
        preferenceSearchProviderSelection.entries = arrayOf(getString(R.string.pref_search_provider_selection_gpodder), getString(R.string.pref_search_provider_selection_podcastindex))
        preferenceSearchProviderSelection.entryValues = arrayOf(Keys.PODCAST_SEARCH_PROVIDER_GPODDER, Keys.PODCAST_SEARCH_PROVIDER_PODCASTINDEX)
        preferenceSearchProviderSelection.setDefaultValue(Keys.PODCAST_SEARCH_PROVIDER_PODCASTINDEX)
        preferenceSearchProviderSelection.setOnPreferenceChangeListener { preference, newValue ->
            if (preference is ListPreference) {
                val index: Int = preference.entryValues.indexOf(newValue)
                preferenceSearchProviderSelection.summary = "${getString(R.string.pref_search_provider_selection_summary)} ${preference.entries[index]}"
                return@setOnPreferenceChangeListener true
            } else {
                return@setOnPreferenceChangeListener false
            }
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
        preferenceAppVersion.title = getString(R.string.pref_app_version_title)
        preferenceAppVersion.setIcon(R.drawable.ic_info_24dp)
        preferenceAppVersion.summary = "${getString(R.string.pref_app_version_summary)} ${BuildConfig.VERSION_NAME} (${getString(R.string.app_version_name)})"
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
        preferenceCategoryGeneral.contains(preferenceEnableTapAnywherePlayback)

        val preferenceCategoryMaintenance: PreferenceCategory = PreferenceCategory(activity as Context)
        preferenceCategoryMaintenance.title = getString(R.string.pref_maintenance_title)
        preferenceCategoryMaintenance.contains(preferenceOpmlExport)
        preferenceCategoryMaintenance.contains(preferenceOpmlImport)
        preferenceCategoryMaintenance.contains(preferenceUpdateCovers)
        preferenceCategoryMaintenance.contains(preferenceDeleteAll)

        val preferenceCategoryAdvanced: PreferenceCategory = PreferenceCategory(activity as Context)
        preferenceCategoryAdvanced.title = getString(R.string.pref_advanced_title)
        preferenceCategoryAdvanced.contains(preferenceSearchProviderSelection)
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
        screen.addPreference(preferenceEnableTapAnywherePlayback)
        screen.addPreference(preferenceCategoryMaintenance)
        screen.addPreference(preferenceOpmlExport)
        screen.addPreference(preferenceOpmlImport)
        screen.addPreference(preferenceUpdateCovers)
        screen.addPreference(preferenceDeleteAll)
        screen.addPreference(preferenceCategoryAdvanced)
        screen.addPreference(preferenceSearchProviderSelection)
        screen.addPreference(preferenceDownloadAutoCleanup)
        screen.addPreference(preferenceCategoryAbout)
        screen.addPreference(preferenceAppVersion)
        preferenceScreen = screen
    }


    /* Overrides onYesNoDialog from YesNoDialogListener */
    override fun onYesNoDialog(type: Int, dialogResult: Boolean, payload: Int, payloadString: String, dialogCancelled: Boolean) {
        when (type) {
            // handle delete downloads dialog
            Keys.DIALOG_DELETE_DOWNLOADS -> {
                if (dialogResult) deleteAllEpisodes()
            }
            // handle update covers dialog
            Keys.DIALOG_UPDATE_COVERS -> {
                if (dialogResult) DownloadHelper.updateCovers(activity as Context)
            }
        }
    }


    /* Overrides onPlayerContainerStateChanged from PlayerContainerStateChangeListener */
    override fun onPlayerContainerStateChanged(playerVisible: Boolean, upNextVisible: Boolean) {
        updateListBottomPadding(playerVisible = playerVisible, upNextVisible = upNextVisible)
    }


    /* Register the ActivityResultLauncher for saving OPML */
    private val requestSaveOpmlLauncher =
        registerForActivityResult(StartActivityForResult(), this::requestSaveOpmlResult)


    /* save OPML file to result file location */
    private fun requestSaveOpmlResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK && result.data != null) {
            val sourceUri: Uri = OpmlHelper.getOpmlUri(activity as Activity)
            val targetUri: Uri? = result.data?.data
            if (targetUri != null) {
                // copy file async (= fire & forget - no return value needed)
                CoroutineScope(IO).launch { FileHelper.saveCopyOfFileSuspended(activity as Context, sourceUri, targetUri) }
                Toast.makeText(activity as Context, R.string.toast_message_save_opml, Toast.LENGTH_LONG).show()
            }
        }
    }


    /* Register the ActivityResultLauncher for opening OPML */
    private val requestOpenOpmlLauncher =
        registerForActivityResult(StartActivityForResult(), this::requestOpenOpmlResult)


    /* open OPML file selected in file picker and import */
    private fun requestOpenOpmlResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK && result.data != null) {
            val targetUri: Uri? = result.data?.data
            if (targetUri != null) {
                // open and import OPML in player fragment
                val bundle: Bundle = bundleOf(
                    Keys.ARG_OPEN_OPML to "$targetUri"
                )
                this.findNavController().navigate(R.id.main_fragment_destination, bundle)
            }
        }
    }


    /* Deletes all episode audio files - deep clean */
    private fun deleteAllEpisodes() {
        // delete audio files for all episode
        CollectionHelper.deleteAllAudioFiles(activity as Context)
        // reset all local audio references in database
        CoroutineScope(IO).launch {
            val collectionDatabase = CollectionDatabase.getInstance(activity as Context)
            collectionDatabase.episodeDao().resetLocalAudioReferencesForAllEpisodes()
        }
        // reset current and Up Next media id
        PreferencesHelper.resetPlayerState(keepUpNextMediaId = false)
        // update summary
        preferenceDeleteAll.summary = "${getString(R.string.pref_delete_all_summary)} ${getAvailableSpace()}"
        Toast.makeText(activity as Context, R.string.toast_message_deleting_downloads, Toast.LENGTH_LONG).show()
    }


    /* Opens up a file picker to select the save location */
    private fun openSaveOpmlDialog() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = Keys.MIME_TYPE_XML
            putExtra(Intent.EXTRA_TITLE, Keys.COLLECTION_OPML_FILE)
        }
        // file gets saved in the ActivityResult
        try {
            requestSaveOpmlLauncher.launch(intent)
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to save OPML.\n$exception")
            Toast.makeText(activity as Context, R.string.toast_message_install_file_helper, Toast.LENGTH_LONG).show()
        }
    }


    /* Opens up a file picker to select an OPML file for import */
    private fun openImportOpmlDialog() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, Keys.MIME_TYPES_OPML)
        }
        // file gets saved in the ActivityResult
        try {
            requestOpenOpmlLauncher.launch(intent)
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to open file picker for OPML.\n$exception")
            // Toast.makeText(activity as Context, R.string.toast_message_install_file_helper, Toast.LENGTH_LONG).show()
        }
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