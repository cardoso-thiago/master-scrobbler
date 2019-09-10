package com.kanedasoftware.masterscrobbler.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kanedasoftware.masterscrobbler.utils.Utils

class BootUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Utils.createQuietNotificationChannel(context)
        Utils.createNotificationChannel(context)

        val notificationAccessValidation = Utils.verifyNotificationAccess()
        //Não inicia o serviço se não tiver acesso às notificações
        if (notificationAccessValidation) {
            //Não inicia o serviço se não tiver feito o login
            if (Utils.isValidSessionKey()) {
                if (intent?.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                    val defaultSharedPreferences = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                    if (!Utils.hasAppsToScrobble(defaultSharedPreferences)) {
                        Utils.sendNoPlayerNotification()
                    } else {
                        Utils.startMediaService()
                    }
                }
            }
        }
    }
}