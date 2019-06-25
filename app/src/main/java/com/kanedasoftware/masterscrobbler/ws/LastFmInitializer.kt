package com.kanedasoftware.masterscrobbler.ws

import com.kanedasoftware.masterscrobbler.services.LastFmSecureService
import com.kanedasoftware.masterscrobbler.services.LastFmService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LastFmInitializer {

    private val retrofit = Retrofit.Builder()
            .baseUrl("http://ws.audioscrobbler.com/2.0/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    private val secureRetrofit = Retrofit.Builder()
            .baseUrl("https://ws.audioscrobbler.com/2.0/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    fun lastFmService():LastFmService = retrofit.create(LastFmService::class.java)

    fun lastFmSecureService(): LastFmSecureService = secureRetrofit.create(LastFmSecureService::class.java)
}