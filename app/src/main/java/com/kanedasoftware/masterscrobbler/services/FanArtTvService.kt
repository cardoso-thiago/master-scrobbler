package com.kanedasoftware.masterscrobbler.services

import com.kanedasoftware.masterscrobbler.model.FanArtTvArtistInfo
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FanArtTvService {
    @GET("{mbid}?&format=json")
    fun getArtistInfo(@Path("mbid") mbid: String, @Query("api_key") apiKey: String): Call<FanArtTvArtistInfo>
}