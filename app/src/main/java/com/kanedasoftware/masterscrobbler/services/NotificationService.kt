package com.kanedasoftware.masterscrobbler.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import com.kanedasoftware.masterscrobbler.receivers.NotificationServiceReceiver
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.Utils

class NotificationService : NotificationListenerService(), MediaSessionManager.OnActiveSessionsChangedListener {

    private var mediaController: MediaController? = null

    private var mediaControllerCallback: MediaController.Callback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && !intent.action.isNullOrBlank()) {
            Utils.logInfo("Tentando reconectar. Action: ".plus(intent.action))
            tryReconnectService()//switch on/off component and rebind
        }
        return Service.START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        createNotification()
        createCallback()

        val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, this.javaClass)
        mediaSessionManager.addOnActiveSessionsChangedListener(this, componentName)

        val filter = IntentFilter("com.kanedasoftware.masterscrobbler.NOTIFICATION_LISTENER")
        registerReceiver(NotificationServiceReceiver(), filter)
    }

    override fun onDestroy() {
        unregisterReceiver(NotificationServiceReceiver())
        unregisterCallback(mediaController)
        super.onDestroy()
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL,
                    "Master Scrobbler",
                    NotificationManager.IMPORTANCE_LOW
            )
            notificationChannel.description = "Foreground Service"
            notificationChannel.setSound(null, null)
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.WHITE
            notificationChannel.enableVibration(false)
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    private fun createNotification() {
        val notification = Utils.buildNotification(applicationContext, "Master Scrobbler", "Service Running")
        startForeground(Constants.NOTIFICATION_ID, notification)
    }

    private fun createCallback() {
        mediaControllerCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                when (state?.state) {
                    PlaybackState.STATE_PLAYING -> {
                        Utils.logInfo("STATE_PLAYING")
                    }
                    PlaybackState.STATE_STOPPED -> {
                        Utils.logInfo("STATE_STOPPED")
                    }
                    PlaybackState.STATE_PAUSED -> {
                        Utils.logInfo("STATE_PAUSED")
                    }
                    PlaybackState.STATE_SKIPPING_TO_NEXT -> {
                        Utils.logInfo("STATE_SKIPPING_TO_NEXT")
                    }
                    PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> {
                        Utils.logInfo("STATE_SKIPPING_TO_PREVIOUS")
                    }
                }
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                metadata?.let { mediaMetadata ->
                    val intent = Intent("com.kanedasoftware.masterscrobbler.NOTIFICATION_LISTENER")
                    val artist = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    val track = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    val album = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
                    val duration = mediaMetadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                    val postTime = System.currentTimeMillis()

                    intent.putExtra("artist", artist)
                    intent.putExtra("track", track)
                    intent.putExtra("album", album)
                    intent.putExtra("postTime", postTime)
                    intent.putExtra("duration", duration)

                    Utils.logInfo(artist)
                    Utils.logInfo(track)
                    Utils.logInfo(album)
                    Utils.logInfo(postTime.toString())
                    Utils.logInfo(duration.toString())

                    sendBroadcast(intent)
                }
            }
        }
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        val activeMediaController = controllers?.firstOrNull()
        //TODO pegar todos os apps de midia das configurações e verificar aqui pra interceptar apenas essas notificações
        //TODO 2 verificar possibilidade de só receber notificações de apps selecionados (IntentFilter?)
        if (activeMediaController?.packageName.equals("com.google.android.music") || activeMediaController?.packageName.equals("com.google.android.apps.youtube.music")) {
            activeMediaController?.let { mediaController ->
                registerCallback(mediaController)
            }
        }
    }

    private fun registerCallback(mediaController: MediaController) {
        unregisterCallback(this.mediaController)
        Utils.logInfo("Registering callback for ${mediaController.packageName}")
        this.mediaController = mediaController
        mediaController.registerCallback(mediaControllerCallback)
    }

    private fun unregisterCallback(mediaController: MediaController?) {
        Utils.logInfo("Unregistering callback for ${mediaController?.packageName}")
        mediaControllerCallback?.let { mediaControllerCallback ->
            mediaController?.unregisterCallback(mediaControllerCallback)
        }
    }
}