package com.kanedasoftware.masterscrobbler.services

import com.kanedasoftware.masterscrobbler.model.FullTrackInfo
import com.kanedasoftware.masterscrobbler.model.ScrobbleInfo
import com.kanedasoftware.masterscrobbler.model.TrackInfo
import com.kanedasoftware.masterscrobbler.model.UpdateNowPlayingInfo
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface LastFmService {

    @GET("?method=track.search&limit=1&format=json")
    fun validateTrackAndArtist(@Query("artist") artist: String, @Query("track") track: String,
                               @Query("api_key") apiKey: String): Call<TrackInfo>

    @GET("?method=track.search&limit=1&format=json")
    fun validateTrack(@Query("track") track: String, @Query("api_key") apiKey: String): Call<TrackInfo>

    @GET("?method=track.getInfo&limit=1&format=json")
    fun fullTrackInfo(@Query("mbid") mbid: String, @Query("api_key") apiKey: String): Call<FullTrackInfo>


    @POST("?method=track.updateNowPlaying&format=json")
    fun updateNowPlaying(@Query("artist") artist: String, @Query("track") track: String,
                         @Query("api_key") apiKey: String, @Query("api_sig") sig: String,
                         @Query("sk") sessionKey: String): Call<UpdateNowPlayingInfo>

    @POST("?method=track.scrobble&format=json")
    fun scrobble(@Query("artist") artist: String, @Query("track") track: String,
                 @Query("api_key") apiKey: String, @Query("api_sig") sig: String,
                 @Query("sk") sessionKey: String, @Query("timestamp") timestamp: String): Call<ScrobbleInfo>
}