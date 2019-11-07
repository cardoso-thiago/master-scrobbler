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
import android.service.notification.NotificationListenerService
import com.google.gson.Gson
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.beans.Scrobble
import com.kanedasoftware.masterscrobbler.db.ScrobbleDb
import com.kanedasoftware.masterscrobbler.main.LoginActivity
import com.kanedasoftware.masterscrobbler.models.ErrorInfo
import com.kanedasoftware.masterscrobbler.network.ConnectionLiveData
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.ImageUtils
import com.kanedasoftware.masterscrobbler.utils.NotificationUtils
import com.kanedasoftware.masterscrobbler.utils.Utils
import de.adorsys.android.securestoragelibrary.SecurePreferences
import okhttp3.ResponseBody
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

class MediaService : NotificationListenerService(),
        MediaSessionManager.OnActiveSessionsChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private val scrobbleDb: ScrobbleDb by inject()
    private val utils: Utils by inject()
    private val imageUtils: ImageUtils by inject()
    private val notificationUtils: NotificationUtils by inject()
    private val lastFmService: LastFmService by inject()

    private var mediaController: MediaController? = null
    private var mediaControllerCallback: MediaController.Callback? = null
    private var connectionLiveData: ConnectionLiveData? = null
    private var playbackState = 0
    private var metadataArtist = ""
    private var metadataTrack = ""
    private var playtimeHolder = 0L
    private var paused = false
    private var playtime = 0L
    private var toScrobble: Scrobble? = null
    private var isOnline = true
    private var finalDuration = 0L
    private var finalAlbum = ""
    private var totalSeconds = 600L
    private var startedServiceLocal = false

    private var timer: CountDownTimer = object : CountDownTimer(totalSeconds * 1000, 1 * 1000) {
        override fun onFinish() {
            utils.logDebug("Finish timer. Duration: ${playtime + playtimeHolder}")
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
            utils.log("START COMMAND ${intent.action}")
            when {
                intent.action == Constants.START_SERVICE -> startService()
                intent.action == Constants.STOP_SERVICE -> stopService()
                intent.action == Constants.SCROBBLE_PENDING_SERVICE -> validateAndScrobblePending()
            }
        }
        return Service.START_STICKY
    }

    private fun startService() {
        if (!startedServiceLocal) {
            if (utils.hasAppsToScrobble()) {
                utils.setStartedService(true)
                startedServiceLocal = true

                notificationUtils.cancelNoPlayerNotification()
                createSharedPreferenceListener()
                createMediaSessionManager()
                createCallback()
                tryReconnectService()
                observeConnection()
                createDefaultServiceNotification()
            } else {
                notificationUtils.sendNoPlayerNotification()
                createSharedPreferenceListener()
                createMediaSessionManager()
                createDefaultServiceNotification()
            }
        }
    }

    private fun stopService() {
        if (startedServiceLocal) {
            utils.setStartedService(false)
            startedServiceLocal = false
            (getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager).removeOnActiveSessionsChangedListener(this)
            utils.getPreferences().unregisterOnSharedPreferenceChangeListener(this)
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
        packageManager.setComponentEnabledSetting(ComponentName(this, MediaService::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        packageManager.setComponentEnabledSetting(ComponentName(this, MediaService::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }

    private fun createDefaultServiceNotification() {
        val notification = notificationUtils.buildNotification(getString(R.string.app_name), getString(R.string.service_running))
        startForeground(Constants.NOTIFICATION_ID, notification)
    }

    private fun createCallback() {
        mediaControllerCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                playbackState = state?.state!!
                utils.logDebug("Playback state: $playbackState")
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

    private fun createSharedPreferenceListener() {
        utils.getPreferences().unregisterOnSharedPreferenceChangeListener(this)
        utils.getPreferences().registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        utils.logDebug("Preference changed: $key")
        if (key == "apps_to_scrobble") {
            if (utils.hasAppsToScrobble()) {
                createMediaSessionManager()
            }
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
        utils.logDebug("METADATA changed")
        metadata?.let { mediaMetadata ->
            val artist = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val track = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            val duration = mediaMetadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            var album = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ALBUM)

            if (artist.isNullOrEmpty() || track.isNullOrEmpty()) {
                utils.log("Não conseguiu obter o artista ou música do metadata, não irá realizar a validação.")
            } else {
                if ((metadataArtist != artist || metadataTrack != track) || ((playtime + playtimeHolder) > (duration / 2))) {
                    timer.onFinish()
                    utils.log("Duração até o momento $playtimeHolder")
                    timer.start()

                    utils.logDebug("Vai iniciar a validação com $artist - $track  - PlaybackState: $playbackState")
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
                    val scrobbleBean = Scrobble(utils.clearArtist(artist), utils.clearTrack(track), postTime, duration)
                    //Seta o artista e música originais por questões de histórico e comparação com o artista em validação atual
                    scrobbleBean.originalArtist = artist
                    scrobbleBean.originalTrack = track

                    //Já atualiza a notificação com a informação que veio, pode mudar depois
                    notificationUtils.updateNotification(getString(R.string.notification_scrobbling), "${getString(R.string.notification_scrobbling_validating)}: ${scrobbleBean.artist} - ${scrobbleBean.track}")

                    //Se não estiver conectado já adiciona para realizar o scrobble offline
                    if (isOnline) {
                        doAsync {
                            val scrobbleValidationBean = allValidations(scrobbleBean)
                            if (scrobbleBean.originalArtist != metadataArtist && scrobbleBean.originalTrack != metadataTrack) {
                                utils.log("Não é a mesma música em execução, não prossegue com a validação")
                            } else {
                                if (scrobbleValidationBean == null) {
                                    utils.log("Não conseguiu validar a música, não será realizado o scrobble.")
                                    uiThread {
                                        notificationUtils.updateNotification(getString(R.string.app_name), getString(R.string.notification_scrobbling_validation_error))
                                    }
                                } else {
                                    if (!scrobbleValidationBean.validationError) {
                                        utils.log("Sem erro de validação, vai tentar obter a imagem pra montar a notificação.")
                                        val artistImageUrl = "https://tse2.mm.bing.net/th?q=${scrobbleValidationBean.artist} Band&w=500&h=500&c=7&rs=1&p=0&dpr=3&pid=1.7&mkt=en-IN&adlt=on"
                                        uiThread {
                                            val title = getString(R.string.notification_scrobbling)
                                            val text = "${scrobbleValidationBean.artist} - ${scrobbleValidationBean.track}"
                                            notificationUtils.updateNotification(title, text)
                                            utils.log("Fazendo a requisição ao Glide.")
                                            imageUtils.updateNotificationWithImage(artistImageUrl, title, text)
                                        }

                                        utils.log("Vai atualizar o NowPlaying e gravar a música para o scrobble (toScrobble)")
                                        toScrobble = scrobbleValidationBean
                                        updateNowPlaying(scrobbleValidationBean)
                                    } else {
                                        utils.log("Erro de validação, vai armazenar a música  para o próximo scrobble (toScrobble)")
                                        toScrobble = scrobbleBean
                                    }
                                }
                            }
                        }
                    } else {
                        notificationUtils.updateNotification(getString(R.string.app_name), "${getString(R.string.notification_scrobbling_offline)}: ${scrobbleBean.artist} - ${scrobbleBean.track}")
                        utils.log("Não está online, vai armazenar a música para o próximo scrobble (toScrobble)")
                        toScrobble = scrobbleBean
                    }
                } else {
                    when {
                        (finalDuration != duration) -> {
                            finalDuration = duration
                            utils.log("Duração Atualizada - Em milisegundos: $duration - " +
                                    "Em segundos: ${TimeUnit.MILLISECONDS.toSeconds(duration)} - " +
                                    "Em minutos: ${TimeUnit.MILLISECONDS.toMinutes(duration)}")
                        }
                        (album != null && finalAlbum != utils.clearAlbum(album)) -> {
                            utils.log("Album atualizado de $finalAlbum para $album")
                            finalAlbum = utils.clearAlbum(album)
                        }
                        else ->
                            utils.logDebug("Mesma música tocando, será ignorada a mudança de metadata.")
                    }
                }
            }
        }
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        utils.logDebug("Active Sessions Changed")
        if (controllers != null) {
            //Se não tem controles ativos envia um broadcast para o receiver sem artista, para fazer scrobble de alguma possível música pendente
            if (controllers.size == 0) {
                stopScrobbler()
            }
            val playersMap = utils.getPlayersMap()
            val packageManager = applicationContext?.packageManager
            val packagePlayerList = utils.getPackagePlayerList(packageManager)

            for (controller in controllers) {
                if (!packagePlayerList.contains(controller.packageName)) {
                    if (!playersMap.containsKey(controller.packageName)) {
                        val playerName = packageManager?.getApplicationLabel(packageManager.getApplicationInfo(controller.packageName, 0)).toString()
                        playersMap[controller.packageName] = playerName
                        notificationUtils.cancelNoPlayerNotification()
                        notificationUtils.sendNewPlayerNotification(playerName)
                    }
                }
            }
            utils.savePlayersMap(playersMap)
        }

        val activeMediaController = controllers?.firstOrNull()
        val entries = utils.getPreferences().getStringSet("apps_to_scrobble", HashSet<String>())
        if (entries != null) {
            if (entries.size > 0) {
                if (entries.contains(activeMediaController?.packageName)) {
                    activeMediaController?.let { mediaController ->
                        registerCallback(mediaController)
                    }
                } else {
                    utils.log("Nenhum app ativo para scrobble. App ativo ${activeMediaController?.packageName}")
                }
            } else {
                utils.log("Nenhum app selecionado para scrobble.")
            }
        }
    }

    private fun stopScrobbler() {
        metadataArtist = ""
        metadataTrack = ""

        //Para de contar o tempo da música, zera o contador e faz algum possível scrobble pendente
        timer.onFinish()

        //Atualiza a notificação para o padrão do serviço
        createDefaultServiceNotification()
    }

    private fun registerCallback(newMediaController: MediaController) {
        unregisterCallback(this.mediaController)
        utils.log("Registering callback for ${newMediaController.packageName}")

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
        utils.log("Unregistering callback for ${mediaController?.packageName}")
        if (mediaControllerCallback != null) {
            mediaController?.unregisterCallback(mediaControllerCallback!!)
        }
    }

    private fun allValidations(scrobble: Scrobble): Scrobble? {
        return if (isOnline) {
            //Caso caia a conexão exatamente durante o processo de validação, tenta capturar a exceção e devolve o bean original
            try {
                val scrobbleBeanValidationBean = validateTrack(scrobble)
                if (scrobbleBeanValidationBean != null) {
                    if (scrobbleBeanValidationBean.duration == 0L) {
                        utils.log("Não conseguiu obter a duração no metadata, vai tentar obter no Last.fm")
                        scrobbleBeanValidationBean.duration = getDurationFromTrackInfo(scrobbleBeanValidationBean)
                    }
                }
                scrobbleBeanValidationBean
            } catch (t: Throwable) {
                scrobble
            }
        } else {
            scrobble
        }
    }

    private fun validateTrack(scrobble: Scrobble): Scrobble? {
        val responseArtist = lastFmService.validateArtist(scrobble.artist, Constants.API_KEY).execute()
        if (responseArtist.isSuccessful) {
            val artistList = responseArtist.body()?.results?.artistmatches?.artist
            if (artistList != null) {
                for (artist in artistList) {
                    if (!artist.mbid.isBlank() || !artist.image[0].text.isBlank()) {
                        if (scrobble.artist == artist.name) {
                            utils.log("Encontrou o artista ${scrobble.artist} no Last.fm, assume que a música está correta.")
                            scrobble.validated = true
                            return scrobble
                        }
                    }
                }
            }
        } else {
            utils.logError("Erro na validação da música: ${responseArtist.code()}")
            scrobble.validated = false
            scrobble.validationError = true
            return scrobble
        }

        val responseTrackArtist = lastFmService.validateTrackAndArtist(scrobble.artist, scrobble.track, Constants.API_KEY).execute()
        if (responseTrackArtist.isSuccessful) {
            val artistTrackList = responseTrackArtist.body()?.results?.trackmatches?.track
            if (artistTrackList != null) {
                for (track in artistTrackList) {
                    if (!track.mbid.isBlank() || !track.image[0].text.isBlank()) {
                        if (track.artist != "[unknown]" && track.name != "[unknown]") {
                            scrobble.artist = track.artist
                            scrobble.track = track.name
                            scrobble.mbid = track.mbid
                            scrobble.album = getAlbumFromTrackInfo(scrobble)
                            scrobble.validated = true
                            utils.log("Música validada na busca padrão: ${scrobble.artist} - ${scrobble.track}")
                            return scrobble
                        }
                    }
                }
            } else {
                utils.logError("Erro na validação da música: ${responseTrackArtist.code()}")

                scrobble.validated = false
                scrobble.validationError = true
                return scrobble
            }

            val responseTrack = lastFmService.validateTrack(scrobble.track, Constants.API_KEY).execute()
            if (responseTrack.isSuccessful) {
                val trackList = responseTrack.body()?.results?.trackmatches?.track
                if (trackList != null) {
                    for (track in trackList) {
                        if (!track.mbid.isBlank() || !track.image[0].text.isBlank()) {
                            if (track.artist != "[unknown]" && track.name != "[unknown]") {
                                scrobble.artist = track.artist
                                scrobble.track = track.name
                                scrobble.mbid = track.mbid
                                scrobble.album = getAlbumFromTrackInfo(scrobble)
                                scrobble.validated = true
                                utils.log("Música validada na busca somente pelo nome: ${scrobble.artist} - ${scrobble.track}")
                                return scrobble
                            }
                        }
                    }
                } else {
                    utils.logError("Erro na validação da música: ${responseTrack.code()}")
                    scrobble.validated = false
                    scrobble.validationError = true
                    return scrobble
                }
            }
        }
        return null
    }

    private fun getAlbumFromTrackInfo(scrobble: Scrobble): String {
        val response = lastFmService.fullTrackInfo(scrobble.mbid, Constants.API_KEY).execute()
        return if (response.isSuccessful) {
            val album = response.body()?.track?.album?.title
            if (!album.isNullOrBlank()) {
                album
            } else {
                utils.log("Não encontrou o album no Last.fm.")
                ""
            }
        } else {
            utils.logError("Erro na obtenção das informações completas da música: ${response.code()}.")
            ""
        }
    }

    private fun getDurationFromTrackInfo(scrobble: Scrobble): Long {
        val response = lastFmService.fullTrackInfo(scrobble.mbid, Constants.API_KEY).execute()
        return if (response.isSuccessful) {
            val duration = response.body()?.track?.duration
            if (!duration.isNullOrBlank()) {
                duration.toLong()
            } else {
                utils.log("Não encontrou a duração no Metadata nem no Last.fm, assume um minuto de música.")
                60000
            }
        } else {
            utils.logError("Erro na obtenção das informações completas da música: ${response.code()}. Vai retornar um minuto de música.")
            60000
        }
    }

    private fun updateNowPlaying(scrobble: Scrobble) {
        val sessionKey = SecurePreferences.getStringValue(applicationContext, Constants.SECURE_SESSION_TAG, "")
        if (sessionKey != null) {
            val params = mutableMapOf("track" to scrobble.track, "artist" to scrobble.artist, "sk" to sessionKey)
            val sig = utils.getSig(params, Constants.API_UPDATE_NOW_PLAYING)
            val response = lastFmService.updateNowPlaying(scrobble.artist, scrobble.track, Constants.API_KEY, sig, sessionKey).execute()
            if (!response.isSuccessful) {
                verifySessionKey(response.errorBody())
            }
        }
    }

    private fun scrobblePendingAsync(playtimeHolder: Long, finalDuration: Long, finalAlbum: String) {
        utils.log("ScrobblePendingAsync ${toScrobble?.artist} - ${toScrobble?.track}")
        val scrobbleBean = toScrobble
        utils.logDebug("Vai zerar o toScrobble")
        toScrobble = null
        if (scrobbleBean != null) {
            scrobbleBean.playtime = scrobbleBean.playtime + playtimeHolder
            scrobbleBean.duration = finalDuration
            if (scrobbleBean.album.isEmpty()) {
                scrobbleBean.album = finalAlbum
            }
            doAsync {
                scrobble(scrobbleBean)
            }
        }
    }

    private fun scrobble(scrobble: Scrobble) {
        if (scrobble.duration <= 30000) {
            utils.log("Música muito curta, não será realizado o scrobble: ".plus(scrobble.artist).plus(" - ").plus(scrobble.track))
        } else {
            utils.log("Execução - Em milisegundos: ${scrobble.playtime} - " +
                    "Em segundos: ${TimeUnit.MILLISECONDS.toSeconds(scrobble.playtime)} - " +
                    "Em minutos: ${TimeUnit.MILLISECONDS.toMinutes(scrobble.playtime)}")
            if (scrobble.playtime > (scrobble.duration / 2)) {
                if (isOnline && scrobble.validated) {
                    val sessionKey = SecurePreferences.getStringValue(applicationContext, Constants.SECURE_SESSION_TAG, "")
                    val timestamp = (scrobble.postTime.div(1000)).toString()
                    val paramsScrobble = mutableMapOf("track" to scrobble.track, "artist" to scrobble.artist, "album" to scrobble.album, "sk" to sessionKey!!, "timestamp" to timestamp)
                    val sig = utils.getSig(paramsScrobble, Constants.API_TRACK_SCROBBLE)
                    //Tenta capturar qualquer exceção, caso a conexão caia durante o processo de scrobble e adiciona o scrobble pro cache.
                    try {
                        val response = lastFmService.scrobble(scrobble.artist, scrobble.track, scrobble.album, Constants.API_KEY, sig, sessionKey, timestamp).execute()
                        if (response.isSuccessful) {
                            utils.log("Scrobbeled: ${scrobble.artist} - ${scrobble.track}")
                        } else {
                            utils.logError("Erro ao fazer o scrobble, adiciona ao cache para tentar novamente depois: ${response.code()}")
                            cacheScrobble(scrobble)
                            verifySessionKey(response.errorBody())
                        }
                    } catch (t: Throwable) {
                        cacheScrobble(scrobble)
                    }
                } else {
                    cacheScrobble(scrobble)
                }
            } else {
                utils.log("Tempo de execução não alcançou ao menos metade da música, não será feito o scrobble: ${scrobble.artist} - ${scrobble.track}")
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

    private fun cacheScrobble(scrobble: Scrobble) {
        utils.log("Caching scrobble: ${scrobble.artist} - ${scrobble.track}")
        scrobbleDb.scrobbleDao().add(scrobble)
    }

    private fun pauseTimer() {
        utils.logDebug("Pause timer. Duração até aqui: ${playtime + playtimeHolder}")
        notificationUtils.updateNotification(getString(R.string.app_name), getString(R.string.service_running))
        timer.cancel()
        paused = true
    }

    private fun resumeTimer() {
        utils.logDebug("Resume timer")
        if (paused) {
            if (toScrobble != null) {
                val scrobbleBean = toScrobble
                val artistImageUrl = "https://tse2.mm.bing.net/th?q=${scrobbleBean?.artist} Band&w=500&h=500&c=7&rs=1&p=0&dpr=3&pid=1.7&mkt=en-IN&adlt=on"
                val title = getString(R.string.notification_scrobbling)
                val text = "${scrobbleBean?.artist} - ${scrobbleBean?.track}"
                imageUtils.updateNotificationWithImage(artistImageUrl, title, text)
            }
            utils.logDebug("Resumed timer")
            playtimeHolder += playtime
            paused = false
            utils.log("Execução até o momento $playtimeHolder")
            timer.start()
        }
    }

    private fun observeConnection() {
        connectionLiveData = ConnectionLiveData(applicationContext)
        connectionLiveData.let {
            it?.observeForever { isConnected ->
                isConnected?.let {
                    utils.log("Connection state: $isConnected")
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
        utils.log("Scrobbling pending cache")
        if (isOnline) {
            val scrobbleDao = scrobbleDb.scrobbleDao()
            val scrobbles = scrobbleDao.getAll()
            for (scrobbleBean in scrobbles) {
                if (scrobbleBean.validated) {
                    utils.log("Scrobble já validado, só registra no Last.FM: ${scrobbleBean.artist} - ${scrobbleBean.track}")
                    doAsync {
                        scrobble(scrobbleBean)
                    }
                } else {
                    doAsync {
                        utils.log("Vai fazer a validação e em seguida o scrobble: ${scrobbleBean.artist} - ${scrobbleBean.track}")
                        val scrobbleValidationBean = allValidations(scrobbleBean)
                        if (scrobbleValidationBean != null) {
                            utils.log("Música validada ${scrobbleBean.artist} - ${scrobbleBean.track}. Vai encaminhar pro scrobble.")
                            scrobble(scrobbleValidationBean)
                        } else {
                            utils.log("Não foi possível validar a música, não será feito o scrobble.")
                        }
                    }
                }
                scrobbleDao.delete(scrobbleBean)
            }
        } else {
            utils.log("Não vai sincronizar o cache, não está online")
        }
    }
}