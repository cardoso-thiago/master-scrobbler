package com.kanedasoftware.masterscrobbler.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.kanedasoftware.masterscrobbler.utils.Utils
import android.support.v4.app.NotificationCompat
import com.kanedasoftware.masterscrobbler.R


class ForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Utils.createNotificationChannel(applicationContext)
        return START_STICKY
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