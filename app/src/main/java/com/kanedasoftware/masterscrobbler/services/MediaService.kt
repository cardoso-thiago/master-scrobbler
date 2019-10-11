package com.kanedasoftware.masterscrobbler.services

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.CountDownTimer
import android.preference.PreferenceManager
import android.service.notification.NotificationListenerService
import com.google.gson.Gson
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.beans.ScrobbleBean
import com.kanedasoftware.masterscrobbler.db.ScrobbleDb
import com.kanedasoftware.masterscrobbler.main.LoginActivity
import com.kanedasoftware.masterscrobbler.models.ErrorInfo
import com.kanedasoftware.masterscrobbler.network.ConnectionLiveData
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.Utils
import com.kanedasoftware.masterscrobbler.ws.RetrofitInitializer
import de.adorsys.android.securestoragelibrary.SecurePreferences
import okhttp3.ResponseBody
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.concurrent.TimeUnit

class MediaService : NotificationListenerService(),
        MediaSessionManager.OnActiveSessionsChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

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
    private var isOnline = true
    private var finalDuration = 0L
    private var finalAlbum = ""
    private var totalSeconds = 600L
    private var startedService = false

    private var timer: CountDownTimer = object : CountDownTimer(totalSeconds * 1000, 1 * 1000) {
        override fun onFinish() {
            Utils.logDebug("Finish timer. Duration: ${playtime + playtimeHolder}")
            //Faz scrobble do que estiver pendente
            scrobblePendingAsync(playtime.plus(playtimeHolder), finalDuration, finalAlbum)
            playtime = 0L
            playtimeHolder = 0L
        }

        override fun onTick(millisUntilFinished: Long) {
            playtime = totalSeconds * 1000 - millisUntilFinished
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && !intent.action.isNullOrBlank()) {
            if (intent.action == Constants.START_SERVICE) {
                startService()
            } else if (intent.action == Constants.STOP_SERVICE) {
                stopService()
            }
        }
        return Service.START_STICKY
    }

    private fun startService() {
        if (!startedService) {
            startedService = true
            createCallback()
            tryReconnectService()
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            this.preferences = defaultSharedPreferences
            defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
            observeConnection()
            createNotification()
            createMediaSessionManager()
        }
    }

    private fun stopService() {
        if (startedService) {
            startedService = false
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            mediaSessionManager.removeOnActiveSessionsChangedListener(this)
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            defaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
            unregisterCallback(mediaController)
            stopForeground(true)
            stopSelf()
        }
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

    private fun createNotification() {
        val notification = Utils.buildNotification(getString(R.string.app_name), getString(R.string.service_running))
        startForeground(Constants.NOTIFICATION_ID, notification)
    }

    private fun createCallback() {
        mediaControllerCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                playbackState = state?.state!!
                Utils.logDebug("Playback state: $playbackState")
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
        Utils.log("Preference changed: $key")
        if (key == "apps_to_scrobble") {
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
            var album = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ALBUM)

            if(artist.isNullOrEmpty() || track.isNullOrEmpty()) {
                Utils.log("Não conseguiu obter o artista ou música do metadata, não irá realizar a validação.")
            } else {
                if (playbackState != PlaybackState.STATE_PAUSED) {
                    if ((metadataArtist != artist || metadataTrack != track) || ((playtime + playtimeHolder) > (duration / 2))) {
                        timer.onFinish()
                        Utils.log("Duração até o momento $playtimeHolder")
                        timer.start()

                        Utils.logDebug("Vai iniciar a validação com $artist - $track  - PlaybackState: $playbackState")
                        if (album == null) {
                            album = ""
                        }
                        finalAlbum = album
                        finalDuration = duration
                        metadataArtist = artist
                        metadataTrack = track

                        //Valida qualquer música que estiver pendente
                        validateAndScrobblePending()

                        val postTime = System.currentTimeMillis()
                        //Cria novo bean com validação com base na informação atual
                        val scrobbleBean = ScrobbleBean(Utils.clearArtist(artist), Utils.clearTrack(track), postTime, duration)
                        //Seta o artista e música originais por questões de histórico e comparação com o artista em validação atual
                        scrobbleBean.originalArtist = artist
                        scrobbleBean.originalTrack = track

                        //Já atualiza a notificação com a informação que veio, pode mudar depois
                        Utils.updateNotification(getString(R.string.notification_scrobbling), "${getString(R.string.notification_scrobbling_validating)}: ${scrobbleBean.artist} - ${scrobbleBean.track}")

                        //Se não estiver conectado já adiciona para realizar o scrobble offline
                        if (isOnline) {
                            doAsync {
                                val scrobbleValidationBean = allValidations(scrobbleBean)
                                if(scrobbleBean.originalArtist != metadataArtist && scrobbleBean.originalTrack != metadataTrack) {
                                    Utils.log("Não é a mesma música em execução, não prossegue com a validação")
                                } else {
                                    if (scrobbleValidationBean == null) {
                                        Utils.log("Não conseguiu validar a música, não será realizado o scrobble.")
                                        uiThread {
                                            Utils.updateNotification(getString(R.string.app_name), getString(R.string.notification_scrobbling_validation_error))
                                        }
                                    } else {
                                        if (!scrobbleValidationBean.validationError) {
                                            Utils.log("Sem erro de validação, vai tentar obter a imagem pra montar a notificação.")
                                            val artistImageUrl = "https://tse2.mm.bing.net/th?q=${scrobbleValidationBean.artist} Band&w=500&h=500&c=7&rs=1&p=0&dpr=3&pid=1.7&mkt=en-IN&adlt=on"
                                            uiThread {
                                                val title = getString(R.string.notification_scrobbling)
                                                val text = "${scrobbleValidationBean.artist} - ${scrobbleValidationBean.track}"
                                                Utils.updateNotification(title, text)
                                                Utils.log("Fazendo a requisição ao Picasso.")
                                                Utils.getNotificationImageCache(artistImageUrl, title, text)
                                            }

                                            Utils.log("Vai atualizar o NowPlaying e gravar a música para o scrobble (toScrobble)")
                                            toScrobble = scrobbleValidationBean
                                            updateNowPlaying(scrobbleValidationBean)
                                        } else {
                                            Utils.log("Erro de validação, vai armazenar a música  para o próximo scrobble (toScrobble)")
                                            toScrobble = scrobbleBean
                                        }
                                    }
                                }
                            }
                        } else {
                            Utils.updateNotification(getString(R.string.app_name), "${getString(R.string.notification_scrobbling_offline)}: ${scrobbleBean.artist} - ${scrobbleBean.track}\"")
                            Utils.log("Não está online, vai armazenar a música para o próximo scrobble (toScrobble)")
                            toScrobble = scrobbleBean
                        }
                    } else {
                        Utils.logDebug("Mesma música tocando, será ignorada a mudança de metadata, mas será atualizada a duração da música e o álbum.")
                        if (finalDuration != duration) {
                            finalDuration = duration
                            Utils.log("Duração Atualizada - Em milisegundos: $duration - " +
                                    "Em segundos: ${TimeUnit.MILLISECONDS.toSeconds(duration)} - " +
                                    "Em minutos: ${TimeUnit.MILLISECONDS.toMinutes(duration)}")
                        }
                        if (album != null) {
                            if (finalAlbum != Utils.clearAlbum(album)) {
                                Utils.log("Album atualizado de $finalAlbum para $album")
                                finalAlbum = Utils.clearAlbum(album)
                            }
                        }
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
            val playersMap = Utils.getPlayersMap()
            val packageManager = applicationContext?.packageManager
            val packagePlayerList = Utils.getPackagePlayerList(packageManager)

            for (controller in controllers) {
                if (!packagePlayerList.contains(controller.packageName)) {
                    if (!playersMap.containsKey(controller.packageName)) {
                        val playerName = packageManager?.getApplicationLabel(packageManager.getApplicationInfo(controller.packageName, 0)).toString()
                        playersMap[controller.packageName] = playerName
                        Utils.sendNewPlayerNotification(playerName)
                    }
                }
            }
            Utils.savePlayersMap(playersMap)
        }

        val activeMediaController = controllers?.firstOrNull()
        val entries = preferences?.getStringSet("apps_to_scrobble", HashSet<String>())
        if (entries != null) {
            if (entries.size > 0) {
                if (entries.contains(activeMediaController?.packageName)) {
                    activeMediaController?.let { mediaController ->
                        registerCallback(mediaController)
                    }
                } else {
                    Utils.log("Nenhum app ativo para scrobble. App ativo ${activeMediaController?.packageName}")
                }
            } else {
                Utils.log("Nenhum app selecionado para scrobble.")
            }
        }
    }

    private fun registerCallback(newMediaController: MediaController) {
        unregisterCallback(this.mediaController)
        Utils.log("Registering callback for ${newMediaController.packageName}")

        if (newMediaController.playbackState != null) {
            //Atualiza o estado atual do playback
            playbackState = newMediaController.playbackState!!.state
            if (playbackState == PlaybackState.STATE_PLAYING) {
                resumeTimer()
            }
        }
        handleMetadataChange(newMediaController.metadata)

        mediaController = newMediaController
        if (mediaControllerCallback != null) {
            newMediaController.registerCallback(mediaControllerCallback!!)
        }
    }

    private fun unregisterCallback(mediaController: MediaController?) {
        Utils.log("Unregistering callback for ${mediaController?.packageName}")
        if (mediaControllerCallback != null) {
            mediaController?.unregisterCallback(mediaControllerCallback!!)
        }
    }

    private fun allValidations(scrobbleBean: ScrobbleBean): ScrobbleBean? {
        return if (isOnline) {
            //Caso caia a conexão exatamente durante o processo de validação, tenta capturar a exceção e devolve o bean original
            try {
                val scrobbleBeanValidationBean = validateTrack(scrobbleBean)
                if (scrobbleBeanValidationBean != null) {
                    if (scrobbleBeanValidationBean.duration == 0L) {
                        Utils.log("Não conseguiu obter a duração no metadata, vai tentar obter no Last.fm")
                        scrobbleBeanValidationBean.duration = getDurationFromTrackInfo(scrobbleBeanValidationBean)
                    }
                }
                scrobbleBeanValidationBean
            } catch (t: Throwable) {
                scrobbleBean
            }
        } else {
            scrobbleBean
        }
    }

    private fun validateTrack(scrobbleBean: ScrobbleBean): ScrobbleBean? {
        val responseArtist = RetrofitInitializer().lastFmService().validateArtist(scrobbleBean.artist, Constants.API_KEY).execute()
        if (responseArtist.isSuccessful) {
            val artistList = responseArtist.body()?.results?.artistmatches?.artist
            if (artistList != null) {
                for (artist in artistList) {
                    if (!artist.mbid.isBlank() || !artist.image[0].text.isBlank()) {
                        if (scrobbleBean.artist == artist.name) {
                            Utils.log("Encontrou o artista ${scrobbleBean.artist} no Last.fm, assume que a música está correta.")
                            scrobbleBean.validated = true
                            return scrobbleBean
                        }
                    }
                }
            }
        } else {
            Utils.logError("Erro na validação da música: ${responseArtist.code()}")
            scrobbleBean.validated = false
            scrobbleBean.validationError = true
            return scrobbleBean
        }

        val responseTrackArtist = RetrofitInitializer().lastFmService().validateTrackAndArtist(scrobbleBean.artist, scrobbleBean.track, Constants.API_KEY).execute()
        if (responseTrackArtist.isSuccessful) {
            val artistTrackList = responseTrackArtist.body()?.results?.trackmatches?.track
            if (artistTrackList != null) {
                for (track in artistTrackList) {
                    if (!track.mbid.isBlank() || !track.image[0].text.isBlank()) {
                        if (track.artist != "[unknown]" && track.name != "[unknown]") {
                            scrobbleBean.artist = track.artist
                            scrobbleBean.track = track.name
                            scrobbleBean.mbid = track.mbid
                            scrobbleBean.album = getAlbumFromTrackInfo(scrobbleBean)
                            scrobbleBean.validated = true
                            Utils.log("Música validada na busca padrão: ${scrobbleBean.artist} - ${scrobbleBean.track}")
                            return scrobbleBean
                        }
                    }
                }
            } else {
                Utils.logError("Erro na validação da música: ${responseTrackArtist.code()}")

                scrobbleBean.validated = false
                scrobbleBean.validationError = true
                return scrobbleBean
            }

            val responseTrack = RetrofitInitializer().lastFmService().validateTrack(scrobbleBean.track, Constants.API_KEY).execute()
            if (responseTrack.isSuccessful) {
                val trackList = responseTrack.body()?.results?.trackmatches?.track
                if (trackList != null) {
                    for (track in trackList) {
                        if (!track.mbid.isBlank() || !track.image[0].text.isBlank()) {
                            if (track.artist != "[unknown]" && track.name != "[unknown]") {
                                scrobbleBean.artist = track.artist
                                scrobbleBean.track = track.name
                                scrobbleBean.mbid = track.mbid
                                scrobbleBean.album = getAlbumFromTrackInfo(scrobbleBean)
                                scrobbleBean.validated = true
                                Utils.log("Música validada na busca somente pelo nome: ${scrobbleBean.artist} - ${scrobbleBean.track}")
                                return scrobbleBean
                            }
                        }
                    }
                } else {
                    Utils.logError("Erro na validação da música: ${responseTrack.code()}")
                    scrobbleBean.validated = false
                    scrobbleBean.validationError = true
                    return scrobbleBean
                }
            }
        }
        return null
    }

    private fun getAlbumFromTrackInfo(scrobbleBean: ScrobbleBean):String{
        val response = RetrofitInitializer().lastFmService().fullTrackInfo(scrobbleBean.mbid, Constants.API_KEY).execute()
        return if (response.isSuccessful) {
            val album = response.body()?.track?.album?.title
            if (!album.isNullOrBlank()) {
                album
            } else {
                Utils.log("Não encontrou a duração no Metadata nem no Last.fm, assume um minuto de música.")
                ""
            }
        } else {
            Utils.logError("Erro na obtenção das informações completas da música: ${response.code()}. Vai retornar um minuto de música.")
            ""
        }
    }

    private fun getDurationFromTrackInfo(scrobbleBean: ScrobbleBean): Long {
        val response = RetrofitInitializer().lastFmService().fullTrackInfo(scrobbleBean.mbid, Constants.API_KEY).execute()
        return if (response.isSuccessful) {
            val duration = response.body()?.track?.duration
            if (!duration.isNullOrBlank()) {
                duration.toLong()
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
        val sessionKey = SecurePreferences.getStringValue(applicationContext, Constants.SECURE_SESSION_TAG, "")
        if (sessionKey != null) {
            val params = mutableMapOf("track" to scrobbleBean.track, "artist" to scrobbleBean.artist, "sk" to sessionKey)
            val sig = Utils.getSig(params, Constants.API_UPDATE_NOW_PLAYING)
            val response = RetrofitInitializer().lastFmService().updateNowPlaying(scrobbleBean.artist, scrobbleBean.track, Constants.API_KEY, sig, sessionKey).execute()
            if (!response.isSuccessful) {
                verifySessionKey(response.errorBody())
            }
        }
    }

    private fun scrobblePendingAsync(playtimeHolder: Long, finalDuration: Long, finalAlbum: String) {
        Utils.log("ScrobblePendingAsync ${toScrobble?.artist} - ${toScrobble?.track}")
        val scrobbleBean = toScrobble
        Utils.logDebug("Vai zerar o toScrobble")
        toScrobble = null
        if (scrobbleBean != null) {
            scrobbleBean.playtime = scrobbleBean.playtime + playtimeHolder
            scrobbleBean.duration = finalDuration
            if(scrobbleBean.album.isEmpty()){
                scrobbleBean.album = finalAlbum
            }
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
                    val sessionKey = SecurePreferences.getStringValue(applicationContext, Constants.SECURE_SESSION_TAG, "")
                    val timestamp = (scrobbleBean.postTime.div(1000)).toString()
                    val paramsScrobble = mutableMapOf("track" to scrobbleBean.track, "artist" to scrobbleBean.artist, "album" to scrobbleBean.album, "sk" to sessionKey!!, "timestamp" to timestamp)
                    val sig = Utils.getSig(paramsScrobble, Constants.API_TRACK_SCROBBLE)
                    //Tenta capturar qualquer exceção, caso a conexão caia durante o processo de scrobble e adiciona o scrobble pro cache.
                    try {
                        val response = RetrofitInitializer().lastFmService().scrobble(scrobbleBean.artist, scrobbleBean.track, scrobbleBean.album, Constants.API_KEY, sig, sessionKey, timestamp).execute()
                        if (response.isSuccessful) {
                            Utils.log("Scrobbeled: ${scrobbleBean.artist} - ${scrobbleBean.track}")
                        } else {
                            Utils.logError("Erro ao fazer o scrobble, adiciona ao cache para tentar novamente depois: ${response.code()}")
                            cacheScrobble(scrobbleBean)
                            verifySessionKey(response.errorBody())
                        }
                    } catch (t: Throwable) {
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

    private fun verifySessionKey(responseBody: ResponseBody?) {
        val errorInfo = Gson().fromJson(responseBody?.charStream(), ErrorInfo::class.java)
        //Session Key inválida, necessário reautenticar
        if (errorInfo.error == 9) {
            stopService()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun cacheScrobble(scrobbleBean: ScrobbleBean) {
        Utils.log("Caching scrobble: ${scrobbleBean.artist} - ${scrobbleBean.track}")
        ScrobbleDb.getInstance(applicationContext).scrobbleDao().add(scrobbleBean)
    }

    private fun pauseTimer() {
        Utils.logDebug("Pause timer. Duração até aqui: ${playtime + playtimeHolder}")
        timer.cancel()
        paused = true
    }

    private fun resumeTimer() {
        Utils.logDebug("Resume timer")
        if (paused) {
            Utils.logDebug("Resumed timer")
            playtimeHolder += playtime
            paused = false
            Utils.log("Execução até o momento $playtimeHolder")
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
        Utils.log("Scrobbling pending cache")
        if (isOnline) {
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
                        val scrobbleValidationBean = allValidations(scrobbleBean)
                        if (scrobbleValidationBean != null) {
                            Utils.log("Música validada ${scrobbleBean.artist} - ${scrobbleBean.track}. Vai encaminhar pro scrobble.")
                            scrobble(scrobbleValidationBean)
                        } else {
                            Utils.log("Não foi possível validar a música, não será feito o scrobble.")
                        }
                    }
                }
                scrobbleDao.delete(scrobbleBean)
            }
        } else {
            Utils.log("Não vai sincronizar o cache, não está online")
        }
    }
}