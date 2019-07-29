package com.kanedasoftware.masterscrobbler.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kanedasoftware.masterscrobbler.services.MediaService
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.Utils

class BootUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val notificationAccessValidation = Utils.verifyNotificationAccess(context)
        //Não inicia p serviço se não tiver acesso às notificações
        if (!notificationAccessValidation) {
            //Não inicia o serviço se não tiver feito o login
            if (!Utils.isValidSessionKey(context)) {
                if (intent?.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                    val i = Intent(context, MediaService::class.java)
                    i.action = Constants.START_SERVICE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(i)
                    } else {
                        context.startService(i)
                    }
                }
            }
        }
    }
}