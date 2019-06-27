package com.kanedasoftware.masterscrobbler.services

import android.app.Notification
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.preference.PreferenceManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.v4.app.NotificationCompat
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.receivers.NotificationServiceReceiver
import com.kanedasoftware.masterscrobbler.utils.Utils


class NotificationService : NotificationListenerService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && !intent.action.isNullOrBlank()) {
            Utils.logDebug("Tentando reconectar. Action: ".plus(intent.action))
            tryReconnectService()//switch on/off component and rebind
        }
        return Service.START_STICKY
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        //TODO pegar todos os apps de midia das configurações e verificar aqui pra interceptar apenas essas notificações
        //TODO 2 verificar possibilidade de só receber notificações de apps selecionados (IntentFilter?)
        //Obtém apenas as informações dos apps selecionados pelo usuário nas notificações
        if (sbn?.packageName.equals("com.google.android.music") || sbn?.packageName.equals("com.google.android.apps.youtube.music")) {
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(baseContext)

            val intent = Intent("com.kanedasoftware.masterscrobbler.NOTIFICATION_LISTENER")
            val artist = sbn?.notification?.extras?.getString(Notification.EXTRA_TEXT)
            val track = sbn?.notification?.extras?.getString(Notification.EXTRA_TITLE)
            var postTime = sbn?.postTime

            if(postTime == null) {
                postTime = 0L
            }

            //Se a notificação tiver o mesmo título, mesmo texto e foi recebida menos de 3 segundos depois da anterior, ignora a nova notificação
            if(!(defaultSharedPreferences.getString("artist", "") == artist &&
                    defaultSharedPreferences.getString("track", "") == track &&
                    (postTime.minus(defaultSharedPreferences.getLong("postTime", 0)) < 3000))){
                intent.putExtra("artist", artist)
                intent.putExtra("track", track)
                intent.putExtra("postTime", postTime)

                val editor = defaultSharedPreferences.edit()
                editor.putString("artist", artist)
                editor.putString("track", track)
                editor.putLong("postTime", postTime)
                editor.apply()

                sendBroadcast(intent)
            } else {
                //TODO remover depois
                Utils.logDebug("Notificação ignorada, igual à notificação anterior")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotification()
        val filter = IntentFilter("com.kanedasoftware.masterscrobbler.NOTIFICATION_LISTENER")
        registerReceiver(NotificationServiceReceiver(), filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(NotificationServiceReceiver())
    }

    private fun tryReconnectService() {
        toggleNotificationListenerService()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val componentName = ComponentName(applicationContext, NotificationService::class.java)
            requestRebind(componentName)
        }
    }

    private fun toggleNotificationListenerService() {
        val pm = packageManager
        pm.setComponentEnabledSetting(ComponentName(this, NotificationService::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(ComponentName(this, NotificationService::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }

    private fun createNotification() {
        val notification = NotificationCompat.Builder(this, "masterScrobblerNotificationService")
                .setContentTitle("Master Scrobbbler")
                .setContentText("Foreground Service")
                .setSmallIcon(R.drawable.navigation_empty_icon)
                .build()

        startForeground(1, notification)
    }
}