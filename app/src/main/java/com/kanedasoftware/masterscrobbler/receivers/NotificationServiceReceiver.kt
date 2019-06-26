package com.kanedasoftware.masterscrobbler.receivers

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

class NotificationServiceReceiver : BroadcastReceiver() {

    private var scrobbleBean = ScrobbleBean()
    private var context: Context? = null
    private var preferences: SharedPreferences? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        Utils.logDebug("onReceive")
        val extras = intent?.extras
        if (extras != null) {
            this.scrobbleBean.artist = extras.getString("artist", "")
            this.scrobbleBean.track = extras.getString("track", "")
            this.scrobbleBean.postTime = extras.getLong("postTime", 0)

            this.context = context
            this.preferences = PreferenceManager.getDefaultSharedPreferences(context)

            if (!scrobbleBean.artist.isBlank() && !scrobbleBean.track.isBlank()) {
                Utils.logDebug("Artista e músicas OK")
                validateTrackAndArtist(scrobbleBean)
            }
        }
    }

    private fun validateTrackAndArtist(scrobbleBean: ScrobbleBean) {
        Utils.logDebug("Validate track")
        LastFmInitializer().lastFmService().validateTrackAndArtist(Constants.API_KEY, scrobbleBean.track, scrobbleBean.artist).enqueue(object : Callback<TrackInfo> {
            override fun onResponse(call: Call<TrackInfo>, response: Response<TrackInfo>) {
                Utils.logDebug("Validate track OK")
                val trackList = response.body()?.results?.trackmatches?.track
                val listSize = trackList?.size
                if (listSize != null && listSize > 0) {
                    val mbid = trackList[0].mbid

                    var validatedScrobbleBean = ScrobbleBean()
                    validatedScrobbleBean.artist = trackList[0].artist
                    validatedScrobbleBean.track = trackList[0].name
                    validatedScrobbleBean.postTime = scrobbleBean.postTime
                    validatedScrobbleBean.mbid = mbid

                    if (mbid.isBlank()) {
                        Utils.logDebug("MBID não encontrado, assume que a música não existe, vai tentar validar só pelo nome da música")
                        validateOnlyByTrack(scrobbleBean)
                    } else {
                        getFullTrackInfo(validatedScrobbleBean)
                    }
                } else {
                    Utils.logDebug("Não foi possível encontrar a música " + scrobbleBean.artist + " - " + scrobbleBean.track + " no Last.FM")
                }
            }

            override fun onFailure(call: Call<TrackInfo>, t: Throwable) {
                //TODO implementar tratamento de erro
                Utils.logDebug(t.localizedMessage)
            }
        })
    }

    private fun validateOnlyByTrack(scrobbleBean: ScrobbleBean) {
        LastFmInitializer().lastFmService().validateTrack(Constants.API_KEY, scrobbleBean.track).enqueue(object : Callback<TrackInfo> {
            override fun onResponse(call: Call<TrackInfo>, response: Response<TrackInfo>) {
                val trackList = response.body()?.results?.trackmatches?.track
                val listSize = trackList?.size

                if (listSize != null && listSize > 0) {
                    val mbid = trackList[0].mbid

                    var validatedScrobbleBean = ScrobbleBean()
                    validatedScrobbleBean.artist = trackList[0].artist
                    validatedScrobbleBean.track = trackList[0].name
                    validatedScrobbleBean.postTime = scrobbleBean.postTime
                    validatedScrobbleBean.mbid = mbid

                    if (mbid.isBlank()) {
                        Utils.logDebug("MBID não encontrado, assume que a música não existe, não será feito o updateNowPlaying")
                    } else {
                        getFullTrackInfo(validatedScrobbleBean)
                    }
                } else {
                    Toast.makeText(context, "Não foi possível encontrar a música " + scrobbleBean.track + " no Last.FM", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<TrackInfo>, t: Throwable) {
                //TODO implementar tratamento de erro
                Toast.makeText(context, t.localizedMessage, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun getFullTrackInfo(scrobbleBean: ScrobbleBean) {
        LastFmInitializer().lastFmService().fullTrackInfo(Constants.API_KEY, scrobbleBean.mbid).enqueue(object : Callback<FullTrackInfo> {
            override fun onResponse(call: Call<FullTrackInfo>, response: Response<FullTrackInfo>) {
                scrobbleBean.trackDuration = response.body()?.track?.duration.toString()
                updateNowPlaying(scrobbleBean)
            }

            override fun onFailure(call: Call<FullTrackInfo>, t: Throwable) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        })
    }

    private fun updateNowPlaying(scrobbleBean: ScrobbleBean) {
        val sessionKey = PreferenceManager.getDefaultSharedPreferences(context).getString("sessionKey", "")
        val params = mutableMapOf("track" to scrobbleBean.track, "artist" to scrobbleBean.artist, "sk" to sessionKey)
        val sig = Utils.getSig(params, Constants.API_UPDATE_NOW_PLAYING)

        LastFmInitializer().lastFmService().updateNowPlaying(Constants.API_KEY, scrobbleBean.track, scrobbleBean.artist, sig, sessionKey).enqueue(object : Callback<UpdateNowPlayingInfo> {
            override fun onResponse(call: Call<UpdateNowPlayingInfo>, response: Response<UpdateNowPlayingInfo>) {
                Utils.logDebug("Atualizou faixa tocando agora: ".plus(scrobbleBean.artist).plus(" - ").plus(scrobbleBean.track))
                scrobble(scrobbleBean)
            }
            override fun onFailure(call: Call<UpdateNowPlayingInfo>, t: Throwable) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        })
    }

    private fun scrobble(scrobbleBean:ScrobbleBean) {
        val sessionKey = PreferenceManager.getDefaultSharedPreferences(context).getString("sessionKey", "")
        val handler = Handler()
        val runnable = Runnable {
            val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audioManager.isMusicActive) {
                if (preferences?.getLong("postTime", 0) == scrobbleBean.postTime) {
                    val timestamp = (scrobbleBean.postTime?.div(1000)).toString()
                    val paramsScrobble = mutableMapOf("track" to scrobbleBean.track, "artist" to scrobbleBean.artist, "sk" to sessionKey, "timestamp" to timestamp)
                    val sigScrobble = Utils.getSig(paramsScrobble, Constants.API_TRACK_SCROBBLE)
                    LastFmInitializer().lastFmService().scrobble(Constants.API_KEY, scrobbleBean.track, scrobbleBean.artist, sigScrobble, sessionKey, timestamp)
                            .enqueue(object : Callback<ScrobbleInfo> {
                        override fun onResponse(call: Call<ScrobbleInfo>, response: Response<ScrobbleInfo>) {
                            val scrobble = response.body()?.scrobbles?.scrobble
                            //TODO pegar informação corrigida da maneira certa pra logar
                            Utils.logDebug("Scrobble Corrected: ".plus(scrobble?.artist?.text).plus(" - ").plus(scrobble?.track?.text))
                        }

                        override fun onFailure(call: Call<ScrobbleInfo>, t: Throwable) {
                            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                        }
                    })

                } else {
                    Utils.logDebug("A notificação ativa não é a mesma notificação, não será feito o updateNowPlaying")
                }
            } else {
                Utils.logDebug("Música não ativa, não será feito o updateNowPlaying")
            }
        }

        Utils.logDebug("Duration: ".plus(scrobbleBean.trackDuration))
        if (scrobbleBean.trackDuration.isBlank()) {
            handler.postDelayed(runnable, 30000)
        } else {
            val longDuration = scrobbleBean.trackDuration?.toLong()
            handler.postDelayed(runnable, longDuration / 2)
        }
    }
}