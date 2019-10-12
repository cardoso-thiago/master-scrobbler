package com.kanedasoftware.masterscrobbler.main

import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.jaredrummler.cyanea.prefs.CyaneaSettingsFragment
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.utils.NotificationUtils
import com.kanedasoftware.masterscrobbler.utils.Utils
import org.koin.android.ext.android.inject

class SettingsFragment : CyaneaSettingsFragment() {

    private val utils: Utils by inject()
    private val notificationUtils: NotificationUtils by inject()

    @Suppress("UNCHECKED_CAST")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val listPreference = findPreference("apps_to_scrobble") as MultiSelectListPreference

        listPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if(newValue is HashSet<*>){
                val values = newValue as HashSet<String>
                if(values.size == 0){
                    notificationUtils.sendNoPlayerNotification()
                    utils.stopMediaService()
                } else {
                    notificationUtils.cancelNoPlayerNotification()
                    utils.startMediaService()
                }
            }
            true
        }
        setPlayers(listPreference)
    }

    private fun setPlayers(listPreference: MultiSelectListPreference) {
        val packageManager = context?.packageManager
        val playerList: List<ResolveInfo> = utils.getPlayerList(packageManager)

        val entries: MutableList<String> = mutableListOf()
        val entryValues: MutableList<String> = mutableListOf()

        for (player in playerList) {
            entries.add(packageManager?.getApplicationLabel(player.activityInfo.applicationInfo).toString())
            entryValues.add(player.activityInfo.packageName)
        }

        val playersMap = utils.getPlayersMap()
        if (playersMap.isNotEmpty()) {
            for (player in playersMap) {
                entries.add(player.value)
                entryValues.add(player.key)
            }
        }
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
