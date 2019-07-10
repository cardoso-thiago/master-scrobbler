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

class MediaService : NotificationListenerService(),
        MediaSessionManager.OnActiveSessionsChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

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
            Utils.logDebug("Finish timer. Duration: ${playtime + playtimeHolder}", applicationContext)
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
            Utils.logWarning("Tentando reconectar. Action: ".plus(intent.action), applicationContext)
            tryReconnectService()//switch on/off component and rebind
        }
        return Service.START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
        this.preferences = defaultSharedPreferences

        observeConnection()
        createNotificationChannel()
        createNotification()
        createCallback()
        createMediaSessionManager()
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
                Utils.logDebug(playbackState.toString(), applicationContext)
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Utils.log("Preference changed: $key", applicationContext)
        if(key == "apps_to_scrobble"){
            createMediaSessionManager()
        }
    }

    private fun createMediaSessionManager() {
        //Ativa o listener para o caso de já ter uma sessão ativa
        val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, this.javaClass)
        mediaSessionManager.removeOnActiveSessionsChangedListener(this)
        mediaSessionManager.addOnActiveSessionsChangedListener(this, componentName)
        onActiveSessionsChanged(mediaSessionManager.getActiveSessions(componentName))
    }

    private fun handleMetadataChange(metadata: MediaMetadata?) {
        metadata?.let { mediaMetadata ->
            val artist = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val track = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            val duration = mediaMetadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

            if (playbackState == PlaybackState.STATE_PLAYING) {
                if ((metadataArtist != artist || metadataTrack != track) || ((playtime + playtimeHolder) > (duration / 2))) {
                    timer.onFinish()
                    Utils.log("Duração até o momento $playtimeHolder", applicationContext)
                    timer.start()

                    Utils.logDebug("Vai iniciar a validação com $artist - $track  - PlaybackState: $playbackState", applicationContext)
                    finalDuration = duration
                    metadataArtist = artist
                    metadataTrack = track

                    //Valida qualquer música que estiver pendente
                    validateAndScrobblePending()

                    val postTime = System.currentTimeMillis()
                    //Cria novo bean com validação com base na informação atual
                    val scrobbleBean = ScrobbleBean(artist, track, postTime, duration)
                    //Se não estiver conectado já adiciona para realizar o scrobble
                    if (isOnline) {
                        doAsync {
                            val scrobbleValidationBean = allValidations(scrobbleBean)
                            if (scrobbleValidationBean == null) {
                                Utils.log("Não conseguiu validar a música, não será realizado o scrobble.", applicationContext)
                            } else {
                                if (!scrobbleBean.validationError) {
                                    Utils.updateNotification(applicationContext, "Scrobbling", "${scrobbleBean.artist} - ${scrobbleBean.track}")
                                    Utils.log("Vai atualizar o NowPlaying e gravar a música para o scrobble (toScrobble)", applicationContext)
                                    toScrobble = scrobbleValidationBean
                                    updateNowPlaying(scrobbleValidationBean)
                                } else {
                                    Utils.log("Erro de validação, vai armazenar a música  para o próximo scrobble (toScrobble)", applicationContext)
                                    toScrobble = scrobbleBean
                                }
                            }
                        }
                    } else {
                        Utils.log("Não está online, vai armazenar a música para o próximo scrobble (toScrobble)", applicationContext)
                        toScrobble = scrobbleBean
                    }
                } else {
                    Utils.logDebug("Mesma música tocando, será ignorada a mudança de metadata, mas será atualizada a duração da música.", applicationContext)
                    if (finalDuration != duration) {
                        finalDuration = duration
                        Utils.log("Duração Atualizada - Em milisegundos: $duration - " +
                                "Em segundos: ${TimeUnit.MILLISECONDS.toSeconds(duration)} - " +
                                "Em minutos: ${TimeUnit.MILLISECONDS.toMinutes(duration)}", applicationContext)
                    }
                }
            }
        }
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        Utils.logDebug("Active Sessions Changed", applicationContext)
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
                Utils.logDebug(controller.packageName, applicationContext)
            }
        }

        val activeMediaController = controllers?.firstOrNull()
        val entries = preferences?.getStringSet("apps_to_scrobble", HashSet<String>())
        if (entries != null) {
            if(entries.size > 0) {
                if (entries.contains(activeMediaController?.packageName)) {
                    activeMediaController?.let { mediaController ->
                        registerCallback(mediaController)
                    }
                } else {
                    Utils.log("Nenhum app ativo para scrobble.", applicationContext)
                }
            } else {
                Utils.log("Nenhum app selecionado para scrobble.", applicationContext)
            }
        }
    }

    private fun registerCallback(newMediaController: MediaController) {
        unregisterCallback(this.mediaController)
        Utils.logDebug("Registering callback for ${newMediaController.packageName}", applicationContext)

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
        Utils.logDebug("Unregistering callback for ${mediaController?.packageName}", applicationContext)
        mediaControllerCallback.let { mediaControllerCallback ->
            mediaController?.unregisterCallback(mediaControllerCallback)
        }
    }

    private fun allValidations(scrobbleBean: ScrobbleBean): ScrobbleBean? {
        val scrobbleBeanValidationBean = validateTrack(scrobbleBean)
        if (scrobbleBeanValidationBean != null) {
            if (scrobbleBeanValidationBean.duration == 0L) {
                Utils.log("Não conseguiu obter a duração no metadata, vai tentar obter no Last.fm", applicationContext)
                scrobbleBeanValidationBean.duration = getFullTrackInfo(scrobbleBeanValidationBean)
            }
        }
        return scrobbleBeanValidationBean
    }

    private fun validateTrack(scrobbleBean: ScrobbleBean): ScrobbleBean? {
        val responseArtist = LastFmInitializer().lastFmService().validateArtist(scrobbleBean.artist, Constants.API_KEY).execute()
        if (responseArtist.isSuccessful) {
            val artistList = responseArtist.body()?.results?.artistmatches?.artist
            if (artistList != null) {
                for (artist in artistList) {
                    if (!artist.mbid.isBlank() || !artist.image[0].text.isBlank()) {
                        if (scrobbleBean.artist == artist.name) {
                            Utils.log("Encontrou o artista ${scrobbleBean.artist} no Last.fm, assume que a música está correta.", applicationContext)
                            scrobbleBean.validated = true
                            return scrobbleBean
                        }
                    }
                }
            }
        } else {
            Utils.logError("Erro na validação da música: ${responseArtist.code()}", applicationContext)
            scrobbleBean.validated = false
            scrobbleBean.validationError = true
            return scrobbleBean
        }

        val responseTrackArtist = LastFmInitializer().lastFmService().validateTrackAndArtist(scrobbleBean.artist, scrobbleBean.track, Constants.API_KEY).execute()
        if (responseTrackArtist.isSuccessful) {
            val artistTrackList = responseTrackArtist.body()?.results?.trackmatches?.track
            if (artistTrackList != null) {
                for (track in artistTrackList) {
                    if (!track.mbid.isBlank() || !track.image[0].text.isBlank()) {
                        if (track.artist != "[unknown]" && track.name != "[unknown]") {
                            scrobbleBean.artist = track.artist
                            scrobbleBean.track = track.name
                            scrobbleBean.image = track.image[0].text
                            scrobbleBean.mbid = track.mbid
                            scrobbleBean.validated = true
                            Utils.log("Música validada na busca padrão: ${scrobbleBean.artist} - ${scrobbleBean.track}", applicationContext)
                            return scrobbleBean
                        }
                    }
                }
            } else {
                Utils.logError("Erro na validação da música: ${responseTrackArtist.code()}", applicationContext)

                scrobbleBean.validated = false
                scrobbleBean.validationError = true
                return scrobbleBean
            }

            val responseTrack = LastFmInitializer().lastFmService().validateTrack(scrobbleBean.track, Constants.API_KEY).execute()
            if (responseTrack.isSuccessful) {
                val trackList = responseTrack.body()?.results?.trackmatches?.track
                if (trackList != null) {
                    for (track in trackList) {
                        if (!track.mbid.isBlank() || !track.image[0].text.isBlank()) {
                            if (track.artist != "[unknown]" && track.name != "[unknown]") {
                                scrobbleBean.artist = track.artist
                                scrobbleBean.track = track.name
                                scrobbleBean.image = track.image[0].text
                                scrobbleBean.mbid = track.mbid
                                scrobbleBean.validated = true
                                Utils.log("Música validada na busca somente pelo nome: ${scrobbleBean.artist} - ${scrobbleBean.track}", applicationContext)
                                return scrobbleBean
                            }
                        }
                    }
                } else {
                    Utils.logError("Erro na validação da música: ${responseTrack.code()}", applicationContext)
                    scrobbleBean.validated = false
                    scrobbleBean.validationError = true
                    return scrobbleBean
                }
            }
        }
        return null
    }

    private fun getFullTrackInfo(scrobbleBean: ScrobbleBean): Long {
        val response = LastFmInitializer().lastFmService().fullTrackInfo(scrobbleBean.mbid, Constants.API_KEY).execute()
        return if (response.isSuccessful) {
            val duration = response.body()?.track?.duration
            if (!duration.isNullOrBlank()) {
                duration?.toLong()!!
            } else {
                Utils.log("Não encontrou a duração no Metadata nem no Last.fm, assume um minuto de música.", applicationContext)
                60000
            }
        } else {
            Utils.logError("Erro na obtenção das informações completas da música: ${response.code()}. Vai retornar um minuto de música.", applicationContext)
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
        Utils.log("ScrobblePendingAsync ${toScrobble?.artist} - ${toScrobble?.track}", applicationContext)
        val scrobbleBean = toScrobble
        Utils.logDebug("Vai zerar o toScrobble", applicationContext)
        toScrobble = null
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
            Utils.log("Música muito curta, não será realizado o scrobble: ".plus(scrobbleBean.artist).plus(" - ").plus(scrobbleBean.track), applicationContext)
        } else {
            Utils.log("Execução - Em milisegundos: ${scrobbleBean.playtime} - " +
                    "Em segundos: ${TimeUnit.MILLISECONDS.toSeconds(scrobbleBean.playtime)} - " +
                    "Em minutos: ${TimeUnit.MILLISECONDS.toMinutes(scrobbleBean.playtime)}", applicationContext)
            if (scrobbleBean.playtime > (scrobbleBean.duration / 2)) {
                if (isOnline && scrobbleBean.validated) {
                    val sessionKey = preferences?.getString("sessionKey", "")
                    val timestamp = (scrobbleBean.postTime.div(1000)).toString()
                    val paramsScrobble = mutableMapOf("track" to scrobbleBean.track, "artist" to scrobbleBean.artist, "sk" to sessionKey!!, "timestamp" to timestamp)
                    val sig = Utils.getSig(paramsScrobble, Constants.API_TRACK_SCROBBLE)
                    val response = LastFmInitializer().lastFmService().scrobble(scrobbleBean.artist, scrobbleBean.track, Constants.API_KEY, sig, sessionKey, timestamp).execute()
                    if (response.isSuccessful) {
                        Utils.log("Scrobbeled: ${scrobbleBean.artist} - ${scrobbleBean.track}", applicationContext)
                    } else {
                        Utils.logError("Erro ao fazer o scrobble, adiciona ao cache para tentar novamente depois: ${response.code()}", applicationContext)
                        cacheScrobble(scrobbleBean)
                    }
                } else {
                    cacheScrobble(scrobbleBean)
                }
            } else {
                Utils.log("Tempo de execução não alcançou ao menos metade da música, não será feito o scrobble: ${scrobbleBean.artist} - ${scrobbleBean.track}", applicationContext)
            }
        }
    }

    private fun cacheScrobble(scrobbleBean: ScrobbleBean) {
        Utils.log("Caching scrobble: ${scrobbleBean.artist} - ${scrobbleBean.track}", applicationContext)
        ScrobbleDb.getInstance(applicationContext).scrobbleDao().add(scrobbleBean)
    }

    private fun pauseTimer() {
        Utils.logDebug("Pause timer. Duração até aqui: ${playtime + playtimeHolder}", applicationContext)
        timer.cancel()
        paused = true
    }

    private fun resumeTimer() {
        Utils.logDebug("Resume timer", applicationContext)
        if (paused) {
            Utils.logDebug("Resumed timer", applicationContext)
            playtimeHolder += playtime
            paused = false
            Utils.log("Duração até o momento $playtimeHolder", applicationContext)
            timer.start()
        }
    }

    private fun observeConnection() {
        connectionLiveData = ConnectionLiveData(applicationContext)
        connectionLiveData.let {
            it?.observeForever { isConnected ->
                isConnected?.let {
                    Utils.log("Connection state: $isConnected", applicationContext)
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
        Utils.log("Scrobbling pending cache", applicationContext)
        val scrobbleDao = ScrobbleDb.getInstance(applicationContext).scrobbleDao()
        val scrobbles = scrobbleDao.getAll()
        for (scrobbleBean in scrobbles) {
            if (scrobbleBean.validated) {
                Utils.log("Scrobble já validado, só registra no Last.FM: ${scrobbleBean.artist} - ${scrobbleBean.track}", applicationContext)
                doAsync {
                    scrobble(scrobbleBean)
                }
            } else {
                doAsync {
                    Utils.log("Vai fazer a validação e em seguida o scrobble: ${scrobbleBean.artist} - ${scrobbleBean.track}", applicationContext)
                    val scrobbleValidationBean = allValidations(scrobbleBean)
                    if (scrobbleValidationBean != null) {
                        Utils.log("Música validada ${scrobbleBean.artist} - ${scrobbleBean.track}. Vai encaminhar pro scrobble.", applicationContext)
                        scrobble(scrobbleValidationBean)
                    } else {
                        Utils.log("Não foi possível validar a música, não será feito o scrobble.", applicationContext)
                    }
                }
            }
            scrobbleDao.delete(scrobbleBean)
        }
    }
}