package com.kanedasoftware.masterscrobbler.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kanedasoftware.masterscrobbler.services.NotificationService

class BootUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            val i = Intent(context, NotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context?.startForegroundService(i)
            } else {
                context?.startService(i)
            }
        }
    }
}