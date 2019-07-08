package com.kanedasoftware.masterscrobbler.main

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v14.preference.MultiSelectListPreference
import android.support.v7.preference.PreferenceFragmentCompat
import com.kanedasoftware.masterscrobbler.R


class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstance: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val listPreference = findPreference("apps_to_scrobble") as MultiSelectListPreference
        setListPreferenceData(listPreference)

    }

    private fun setListPreferenceData(lp: MultiSelectListPreference) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = Uri.withAppendedPath(MediaStore.Audio.Media.INTERNAL_CONTENT_URI, "1")
        intent.setDataAndType(uri, "audio/*")
        val playerList: List<ResolveInfo>
        val packageManager = context?.packageManager
        playerList = packageManager?.queryIntentActivities(intent,  PackageManager.GET_RESOLVED_FILTER) as List<ResolveInfo>

        var entries: MutableList<String> = mutableListOf()
        var entryValues: MutableList<String> = mutableListOf()

        for (player in playerList) {
            entries.add(packageManager.getApplicationLabel(player.activityInfo.applicationInfo).toString())
            entryValues.add(player.activityInfo.packageName)
        }

        lp.entries = entries.toTypedArray()
        lp.entryValues = entryValues.toTypedArray()
    }
}
