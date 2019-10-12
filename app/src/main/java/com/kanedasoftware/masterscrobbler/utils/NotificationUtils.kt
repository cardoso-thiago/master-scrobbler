package com.kanedasoftware.masterscrobbler.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.main.MainActivity
import com.kanedasoftware.masterscrobbler.main.SettingsActivity
import org.koin.core.KoinComponent
import org.koin.core.inject
import kotlin.system.exitProcess

class NotificationUtils constructor(appContext: Context):KoinComponent{

    private val utils: Utils by inject()
    private val context = appContext

    fun verifyNotificationAccess() = NotificationManagerCompat.getEnabledListenerPackages(context).contains("com.kanedasoftware.masterscrobbler")

    fun changeNotificationAccess(context: Context) {
        if (!verifyNotificationAccess()) {
            AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.notification_access))
                    .setMessage(context.getString(R.string.question_notification_access))
                    .setPositiveButton(context.getString(R.string.option_yes)) { _, _ ->
                        context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    }
                    .setNegativeButton(context.getString(R.string.option_exit)) { _, _ ->
                        android.os.Process.killProcess(android.os.Process.myPid())
                        exitProcess(1)
                    }
                    .show()
        }
    }

    fun updateNotification(title: String, text: String): Notification? {
        val notification = buildNotification(title, text)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIFICATION_ID, notification)
        return notification
    }

    fun updateNotification(title: String, text: String, image: Bitmap?): Notification? {
        val notification = buildNotification(title, text, image)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIFICATION_ID, notification)
        return notification
    }

    fun buildNotification(title: String, text: String): Notification? {
        return buildNotification(title, text, null)
    }

    private fun buildNotification(title: String, text: String, image: Bitmap?): Notification? {
        utils.log("Construindo notificação: $title - $text")
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

        val notif = NotificationCompat.Builder(context, Constants.QUIET_NOTIFICATION_CHANNEL)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_cassette)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0L))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        if (image != null) {
            val mediaSession = MediaSessionCompat(context, Constants.LOG_TAG)
            val builder = MediaMetadataCompat.Builder()
            builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1)
            mediaSession.setMetadata(builder.build())

            notif.setLargeIcon(image)
            notif.setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken))
                    .setColorized(true)
        }
        return notif.build()
    }

    fun sendNewPlayerNotification(player: String) {

        val intent = Intent(context, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL)
                .setContentTitle(context.getString(R.string.new_player_identified))
                .setContentText(context.getString(R.string.player_identified, player))
                .setSmallIcon(R.drawable.ic_stat_cassette)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.player_identified, player)))
                .setAutoCancel(true)
                .build()
        notificationManager.notify(Constants.NOTIFICATION_NEW_PLAYER_ID, notif)
    }

    fun sendNoPlayerNotification() {
        val intent = Intent(context, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL)
                .setContentTitle(context.getString(R.string.no_player_identified))
                .setContentText(context.getString(R.string.no_player_identified_text))
                .setSmallIcon(R.drawable.ic_stat_cassette)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.no_player_identified_text)))
                .setAutoCancel(true)
                .build()
        notificationManager.notify(Constants.NOTIFICATION_NO_PLAYER_ID, notif)
    }

    fun createQuietNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                    Constants.QUIET_NOTIFICATION_CHANNEL,
                    context.getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
            )
            notificationChannel.description = context.getString(R.string.foreground_service)
            notificationChannel.setSound(null, null)
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.WHITE
            notificationChannel.enableVibration(false)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAtt = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            val notificationChannel = NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL,
                    context.getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationChannel.description = context.getString(R.string.master_scrobbler_notifications)
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.WHITE
            notificationChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audioAtt)
            notificationChannel.enableVibration(true)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    fun cancelNoPlayerNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(Constants.NOTIFICATION_NO_PLAYER_ID)
    }

}