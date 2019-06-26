package com.kanedasoftware.masterscrobbler.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import android.preference.PreferenceManager
import com.kanedasoftware.masterscrobbler.utils.Utils


class NotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        //TODO pegar todos os apps de midia das configurações e verificar aqui pra interceptar apenas essas notificações
        //Obtém apenas as informações dos apps selecionados pelo usuário nas notificações
        //if (sbn?.packageName.equals("com.google.android.music")) {
        if (sbn?.packageName.equals("com.google.android.apps.youtube.music")) {
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(baseContext)

            val intent = Intent("com.kanedasoftware.masterscrobbler.NOTIFICATION_LISTENER")
            val artist = sbn?.notification?.extras?.getString(Notification.EXTRA_TEXT)
            val track = sbn?.notification?.extras?.getString(Notification.EXTRA_TITLE)
            val postTime = sbn?.postTime

            //Se a notificação tiver o mesmo título, mesmo texto e foi recebida menos de 3 segundos depois da anterior, ignora a nova notificação
            if(!(defaultSharedPreferences.getString("artist", "") == artist &&
                    defaultSharedPreferences.getString("track", "") == track &&
                    (postTime?.minus(defaultSharedPreferences.getLong("postTime", 0))!! < 3000))){
                intent.putExtra("artist", artist)
                intent.putExtra("track", track)
                intent.putExtra("postTime", postTime)

                val editor = defaultSharedPreferences.edit()
                editor.putString("artist", artist)
                editor.putString("track", track)
                editor.putLong("postTime", postTime!!)
                editor.apply()

                sendBroadcast(intent)
            } else {
                Utils.logDebug("Notificação ignorada")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}