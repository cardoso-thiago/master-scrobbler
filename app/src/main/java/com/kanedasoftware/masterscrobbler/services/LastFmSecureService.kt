package com.kanedasoftware.masterscrobbler.services

import com.kanedasoftware.masterscrobbler.models.LoginInfo
import retrofit2.Call
import retrofit2.http.POST
import retrofit2.http.Query

interface LastFmSecureService {

    @POST("?format=json")
    fun getMobileSession(@Query("password") password: String, @Query("username") username: String,
                         @Query("api_key") apiKey: String, @Query("api_sig") apiSig: String,
                         @Query("method") method: String): Call<LoginInfo>
}