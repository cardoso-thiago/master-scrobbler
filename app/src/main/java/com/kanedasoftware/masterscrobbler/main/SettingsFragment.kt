package com.kanedasoftware.masterscrobbler.main

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.preference.*
import com.jaredrummler.android.colorpicker.ColorPreference
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.utils.Utils
import io.multimoon.colorful.Colorful
import io.multimoon.colorful.CustomThemeColor
import io.multimoon.colorful.ThemeColor

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstance: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val listPreference = findPreference("apps_to_scrobble") as MultiSelectListPreference
        setPlayers(listPreference)

        val primaryColor = findPreference("color_picker_primary") as ColorPreferenceCompat
        primaryColor.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            var primaryTheme = ThemeColor.RED
            context?.let {
                Colorful().edit()
                        .setPrimaryColor(primaryTheme)
                        .apply(it) {
                            //TODO exibir dialogo informando o restart
                            val intent = Intent(it, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                        }
            }
            true
        }

        val listThemes = findPreference("app_themes") as ListPreference
        setThemes(listThemes)
        if (listThemes.value == null) {
            listThemes.setValueIndex(0)
        } else {
            listThemes.setValueIndex(listThemes.findIndexOfValue(listThemes.value))
        }

        listThemes.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            var darkTheme = false
            var customTheme = R.style.AppTheme
            var primaryTheme = ThemeColor.RED
            var accentTheme = ThemeColor.GREY

            val splitValue = newValue.toString().split("-")
            when (splitValue[0]) {
                "RED" -> primaryTheme = ThemeColor.RED
                "PINK" -> primaryTheme = ThemeColor.PINK
                "PURPLE" -> primaryTheme = ThemeColor.PURPLE
                "DEEP_PURPLE" -> primaryTheme = ThemeColor.DEEP_PURPLE
                "INDIGO" -> primaryTheme = ThemeColor.INDIGO
                "BLUE" -> primaryTheme = ThemeColor.BLUE
                "LIGHT_BLUE" -> primaryTheme = ThemeColor.LIGHT_BLUE
                "CYAN" -> primaryTheme = ThemeColor.CYAN
                "TEAL" -> primaryTheme = ThemeColor.TEAL
                "GREEN" -> primaryTheme = ThemeColor.GREEN
                "LIGHT_GREEN" -> primaryTheme = ThemeColor.LIGHT_GREEN
                "LIME" -> primaryTheme = ThemeColor.LIME
                "YELLOW" -> primaryTheme = ThemeColor.YELLOW
                "AMBER" -> primaryTheme = ThemeColor.AMBER
                "ORANGE" -> primaryTheme = ThemeColor.ORANGE
                "DEEP_ORANGE" -> primaryTheme = ThemeColor.DEEP_ORANGE
                "BROWN" -> primaryTheme = ThemeColor.BROWN
                "GREY" -> primaryTheme = ThemeColor.GREY
                "BLUE_GREY" -> primaryTheme = ThemeColor.BLUE_GREY
                "WHITE" -> primaryTheme = ThemeColor.WHITE
                "BLACK" -> primaryTheme = ThemeColor.BLACK

            }
            when (splitValue[1]) {
                "RED" -> accentTheme = ThemeColor.RED
                "PINK" -> accentTheme = ThemeColor.PINK
                "PURPLE" -> accentTheme = ThemeColor.PURPLE
                "DEEP_PURPLE" -> accentTheme = ThemeColor.DEEP_PURPLE
                "INDIGO" -> accentTheme = ThemeColor.INDIGO
                "BLUE" -> accentTheme = ThemeColor.BLUE
                "LIGHT_BLUE" -> accentTheme = ThemeColor.LIGHT_BLUE
                "CYAN" -> accentTheme = ThemeColor.CYAN
                "TEAL" -> accentTheme = ThemeColor.TEAL
                "GREEN" -> accentTheme = ThemeColor.GREEN
                "LIGHT_GREEN" -> accentTheme = ThemeColor.LIGHT_GREEN
                "LIME" -> accentTheme = ThemeColor.LIME
                "YELLOW" -> accentTheme = ThemeColor.YELLOW
                "AMBER" -> accentTheme = ThemeColor.AMBER
                "ORANGE" -> accentTheme = ThemeColor.ORANGE
                "DEEP_ORANGE" -> accentTheme = ThemeColor.DEEP_ORANGE
                "BROWN" -> accentTheme = ThemeColor.BROWN
                "GREY" -> accentTheme = ThemeColor.GREY
                "BLUE_GREY" -> accentTheme = ThemeColor.BLUE_GREY
                "WHITE" -> accentTheme = ThemeColor.WHITE
                "BLACK" -> accentTheme = ThemeColor.BLACK
            }
            when (splitValue[2]) {
                "DARK" -> {
                    darkTheme = true
                    customTheme = R.style.AppThemeDark
                }
            }
            context?.let {
                Colorful().edit()
                        .setPrimaryColor(primaryTheme)
                        .setAccentColor(accentTheme)
                        .setCustomThemeOverride(customTheme)
                        .setDarkTheme(darkTheme)
                        .apply(it) {
                            //TODO exibir dialogo informando o restart
                            val intent = Intent(it, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                        }
            }
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

        //TODO colocar valores no xml
        entries.add("RED/GRAY")
        entryValues.add("RED-GRAY-LIGHT")

        entries.add("LIGHT BLUE/YELLOW")
        entryValues.add("LIGHT_BLUE-YELLOW-LIGHT")

        entries.add("BLUE/RED")
        entryValues.add("BLUE-RED-LIGHT")

        entries.add("DEEP ORANGE/LIGHT BLUE")
        entryValues.add("DEEP_ORANGE-LIGHT_BLUE-LIGHT")

        entries.add("LIGHT GREEN/GREEN")
        entryValues.add("LIGHT_GREEN-GREEN-LIGHT")

        entries.add("TEAL/YELLOW")
        entryValues.add("TEAL-YELLOW-LIGHT")

        entries.add("GREY/PINK")
        entryValues.add("GREY-PINK-LIGHT")

        entries.add("RED/GRAY (DARK)")
        entryValues.add("RED-GRAY-DARK")

        entries.add("LIGHT BLUE/YELLOW (DARK)")
        entryValues.add("LIGHT_BLUE-YELLOW-DARK")

        entries.add("BLUE/RED (DARK)")
        entryValues.add("BLUE-RED-DARK")

        entries.add("DEEP ORANGE/LIGHT BLUE (DARK)")
        entryValues.add("DEEP_ORANGE-LIGHT_BLUE-DARK")

        entries.add("LIGHT GREEN/GREEN (DARK)")
        entryValues.add("LIGHT_GREEN-GREEN-DARK")

        entries.add("TEAL/YELLOW (DARK)")
        entryValues.add("TEAL-YELLOW-DARK")

        entries.add("GREY/PINK (DARK)")
        entryValues.add("GREY-PINK-DARK")

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
