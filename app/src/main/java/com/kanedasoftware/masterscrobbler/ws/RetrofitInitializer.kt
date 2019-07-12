package com.kanedasoftware.masterscrobbler.ws

import com.kanedasoftware.masterscrobbler.services.FanArtTvService
import com.kanedasoftware.masterscrobbler.services.LastFmSecureService
import com.kanedasoftware.masterscrobbler.services.LastFmService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitInitializer {

    private val lastFm = Retrofit.Builder()
            .baseUrl("http://ws.audioscrobbler.com/2.0/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    private val secureLastFm = Retrofit.Builder()
            .baseUrl("https://ws.audioscrobbler.com/2.0/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    private val fanArtTv = Retrofit.Builder()
            .baseUrl("https://webservice.fanart.tv/v3/music/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    fun lastFmService():LastFmService = lastFm.create(LastFmService::class.java)

    fun lastFmSecureService(): LastFmSecureService = secureLastFm.create(LastFmSecureService::class.java)

    fun fanArtTvService(): FanArtTvService = fanArtTv.create(FanArtTvService::class.java)
}