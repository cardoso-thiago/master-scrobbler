package com.kanedasoftware.masterscrobbler.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.preference.PreferenceManager
import android.widget.Toast
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

    override fun onReceive(context: Context?, intent: Intent?) {
        val extras = intent?.extras
        val artist = extras?.getString("artist")
        val track = extras?.getString("track")
        val postTime = extras?.getLong("postTime")
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        if (track != null && artist != null) {
            //Valida se a música e o artista existem no Last.FM fazendo uma busca através da API
            LastFmInitializer().lastFmService().validateTrack(Constants.API_KEY, track, artist).enqueue(object : Callback<TrackInfo> {
                //Resposta com sucesso da busca
                override fun onResponse(call: Call<TrackInfo>, response: Response<TrackInfo>) {
                    val trackInfo = response.body()
                    //Obtém a lista com os resultados
                    val trackList = trackInfo?.results?.trackmatches?.track
                    //Pega o tamanho da lista
                    val listSize = trackList?.size
                    //Se a lista for diferente de null e o tamanho maior que 0, assume que a música eciste
                    if (listSize != null && listSize > 0) {
                        //Pega o primeiro item da busca, pois na chamada foi limitado para retornar só um resultado no máximo
                        val validatedArtist = trackList[0].artist
                        val validatedTrack = trackList[0].name

                        //TODO remover o Toast
                        Toast.makeText(context, validatedArtist + " - " + validatedTrack, Toast.LENGTH_LONG).show()

                        //Obtém a chave da sessão do usuário
                        val sessionKey = PreferenceManager.getDefaultSharedPreferences(context).getString("sessionKey", "")
                        //Cria os parâmetros para o update do NowPlaying
                        val params = mutableMapOf("track" to validatedTrack, "artist" to validatedArtist, "sk" to sessionKey)
                        //Obtém a assinatura para a API
                        val sig = Utils.getSig(params, Constants.API_UPDATE_NOW_PLAYING)

                        //Atualiza a música que está tocando
                        LastFmInitializer().lastFmService().updateNowPlaying(Constants.API_KEY, validatedTrack, validatedArtist, sig, sessionKey).enqueue(object : Callback<UpdateNowPlayingInfo> {

                            override fun onResponse(call: Call<UpdateNowPlayingInfo>, response: Response<UpdateNowPlayingInfo>) {
                                Utils.logDebug("Atualizou faixa tocando agora: ".plus(validatedArtist).plus(" - ").plus(validatedTrack))

                                val handler = Handler()
                                val runnable = Runnable {
                                    val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                    if (audioManager.isMusicActive) {
                                        if (preferences.getLong("postTime", 0) == postTime) {
                                            val timestamp = (postTime / 1000).toString()
                                            val paramsScrobble = mutableMapOf("track" to validatedTrack, "artist" to validatedArtist, "sk" to sessionKey, "timestamp" to timestamp)
                                            val sigScrobble = Utils.getSig(paramsScrobble, Constants.API_TRACK_SCROBBLE)
                                            LastFmInitializer().lastFmService().scrobble(Constants.API_KEY, validatedTrack, validatedArtist, sigScrobble, sessionKey, timestamp).enqueue(object : Callback<ScrobbleInfo> {
                                                override fun onResponse(call: Call<ScrobbleInfo>, response: Response<ScrobbleInfo>) {
                                                    val scrobble = response.body()?.scrobbles?.scrobble
                                                    Utils.logDebug("Scrobble Corrected: ".plus(scrobble?.artist?.text).plus(" - ").plus(scrobble?.track?.text))
                                                }

                                                override fun onFailure(call: Call<ScrobbleInfo>, t: Throwable) {
                                                    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                                                }
                                            })

                                        } else {
                                            Utils.logDebug("A notificação ativa não é a mesma notificação, não será feito o scrobble")
                                        }
                                    } else {
                                        Utils.logDebug("Música não ativa, não será feito o scrobble")
                                    }
                                }
                                handler.postDelayed(runnable, 30000)
                            }

                            override fun onFailure(call: Call<UpdateNowPlayingInfo>, t: Throwable) {
                                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                            }
                        })
                    } else {
                        Toast.makeText(context, "Não foi possível encontrar a música " + artist + " - " + track + " no Last.FM", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<TrackInfo>, t: Throwable) {
                    //TODO implementar tratamento de erro
                    Toast.makeText(context, t.localizedMessage, Toast.LENGTH_LONG).show()
                }

            })
        }
    }
}