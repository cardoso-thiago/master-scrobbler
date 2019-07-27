package com.kanedasoftware.masterscrobbler.main

import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceFragmentCompat
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.utils.Utils

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstance: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val listPreference = findPreference("apps_to_scrobble") as MultiSelectListPreference
        setListPreferenceData(listPreference)

    }

    private fun setListPreferenceData(listPreference: MultiSelectListPreference) {
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
}
