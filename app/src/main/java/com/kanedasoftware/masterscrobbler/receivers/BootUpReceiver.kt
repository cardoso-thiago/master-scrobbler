package com.kanedasoftware.masterscrobbler.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.kanedasoftware.masterscrobbler.utils.ImageUtils
import com.kanedasoftware.masterscrobbler.utils.NotificationUtils
import com.kanedasoftware.masterscrobbler.utils.Utils
import org.koin.android.ext.android.inject
import org.koin.core.KoinComponent
import org.koin.core.inject

class BootUpReceiver : BroadcastReceiver(), KoinComponent {

    private val utils: Utils by inject()
    private val notificationUtils: NotificationUtils by inject()

    override fun onReceive(context: Context, intent: Intent?) {
        notificationUtils.createQuietNotificationChannel(context)
        notificationUtils.createNotificationChannel(context)

        val notificationAccessValidation = notificationUtils.verifyNotificationAccess()
        //Não inicia o serviço se não tiver acesso às notificações
        if (notificationAccessValidation) {
            //Não inicia o serviço se não tiver feito o login
            if (utils.isValidSessionKey()) {
                if (intent?.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                    utils.startMediaService()
                }
            }
        }
    }
}