package com.kanedasoftware.masterscrobbler.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Handler
import android.preference.PreferenceManager
import android.widget.Toast
import com.kanedasoftware.masterscrobbler.beans.ScrobbleBean
import com.kanedasoftware.masterscrobbler.model.FullTrackInfo
import com.kanedasoftware.masterscrobbler.model.ScrobbleInfo
import com.kanedasoftware.masterscrobbler.model.TrackInfo
import com.kanedasoftware.masterscrobbler.model.UpdateNowPlayingInfo
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.Utils
import com.kanedasoftware.masterscrobbler.ws.LastFmInitializer
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit

class NotificationServiceReceiver : BroadcastReceiver() {

    private var context: Context? = null
    private var preferences: SharedPreferences? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        val extras = intent?.extras
        if (extras != null) {
            val scrobbleBean = ScrobbleBean(
                    extras.getString("artist", ""),
                    extras.getString("track", ""),
                    extras.getLong("postTime", 0))

            this.context = context
            this.preferences = PreferenceManager.getDefaultSharedPreferences(context)

            if (!scrobbleBean.artist.isBlank() && !scrobbleBean.track.isBlank()) {
                validateTrackAndArtist(scrobbleBean)
            }
        }
    }

    private fun validateTrackAndArtist(scrobbleBean: ScrobbleBean) {
        LastFmInitializer().lastFmService().validateTrackAndArtist(scrobbleBean.artist, scrobbleBean.track,
                Constants.API_KEY).enqueue(object : Callback<TrackInfo> {
            override fun onResponse(call: Call<TrackInfo>, response: Response<TrackInfo>) {
                val trackList = response.body()?.results?.trackmatches?.track
                val listSize = trackList?.size
                if (listSize != null && listSize > 0) {
                    val mbid = trackList[0].mbid
                    val image = trackList[0].image[0].text

                    if (mbid.isBlank() && image.isBlank()) {
                        Utils.logDebug("MBID e imagem não existente, assume que a música não existe, vai tentar validar só pelo nome da música")
                        validateOnlyByTrack(scrobbleBean)
                    } else {
                        //Atualiza o artista e música com os valores validados no Last.FM e insere o MBID
                        scrobbleBean.artist = trackList[0].artist
                        scrobbleBean.track = trackList[0].name
                        scrobbleBean.mbid = mbid
                        getFullTrackInfo(scrobbleBean)
                    }
                } else {
                    Utils.logDebug("Não foi possível encontrar a música " + scrobbleBean.artist + " - " + scrobbleBean.track + " no Last.FM")
                }
            }

            override fun onFailure(call: Call<TrackInfo>, t: Throwable) {
                //TODO implementar tratamento de erro
                if (preferences!!.getBoolean("debug", false)) {
                    updateNotification("Erro Validate", t.localizedMessage)
                }
                Utils.logDebug(t.localizedMessage)
            }
        })
    }

    private fun validateOnlyByTrack(scrobbleBean: ScrobbleBean) {
        LastFmInitializer().lastFmService().validateTrack(scrobbleBean.track, Constants.API_KEY).enqueue(object : Callback<TrackInfo> {
            override fun onResponse(call: Call<TrackInfo>, response: Response<TrackInfo>) {
                val trackList = response.body()?.results?.trackmatches?.track
                val listSize = trackList?.size

                if (listSize != null && listSize > 0) {
                    val mbid = trackList[0].mbid
                    val image = trackList[0].image[0].text

                    if (mbid.isBlank() && image.isBlank()) {
                        updateNotification("Music Not Found MBID", trackList[0].artist.plus(" - ").plus(trackList[0].name))
                        Utils.logDebug("MBID e imagem não encontrados, assume que a música não existe, não será feito o scrobble")
                    } else {
                        //Atualiza o artista e música com os valores validados no Last.FM e insere o MBID
                        scrobbleBean.artist = trackList[0].artist
                        scrobbleBean.track = trackList[0].name
                        scrobbleBean.mbid = mbid
                        getFullTrackInfo(scrobbleBean)
                    }
                } else {
                    updateNotification("Music Not Found", scrobbleBean.artist.plus(" - ").plus(scrobbleBean.track))
                    Toast.makeText(context, "Não foi possível encontrar a música " + scrobbleBean.track + " no Last.FM", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<TrackInfo>, t: Throwable) {
                //TODO implementar tratamento de erro
                if (preferences!!.getBoolean("debug", false)) {
                    updateNotification("Erro - Validate by Track", t.localizedMessage)
                }
                Toast.makeText(context, t.localizedMessage, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun getFullTrackInfo(scrobbleBean: ScrobbleBean) {
        LastFmInitializer().lastFmService().fullTrackInfo(scrobbleBean.mbid, Constants.API_KEY).enqueue(object : Callback<FullTrackInfo> {
            override fun onResponse(call: Call<FullTrackInfo>, response: Response<FullTrackInfo>) {
                //Atualiza a duração da música
                val duration = response.body()?.track?.duration
                if (!duration.isNullOrBlank()) {
                    scrobbleBean.trackDuration = duration?.toLong()!!
                } else {
                    scrobbleBean.trackDuration = 60000
                }
                Utils.logDebug("Duração: ".plus(TimeUnit.MILLISECONDS.toMinutes(scrobbleBean.trackDuration)))
                updateNowPlaying(scrobbleBean)
            }

            override fun onFailure(call: Call<FullTrackInfo>, t: Throwable) {
                //TODO implementar tratamento de erro
                if (preferences!!.getBoolean("debug", false)) {
                    updateNotification("Erro Full Track Info", t.localizedMessage)
                }
            }
        })
    }

    private fun updateNowPlaying(scrobbleBean: ScrobbleBean) {
        val sessionKey = PreferenceManager.getDefaultSharedPreferences(context).getString("sessionKey", "")
        val params = mutableMapOf("track" to scrobbleBean.track, "artist" to scrobbleBean.artist, "sk" to sessionKey)
        val sig = Utils.getSig(params, Constants.API_UPDATE_NOW_PLAYING)

        LastFmInitializer().lastFmService().updateNowPlaying(scrobbleBean.artist, scrobbleBean.track,
                Constants.API_KEY, sig, sessionKey!!).enqueue(object : Callback<UpdateNowPlayingInfo> {
            override fun onResponse(call: Call<UpdateNowPlayingInfo>, response: Response<UpdateNowPlayingInfo>) {
                Utils.logDebug("Atualizou faixa tocando agora: ".plus(scrobbleBean.artist).plus(" - ").plus(scrobbleBean.track))
                validateScrobble(scrobbleBean)
            }

            override fun onFailure(call: Call<UpdateNowPlayingInfo>, t: Throwable) {
                //TODO implementar tratamento de erro
                if (preferences!!.getBoolean("debug", false)) {
                    updateNotification("Update Now Playing", t.localizedMessage)
                }
            }
        })
    }

    private fun validateScrobble(scrobbleBean: ScrobbleBean) {
        val handler = Handler()
        val runnable = Runnable {
            val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audioManager.isMusicActive) {
                if (preferences?.getLong("postTime", 0) == scrobbleBean.postTime) {
                    scrobble(scrobbleBean)
                } else {
                    Utils.logDebug("A notificação ativa não é a mesma notificação, não será feito o validateScrobble")
                }
            } else {
                Utils.logDebug("Música não ativa, não será feito o validateScrobble")
            }
        }
        updateNotification("Scrobbling", scrobbleBean.artist.plus(" - ").plus(scrobbleBean.track))

        //Assume metade do tempo da execução da música para executar o validateScrobble.
        // Se o Last.fm não retornar a informação da duração, faz o validateScrobble em 30 segundos.
        if (scrobbleBean.trackDuration <= 30000) {
            Utils.logDebug("Música muito curta, não será realizado o scrobble")
        } else {
            handler.postDelayed(runnable, scrobbleBean.trackDuration / 2)
        }
    }

    private fun scrobble(scrobbleBean: ScrobbleBean) {
        val sessionKey = PreferenceManager.getDefaultSharedPreferences(context).getString("sessionKey", "")
        val timestamp = (scrobbleBean.postTime.div(1000)).toString()
        val paramsScrobble = mutableMapOf("track" to scrobbleBean.track, "artist" to scrobbleBean.artist, "sk" to sessionKey!!, "timestamp" to timestamp)
        val sig = Utils.getSig(paramsScrobble, Constants.API_TRACK_SCROBBLE)

        LastFmInitializer().lastFmService().scrobble(scrobbleBean.artist, scrobbleBean.track, Constants.API_KEY, sig, sessionKey, timestamp)
                .enqueue(object : Callback<ScrobbleInfo> {
                    override fun onResponse(call: Call<ScrobbleInfo>, response: Response<ScrobbleInfo>) {
                        Utils.logDebug("Scrobble Corrected: ".plus(scrobbleBean.artist).plus(" - ").plus(scrobbleBean.track))
                        updateNotification("Scrobelled", scrobbleBean.artist.plus(" - ").plus(scrobbleBean.track))
                    }

                    override fun onFailure(call: Call<ScrobbleInfo>, t: Throwable) {
                        //TODO implementar tratamento de erro
                        if (preferences!!.getBoolean("debug", false)) {
                            updateNotification("Erro Scrobble", t.localizedMessage)
                        }
                    }
                })
    }

    private fun updateNotification(title: String, text: String) {
        val notification = context?.let {
            Utils.buildNotification(it, title, text)
        }
        val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIFICATION_ID, notification)
    }
}