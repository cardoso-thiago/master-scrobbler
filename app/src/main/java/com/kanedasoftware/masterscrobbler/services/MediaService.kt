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
    private lateinit var mediaControllerCallback: MediaController.Callback
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
                            if (!scrobbleBean.validationError) {
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

    private fun registerCallback(newMediaController: MediaController) {
        unregisterCallback(this.mediaController)
        Utils.logDebug("Registering callback for ${newMediaController.packageName}")

        if (newMediaController.playbackState != null) {
            //Atualiza o estado atual do playback
            playbackState = newMediaController.playbackState!!.state
            if (playbackState == PlaybackState.STATE_PLAYING) {
                resumeTimer()
            }
        }
        handleMetadataChange(newMediaController.metadata)

        mediaController = newMediaController
        newMediaController.registerCallback(mediaControllerCallback)
    }

    private fun unregisterCallback(mediaController: MediaController?) {
        Utils.logDebug("Unregistering callback for ${mediaController?.packageName}")
        mediaControllerCallback.let { mediaControllerCallback ->
            mediaController?.unregisterCallback(mediaControllerCallback)
        }
    }

    private fun allValidations(scrobbleBean: ScrobbleBean): ScrobbleBean {
        var scrobbleBeanValidationBean = validateTrack(scrobbleBean, false)
        if (!scrobbleBeanValidationBean.validated) {
            scrobbleBeanValidationBean = validateTrack(scrobbleBean, true)
            if (scrobbleBeanValidationBean.validated) {
                if (scrobbleBeanValidationBean.duration == 0L) {
                    Utils.log("Não possui a duração no metadata, vai tentar buscar no Last.fm")
                    scrobbleBeanValidationBean.duration = getFullTrackInfo(scrobbleBeanValidationBean)
                }
            } else {
                //Se nao encontrar nenhuma correspondência, faz o scrobble com os valores originais
                Utils.logError("Não encontrou nenhuma correspondência para ${scrobbleBean.artist} - ${scrobbleBean.track}, vai usar os valores originais da notificação")
                return scrobbleBean
            }
        }
        Utils.log("Música encontrada, valor validado: ${scrobbleBeanValidationBean.artist} - ${scrobbleBeanValidationBean.track}")
        return scrobbleBeanValidationBean
    }

    private fun validateTrack(scrobbleBean: ScrobbleBean, onlyTrack: Boolean): ScrobbleBean {
        val response = if (onlyTrack) {
            LastFmInitializer().lastFmService().validateTrack(scrobbleBean.track, Constants.API_KEY).execute()
        } else {
            LastFmInitializer().lastFmService().validateTrackAndArtist(scrobbleBean.artist, scrobbleBean.track, Constants.API_KEY).execute()
        }

        if (response.isSuccessful) {
            val trackList = response.body()?.results?.trackmatches?.track
            val listSize = trackList?.size
            if (listSize != null && listSize > 0) {
                for (track in trackList) {
                    if (onlyTrack) {
                        if (!track.mbid.isBlank() || !track.image[0].text.isBlank()) {
                            scrobbleBean.artist = track.artist
                            scrobbleBean.track = track.name
                            scrobbleBean.image = track.image[0].text
                            scrobbleBean.mbid = track.mbid
                            scrobbleBean.validated = true
                            return scrobbleBean
                        }
                    } else {
                        if (!track.mbid.isBlank() || !track.image[0].text.isBlank()) {
                            scrobbleBean.artist = track.artist
                            scrobbleBean.track = track.name
                            scrobbleBean.image = track.image[0].text
                            scrobbleBean.mbid = track.mbid
                            scrobbleBean.validated = true
                            return scrobbleBean
                        }
                    }
                }
            } else {
                Utils.log("Não encontrou nenhum resultado na busca - Somente pela música: $onlyTrack")
                scrobbleBean.validated = false
                return scrobbleBean
            }
        } else {
            Utils.logError("Erro na validação da música: ${response.code()}")
            scrobbleBean.validated = false
            scrobbleBean.validationError = true
            return scrobbleBean
        }
        scrobbleBean.validated = false
        return scrobbleBean
    }

    private fun getFullTrackInfo(scrobbleBean: ScrobbleBean): Long {
        val response = LastFmInitializer().lastFmService().fullTrackInfo(scrobbleBean.mbid, Constants.API_KEY).execute()
        return if (response.isSuccessful) {
            val duration = response.body()?.track?.duration
            if (!duration.isNullOrBlank()) {
                duration?.toLong()!!
            } else {
                Utils.log("Não encontrou a duração no Metadata nem no Last.fm, assume um minuto de música.")
                60000
            }
        } else {
            Utils.logError("Erro na obtenção das informações completas da música: ${response.code()}. Vai retornar um minuto de música.")
            60000
        }
    }

    private fun updateNowPlaying(scrobbleBean: ScrobbleBean) {
        val sessionKey = preferences!!.getString("sessionKey", "")
        val params = mutableMapOf("track" to scrobbleBean.track, "artist" to scrobbleBean.artist, "sk" to sessionKey)
        val sig = Utils.getSig(params, Constants.API_UPDATE_NOW_PLAYING)
        LastFmInitializer().lastFmService().updateNowPlaying(scrobbleBean.artist, scrobbleBean.track, Constants.API_KEY, sig, sessionKey!!).execute()
    }

    private fun scrobblePendingAsync(playtimeHolder: Long, finalDuration: Long) {
        val scrobbleBean = toScrobble
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
                    if (response.isSuccessful) {
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
                    scrobble(allValidations(scrobbleBean))
                }
            }
            scrobbleDao.delete(scrobbleBean)
        }
    }
}