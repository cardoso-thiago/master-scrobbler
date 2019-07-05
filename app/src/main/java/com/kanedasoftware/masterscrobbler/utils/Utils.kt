package com.kanedasoftware.masterscrobbler.utils

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.AlertDialog
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


class Utils {

    companion object {

        private const val DEBUG = true

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
            if(DEBUG){
                appendLog("[DEBUG] - $message")
            }
        }

        fun log(message: String) {
            val log = AnkoLogger(Constants.LOG_TAG)
            log.info(message)
            if(DEBUG){
                appendLog("[INFO] - $message")
            }
        }

        fun logWarning(message: String) {
            val log = AnkoLogger(Constants.LOG_TAG)
            log.warn(message)
            if(DEBUG){
                appendLog("[WARN] - $message")
            }
        }

        fun logError(message: String) {
            val log = AnkoLogger(Constants.LOG_TAG)
            log.error(message)
            if(DEBUG){
                appendLog("[ERROR] - $message")
            }
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

        fun buildNotification(context: Context, title: String, text: String): Notification? {
            return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_stat_cassette)
                    .setVibrate(longArrayOf(0L))
                    .build()
        }

        fun isConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo
            return activeNetwork != null
        }

        fun appendLog(text: String) {
            val logFile = File("sdcard/MasterScrobbler/MasterScrobbler.log")
            val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm:ss", Locale.ENGLISH)
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
    }
}