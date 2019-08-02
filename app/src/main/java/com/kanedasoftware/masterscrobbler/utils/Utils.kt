package com.kanedasoftware.masterscrobbler.utils

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.main.MainActivity
import com.kanedasoftware.masterscrobbler.main.SettingsActivity
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

private val metaSpam = arrayOf("downloaded", ".com", ".co.", "www.", ".br")

class Utils {
    companion object {

        fun getSig(params: Map<String, String>, method: String): String {
            val hashmapParams = HashMap<String, String>(params)
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

        fun logDebug(message: String) {
            val log = AnkoLogger(Constants.LOG_TAG)
            log.debug(message)
        }


        fun log(message: String) {
            val log = AnkoLogger(Constants.LOG_TAG)
            log.info(message)
        }

        fun logError(message: String) {
            val log = AnkoLogger(Constants.LOG_TAG)
            log.error(message)
        }


        fun verifyNotificationAccess(context: Context) = NotificationManagerCompat.getEnabledListenerPackages(context).contains("com.kanedasoftware.masterscrobbler")

        fun changeNotificationAccess(context: Context) {
            if (!verifyNotificationAccess(context)) {
                AlertDialog.Builder(context)
                        .setTitle("Acesso às Notificações")
                        .setMessage("Para o aplicativo funcionar é necessário conceder acesso às notificações. Deseja abrir a configuração?")
                        .setPositiveButton("Sim") { _, _ ->
                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        }
                        .setNegativeButton("Sair") { _, _ ->
                            android.os.Process.killProcess(android.os.Process.myPid())
                            System.exit(1)
                        }
                        .show()
            }
        }

        fun updateNotification(context: Context, title: String, text: String) {
            val notification = buildNotification(context, title, text)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(Constants.NOTIFICATION_ID, notification)
        }

        fun updateNotification(context: Context, title: String, text: String, image: Bitmap?) {
            val notification = buildNotification(context, title, text, image)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(Constants.NOTIFICATION_ID, notification)
        }

        fun buildNotification(context: Context, title: String, text: String): Notification? {
            return buildNotification(context, title, text, null)
        }

        private fun buildNotification(context: Context, title: String, text: String, image: Bitmap?): Notification? {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

            val notif = NotificationCompat.Builder(context, Constants.QUIET_NOTIFICATION_CHANNEL)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_stat_cassette)
                    .setContentIntent(pendingIntent)
                    .setVibrate(longArrayOf(0L))
            if (image != null) {
                notif.setStyle(androidx.media.app.NotificationCompat.MediaStyle()).setColorized(true)
                notif.setLargeIcon(image)
            }
            return notif.build()
        }

        fun sendNewPlayerNotification(context: Context, player: String) {
            val intent = Intent(context, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notif = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL)
                    .setContentTitle("New Player Identified")
                    .setContentText("Player $player identified. Touch to add the player to scrobble.")
                    .setSmallIcon(R.drawable.ic_stat_cassette)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                    .setAutoCancel(true)
                    .build()
            notificationManager.notify(Constants.NOTIFICATION_NEW_PLAYER_ID, notif)
        }

        fun isConnected(context: Context): Boolean {
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

        fun savePlayersMap(context: Context, map: Map<String, String>) {
            val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
            val mapPlayers = Gson().toJson(map)
            editor.putString("players_map", mapPlayers).apply()
        }

        fun getPlayersMap(context: Context): MutableMap<String, String> {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val playersMap = preferences.getString("players_map", "")
            if (!playersMap.isNullOrBlank()) {
                return Gson().fromJson(playersMap, HashMap<String, String>()::class.java)
            }
            return HashMap()
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

        fun isValidSessionKey(context: Context): Boolean {
            return !SecurePreferences.getStringValue(context, Constants.SECURE_SESSION_TAG, "").isNullOrBlank()
        }

        fun getPeriodParameter(context: Context, value: String): String {
            return when (value) {
                context.getString(R.string.period_7day) -> "7day"
                context.getString(R.string.period_1month) -> "1month"
                context.getString(R.string.period_3month) -> "3month"
                context.getString(R.string.period_6month) -> "6month"
                context.getString(R.string.period_12month) -> "12month"
                else -> "overall"
            }
        }
    }
}