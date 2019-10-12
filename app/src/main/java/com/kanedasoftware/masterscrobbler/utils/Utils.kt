package com.kanedasoftware.masterscrobbler.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.ListView
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.services.MediaService
import de.adorsys.android.securestoragelibrary.SecurePreferences
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.error
import org.jetbrains.anko.info
import java.math.BigInteger
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class Utils constructor(appContext: Context){

    private val metaSpam = arrayOf("downloaded", ".com", ".co.", "www.", ".br")
    private val context = appContext

    fun getSig(params: Map<String, String>, method: String): String {
        val hashmapParams = HashMap(params)
        hashmapParams["api_key"] = Constants.API_KEY
        hashmapParams["method"] = method
        val sortedMap = hashmapParams.toSortedMap()
        var sigFormat = ""
        sortedMap.forEach { param ->
            sigFormat = sigFormat.plus(param.key).plus(param.value)
        }
        sigFormat = sigFormat.plus(Constants.SHARED_SECRET)

        return String.format("%032x", BigInteger(1, MessageDigest.getInstance("MD5").digest(sigFormat.toByteArray(Charsets.UTF_8))))
    }

    fun logDebug(message: String) = AnkoLogger(Constants.LOG_TAG).debug(message)

    fun log(message: String) = AnkoLogger(Constants.LOG_TAG).info(message)

    fun logError(message: String) = AnkoLogger(Constants.LOG_TAG).error(message)

    fun isConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null
    }

    fun getDateTimeFromEpoch(timestamp: Int): String? {
        return try {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val netDate = Date(timestamp.toLong() * 1000L)
            sdf.format(netDate)
        } catch (e: Exception) {
            e.toString()
        }
    }

    fun convertUTCToLocal(date: String): String {
        val simpleDateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
        simpleDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val outputSdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val localDate = simpleDateFormat.parse(date)
        return outputSdf.format(localDate)
    }

    fun setListViewHeightBasedOnItems(listView: ListView) {
        val listAdapter = listView.adapter ?: return
        val numberOfItems = listAdapter.count
        var totalItemsHeight = 0
        for (itemPos in 0 until numberOfItems) {
            val item = listAdapter.getView(itemPos, null, listView)
            item.measure(0, 0)
            totalItemsHeight += item.measuredHeight
        }
        val totalDividersHeight = listView.dividerHeight * (numberOfItems - 1)
        val params = listView.layoutParams
        params.height = totalItemsHeight + totalDividersHeight
        listView.layoutParams = params
        listView.requestLayout()
    }

    fun getPlayerList(packageManager: PackageManager?): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = Uri.withAppendedPath(MediaStore.Audio.Media.INTERNAL_CONTENT_URI, "1")
        intent.setDataAndType(uri, "audio/*")
        val playerList: List<ResolveInfo>
        playerList = packageManager?.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER) as List<ResolveInfo>
        return playerList
    }

    fun getPackagePlayerList(packageManager: PackageManager?): List<String> {
        val players = getPlayerList(packageManager)
        val packageList: MutableList<String> = ArrayList()
        for (player in players) {
            packageList.add(player.activityInfo.packageName)
        }
        return packageList
    }

    fun savePlayersMap(map: Map<String, String>) {
        val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
        val mapPlayers = Gson().toJson(map)
        editor.putString("players_map", mapPlayers).apply()
    }

    fun getPlayersMap(): MutableMap<String, String> {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val playersMap = preferences.getString("players_map", "")
        if (!playersMap.isNullOrBlank()) {
            return Gson().fromJson(playersMap, HashMap<String, String>()::class.java)
        }
        return HashMap()
    }

    fun hasAppsToScrobble(preferences: SharedPreferences?): Boolean {
        val stringSet = preferences?.getStringSet("apps_to_scrobble", HashSet<String>())
        if (stringSet != null) {
            if (stringSet.isNotEmpty()) {
                return true
            }
        }
        return false
    }

    fun clearTrack(titleContentOriginal: String): String {
        return titleContentOriginal.replace(" *\\([^)]*\\) *".toRegex(), " ")
                .replace(" *\\[[^)]*] *".toRegex(), " ")
                .replace("\\W* HD|HQ|4K|MV|M/V|Official Music Video|Music Video|Lyric Video|Official Audio( \\W*)?"
                        .toRegex(RegexOption.IGNORE_CASE)
                        , " ")
    }

    fun clearAlbum(albumOrig: String): String {
        if (albumOrig.contains("unknown", true) && albumOrig.length <= "unknown".length + 4) {
            return ""
        }
        if (metaSpam.any { albumOrig.contains(it) }) {
            return ""
        }
        return albumOrig
    }

    fun clearArtist(artistOrig: String): String {
        val splits = artistOrig.split("; ").filter { !it.isBlank() }
        if (splits.isEmpty()) {
            return ""
        }
        return splits[0]
    }

    fun isValidSessionKey(): Boolean {
        return !SecurePreferences.getStringValue(context, Constants.SECURE_SESSION_TAG, "").isNullOrBlank()
    }

    fun getPeriodParameter(value: String): String {
        return when (value) {
            context.getString(R.string.period_7day) -> "7day"
            context.getString(R.string.period_1month) -> "1month"
            context.getString(R.string.period_3month) -> "3month"
            context.getString(R.string.period_6month) -> "6month"
            context.getString(R.string.period_12month) -> "12month"
            else -> "overall"
        }
    }

    fun startMediaService() {
        if (isValidSessionKey()) {
            val i = Intent(context, MediaService::class.java)
            i.action = Constants.START_SERVICE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }

        }
    }

    fun stopMediaService() {
        val i = Intent(context, MediaService::class.java)
        i.action = Constants.STOP_SERVICE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(i)
        } else {
            context.startService(i)
        }
    }

    fun getAppContext() = context
}