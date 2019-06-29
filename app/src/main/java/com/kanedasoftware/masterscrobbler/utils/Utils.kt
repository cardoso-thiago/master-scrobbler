package com.kanedasoftware.masterscrobbler.utils

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.AlertDialog
import com.kanedasoftware.masterscrobbler.R
import java.math.BigInteger
import java.security.MessageDigest
import java.util.logging.Logger

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

        fun logInfo(message: String) = Logger.getLogger(Constants.LOG_TAG).info(message)

        fun buildNotification(context: Context, title: String, text: String): Notification? {
            return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(R.drawable.navigation_empty_icon)
                    .setVibrate(longArrayOf(0L))
                    .build()
        }

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

        fun verifyNotificationAccess(context: Context) = NotificationManagerCompat.getEnabledListenerPackages(context).contains("com.kanedasoftware.masterscrobbler")
    }
}