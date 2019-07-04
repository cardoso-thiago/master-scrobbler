package com.kanedasoftware.masterscrobbler.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.CountDownTimer
import android.preference.PreferenceManager
import android.service.notification.NotificationListenerService
import com.kanedasoftware.masterscrobbler.beans.ScrobbleBean
import com.kanedasoftware.masterscrobbler.db.ScrobbleDb
import com.kanedasoftware.masterscrobbler.network.ConnectionLiveData
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.Utils
import com.kanedasoftware.masterscrobbler.ws.LastFmInitializer
import org.jetbrains.anko.doAsync
import java.util.concurrent.TimeUnit

class MediaService : NotificationListenerService(), MediaSessionManager.OnActiveSessionsChangedListener {

    private var mediaController: MediaController? = null
    private var mediaControllerCallback: MediaController.Callback? = null
    private var connectionLiveData: ConnectionLiveData? = null
    private var playbackState = 0
    private var metadataArtist = ""
    private var metadataTrack = ""
    private var playtimeHolder = 0L
    private var paused = false
    private var playtime = 0L
    private var preferences: SharedPreferences? = null
    private var toScrobble: ScrobbleBean? = null
    private var isOnline = false
    private var finalDuration = 0L
    private var totalSeconds = 600L

    private var timer: CountDownTimer = object : CountDownTimer(totalSeconds * 1000, 1 * 1000) {
        override fun onFinish() {
            //Faz scrobble do que estiver pendente
            scrobblePendingAsync(playtime.plus(playtimeHolder), finalDuration)
            playtime = 0L
            playtimeHolder = 0L
        }

        override fun onTick(millisUntilFinished: Long) {
            playtime = totalSeconds * 1000 - millisUntilFinished
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && !intent.action.isNullOrBlank()) {
            Utils.logWarning("Tentando reconectar. Action: ".plus(intent.action))
            tryReconnectService()//switch on/off component and rebind
        }
        return Service.START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        this.preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        observeConnection()
        createNotificationChannel()
        createNotification()
        createCallback()

        val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, this.javaClass)
        mediaSessionManager.addOnActiveSessionsChangedListener(this, componentName)
        //Ativa o listener para o caso de já ter uma sessão ativa
        onActiveSessionsChanged(mediaSessionManager.getActiveSessions(componentName))
    }

    override fun onDestroy() {
        unregisterCallback(mediaController)
        super.onDestroy()
    }

    private fun tryReconnectService() {
        toggleNotificationListenerService()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val componentName = ComponentName(applicationContext, MediaService::class.java)
            requestRebind(componentName)
        }
    }

    private fun toggleNotificationListenerService() {
        val pm = packageManager
        pm.setComponentEnabledSetting(ComponentName(this, MediaService::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(ComponentName(this, MediaService::class.java),
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
                playbackState = state?.state!!
                Utils.logDebug(playbackState.toString())
                when (state.state) {
                    PlaybackState.STATE_PAUSED -> {
                        pauseTimer()
                    }
                    PlaybackState.STATE_PLAYING -> {
                        resumeTimer()
                    }
                }
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                handleMetadataChange(metadata)
            }
        }
    }

    private fun handleMetadataChange(metadata: MediaMetadata?) {
        metadata?.let { mediaMetadata ->
            val artist = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val track = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            val duration = mediaMetadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

            if (playbackState == PlaybackState.STATE_PLAYING) {
                if ((metadataArtist != artist || metadataTrack != track) || ((playtime + playtimeHolder) > (duration / 2))) {
                    timer.onFinish()
                    timer.start()

                    Utils.logDebug("Vai iniciar a validação com $artist - $track  - PlaybackState: $playbackState")
                    finalDuration = duration
                    metadataArtist = artist
                    metadataTrack = track

                    val postTime = System.currentTimeMillis()
                    //Cria novo bean com validação com base na informação atual
                    val scrobbleBean = ScrobbleBean(artist, track, postTime, duration)
                    //Se não estiver conectado já adiciona para realizar o scrobble
                    if (isOnline) {
                        doAsync {
                            val scrobbleValidationBean = allValidations(scrobbleBean)
                            if (scrobbleValidationBean != null) {
                                Utils.updateNotification(applicationContext, "Scrobbling", "${scrobbleBean.artist} - ${scrobbleBean.track}")
                                Utils.log("Vai atualizar o NowPlaying e gravar a música para o scrobble")
                                toScrobble = scrobbleValidationBean
                                updateNowPlaying(scrobbleValidationBean)
                            } else {
                                toScrobble = scrobbleBean
                            }
                        }
                    } else {
                        toScrobble = scrobbleBean
                    }
                } else {
                    Utils.logDebug("Mesma música tocando, será ignorada a mudança de metadata, mas será atualizada a duração da música.")
                    if (finalDuration != duration) {
                        finalDuration = duration
                        Utils.log("Duração Atualizada - Em milisegundos: $duration - " +
                                "Em segundos: ${TimeUnit.MILLISECONDS.toSeconds(duration)} - " +
                                "Em minutos: ${TimeUnit.MILLISECONDS.toMinutes(duration)}")
                    }
                }
            }
        }
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        Utils.logDebug("Active Sessions Changed")
        if (controllers != null) {
            //Se não tem controles ativos envia um broadcast para o receiver sem artista, para fazer scrobble de alguma possível música pendente
            if (controllers.size == 0) {
                metadataArtist = ""
                metadataTrack = ""

                //Para de contar o tempo da música, zera o contador e faz algum possível scrobble pendente
                timer.onFinish()

                //Atualiza a notificação para o padrão do serviço
                createNotification()
            }
            for (controller in controllers) {
                Utils.logDebug(controller.packageName)
            }
        }

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
        Utils.logDebug("Registering callback for ${mediaController.packageName}")

        //Atualiza o estado atual do playback
        playbackState = mediaController.playbackState.state
        if (playbackState == PlaybackState.STATE_PLAYING) {
            resumeTimer()
        }
        handleMetadataChange(mediaController.metadata)

        this.mediaController = mediaController
        mediaController.registerCallback(this.mediaControllerCallback)
    }

    private fun unregisterCallback(mediaController: MediaController?) {
        Utils.logDebug("Unregistering callback for ${mediaController?.packageName}")
        mediaControllerCallback?.let { mediaControllerCallback ->
            mediaController?.unregisterCallback(mediaControllerCallback)
        }
    }

    private fun allValidations(scrobbleBean: ScrobbleBean): ScrobbleBean? {
        var scrobbleBean = validateTrack(scrobbleBean, false)
        if (scrobbleBean != null) {
            if (scrobbleBean.mbid.isBlank() && scrobbleBean.image.isBlank()) {
                Utils.log("MBID e imagem não existente, assume que a música não existe, vai tentar validar só pelo nome da música")
                scrobbleBean = validateTrack(scrobbleBean, true)
            }
            if (scrobbleBean != null) {
                Utils.log("Música encontrada, valor validado: ${scrobbleBean.artist} - ${scrobbleBean.track}")
                if (scrobbleBean.duration == 0L) {
                    Utils.log("Não possui a duração no metadata, vai tentar buscar no Last.fm")
                    scrobbleBean.duration = getFullTrackInfo(scrobbleBean)
                }
            }
        }
        if (scrobbleBean != null) {
            scrobbleBean.validated = true
        }
        return scrobbleBean
    }

    private fun validateTrack(scrobbleBean: ScrobbleBean, onlyTrack: Boolean): ScrobbleBean? {
        var response = if (onlyTrack) {
            LastFmInitializer().lastFmService().validateTrack(scrobbleBean.track, Constants.API_KEY).execute()
        } else {
            LastFmInitializer().lastFmService().validateTrackAndArtist(scrobbleBean.artist, scrobbleBean.track, Constants.API_KEY).execute()
        }

        if(response.isSuccessful){
            val trackList = response.body()?.results?.trackmatches?.track
            val listSize = trackList?.size
            if (listSize != null && listSize > 0) {
                scrobbleBean.artist = trackList[0].artist
                scrobbleBean.track = trackList[0].name
                scrobbleBean.image = trackList[0].image[0].text
                scrobbleBean.mbid = trackList[0].mbid
                return scrobbleBean
            }
        } else {
            Utils.logError("Erro na validação da música: ${response.code()}")
        }

        return null
    }

    private fun getFullTrackInfo(scrobbleBean: ScrobbleBean): Long {
        val response = LastFmInitializer().lastFmService().fullTrackInfo(scrobbleBean.mbid, Constants.API_KEY).execute()
        if(response.isSuccessful) {
            val duration = response.body()?.track?.duration
            return if (!duration.isNullOrBlank()) {
                duration?.toLong()!!
            } else {
                Utils.log("Não encontrou a duração no Metadata nem no Last.fm, assume um minuto de música.")
                60000
            }
        } else {
            Utils.logError("Erro na obtenção das informações completas da música: ${response.code()}. Vai retornar um minuto de música.")
            return 60000
        }
    }

    private fun updateNowPlaying(scrobbleBean: ScrobbleBean) {
        val sessionKey = preferences!!.getString("sessionKey", "")
        val params = mutableMapOf("track" to scrobbleBean.track, "artist" to scrobbleBean.artist, "sk" to sessionKey)
        val sig = Utils.getSig(params, Constants.API_UPDATE_NOW_PLAYING)
        LastFmInitializer().lastFmService().updateNowPlaying(scrobbleBean.artist, scrobbleBean.track, Constants.API_KEY, sig, sessionKey!!).execute()
    }

    private fun scrobblePendingAsync(playtimeHolder: Long, finalDuration: Long) {
        var scrobbleBean = toScrobble
        if (scrobbleBean != null) {
            scrobbleBean.playtime = scrobbleBean.playtime + playtimeHolder
            scrobbleBean.duration = finalDuration
            doAsync {
                scrobble(scrobbleBean)
            }
        }
    }

    private fun scrobble(scrobbleBean: ScrobbleBean) {
        if (scrobbleBean.duration <= 30000) {
            Utils.log("Música muito curta, não será realizado o scrobble: ".plus(scrobbleBean.artist).plus(" - ").plus(scrobbleBean.track))
        } else {
            Utils.log("Execução - Em milisegundos: ${scrobbleBean.playtime} - " +
                    "Em segundos: ${TimeUnit.MILLISECONDS.toSeconds(scrobbleBean.playtime)} - " +
                    "Em minutos: ${TimeUnit.MILLISECONDS.toMinutes(scrobbleBean.playtime)}")
            if (scrobbleBean.playtime > (scrobbleBean.duration / 2)) {
                if (isOnline && scrobbleBean.validated) {
                    val sessionKey = preferences?.getString("sessionKey", "")
                    val timestamp = (scrobbleBean.postTime.div(1000)).toString()
                    val paramsScrobble = mutableMapOf("track" to scrobbleBean.track, "artist" to scrobbleBean.artist, "sk" to sessionKey!!, "timestamp" to timestamp)
                    val sig = Utils.getSig(paramsScrobble, Constants.API_TRACK_SCROBBLE)
                    val response = LastFmInitializer().lastFmService().scrobble(scrobbleBean.artist, scrobbleBean.track, Constants.API_KEY, sig, sessionKey, timestamp).execute()
                    if(response.isSuccessful){
                        Utils.log("Scrobbeled: ${scrobbleBean.artist} - ${scrobbleBean.track}")
                    } else {
                        Utils.logError("Erro ao fazer o scrobble, adiciona ao cache para tentar novamente depois: ${response.code()}")
                        cacheScrobble(scrobbleBean)
                    }
                } else {
                    cacheScrobble(scrobbleBean)
                }
            } else {
                Utils.log("Tempo de execução não alcançou ao menos metade da música, não será feito o scrobble: ${scrobbleBean.artist} - ${scrobbleBean.track}")
            }
        }
    }

    private fun cacheScrobble(scrobbleBean: ScrobbleBean) {
        Utils.log("Caching scrobble: ${scrobbleBean.artist} - ${scrobbleBean.track}")
        ScrobbleDb.getInstance(applicationContext).scrobbleDao().add(scrobbleBean)
    }

    private fun pauseTimer() {
        timer.cancel()
        paused = true
    }

    private fun resumeTimer() {
        if (paused) {
            playtimeHolder += playtime
            paused = false
            timer.start()
        }
    }

    private fun observeConnection() {
        connectionLiveData = ConnectionLiveData(applicationContext)
        connectionLiveData.let {
            it?.observeForever { isConnected ->
                isConnected?.let {
                    Utils.log("Connection state: $isConnected")
                    isOnline = isConnected
                    if (isConnected) {
                        //Fazer scrobbles pendentes
                        validateAndScrobblePending()
                    }
                }
            }
        }
    }

    private fun validateAndScrobblePending() {
        Utils.log("Scrobbling pending")
        val scrobbleDao = ScrobbleDb.getInstance(applicationContext).scrobbleDao()
        val scrobbles = scrobbleDao.getAll()
        for (scrobbleBean in scrobbles) {
            if (scrobbleBean.validated) {
                Utils.log("Scrobble já validado, só registra no Last.FM: ${scrobbleBean.artist} - ${scrobbleBean.track}")
                doAsync {
                    scrobble(scrobbleBean)
                }
            } else {
                doAsync {
                    Utils.log("Vai fazer a validação e em seguida o scrobble: ${scrobbleBean.artist} - ${scrobbleBean.track}")
                    val scrobbleBean = allValidations(scrobbleBean)
                    if (scrobbleBean != null) {
                        scrobble(scrobbleBean)
                    }
                }
            }
            //TODO verificar se foi feito com sucesso ou não
            scrobbleDao.delete(scrobbleBean)
        }
    }
}