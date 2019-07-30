package com.kanedasoftware.masterscrobbler.main

import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.preference.*
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.utils.Utils

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstance: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val listPreference = findPreference("apps_to_scrobble") as MultiSelectListPreference
        setPlayers(listPreference)

        val listThemes = findPreference("app_themes") as ListPreference
        setThemes(listThemes)
        if (listThemes.value == null) {
            listThemes.setValueIndex(0)
        }

        listThemes.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            true
        }
    }

    private fun setPlayers(listPreference: MultiSelectListPreference) {
        val packageManager = context?.packageManager
        val playerList: List<ResolveInfo> = Utils.getPlayerList(packageManager)

        val entries: MutableList<String> = mutableListOf()
        val entryValues: MutableList<String> = mutableListOf()

        for (player in playerList) {
            entries.add(packageManager?.getApplicationLabel(player.activityInfo.applicationInfo).toString())
            entryValues.add(player.activityInfo.packageName)
        }

        val playersMap = context?.let { Utils.getPlayersMap(it) }
        if (playersMap != null) {
            for (player in playersMap) {
                entries.add(player.value)
                entryValues.add(player.key)
            }
        }

        listPreference.entries = entries.toTypedArray()
        listPreference.entryValues = entryValues.toTypedArray()
    }

    private fun setThemes(listPreference: ListPreference) {
        val entries: MutableList<String> = mutableListOf()
        val entryValues: MutableList<String> = mutableListOf()

        entries.add("RED/GRAY")
        entryValues.add("red_gray")

        entries.add("GREEN/BLUE")
        entryValues.add("green_blue")

        listPreference.entries = entries.toTypedArray()
        listPreference.entryValues = entryValues.toTypedArray()
    }

    override fun setPreferenceScreen(preferenceScreen: PreferenceScreen?) {
        super.setPreferenceScreen(preferenceScreen)
        if (preferenceScreen != null) {
            val count = preferenceScreen.preferenceCount
            for (i in 0 until count)
                preferenceScreen.getPreference(i)!!.isIconSpaceReserved = false
        }
    }
}
