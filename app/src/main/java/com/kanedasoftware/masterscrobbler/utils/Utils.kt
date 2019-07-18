package com.kanedasoftware.masterscrobbler.utils

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.AlertDialog
import android.support.v7.preference.PreferenceManager
import android.widget.ListView
import com.kanedasoftware.masterscrobbler.R
import org.jetbrains.anko.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap


const val KEY_PREF_DEBUG_MODE = "debug_mode"

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

        fun logDebug(message: String, context: Context) {
            val log = AnkoLogger(Constants.LOG_TAG)
            log.debug(message)
            if (isDebugMode(context)) {
                appendLog("[DEBUG] - $message")
            }
        }


        fun log(message: String, context: Context) {
            val log = AnkoLogger(Constants.LOG_TAG)
            log.info(message)
            if (isDebugMode(context)) {
                appendLog("[INFO] - $message")
            }
        }

        fun logWarning(message: String, context: Context) {
            val log = AnkoLogger(Constants.LOG_TAG)
            log.warn(message)
            if (isDebugMode(context)) {
                appendLog("[WARN] - $message")
            }
        }

        fun logError(message: String, context: Context) {
            val log = AnkoLogger(Constants.LOG_TAG)
            log.error(message)
            if (isDebugMode(context)) {
                appendLog("[ERROR] - $message")
            }
        }

        private fun isDebugMode(context: Context): Boolean {
            val sharedPref = PreferenceManager
                    .getDefaultSharedPreferences(context)
            return sharedPref.getBoolean(KEY_PREF_DEBUG_MODE, false)
        }

        fun verifyNotificationAccess(context: Context) = NotificationManagerCompat.getEnabledListenerPackages(context).contains("com.kanedasoftware.masterscrobbler")

        fun changeNotificationAccess(context: Context) {
            if (!verifyNotificationAccess(context)) {
                //TODO colocar os textos nos properties
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
            val notif = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_stat_cassette)
                    .setVibrate(longArrayOf(0L))
            if (image != null) {
                notif.setStyle(android.support.v4.media.app.NotificationCompat.MediaStyle()).setColorized(true)
                notif.setLargeIcon(image)
            }
            return notif.build()
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

        private fun appendLog(text: String) {
            val logFile = File("sdcard/MasterScrobbler/MasterScrobbler.log")
            val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm:ss", Locale.getDefault())
            val currentDate = sdf.format(Date())
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            var buf: BufferedWriter? = null
            try {
                buf = BufferedWriter(FileWriter(logFile, true))
                buf.append("$currentDate - $text")
                buf.newLine()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                if (buf != null) {
                    buf.flush()
                    buf.close()
                }
            }
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
    }
}