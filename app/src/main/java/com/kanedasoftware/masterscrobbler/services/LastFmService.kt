package com.kanedasoftware.masterscrobbler.services

import com.kanedasoftware.masterscrobbler.model.TrackInfo
import com.kanedasoftware.masterscrobbler.model.UpdateNowPlayingInfo
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface LastFmService {

    @GET("?method=track.search&limit=1&format=json")
    fun validateTrack(@Query("api_key") apiKey: String, @Query("track") track: String, @Query("artist") artist: String):Call<TrackInfo>

    @POST("?method=track.updateNowPlaying&format=json")
    fun updateNowPlaying(@Query("api_key") apiKey: String, @Query("track") track: String, @Query("artist") artist: String,
                         @Query("api_sig") sig: String, @Query("sk") sessionKey: String):Call<UpdateNowPlayingInfo>
}