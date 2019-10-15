package com.kanedasoftware.masterscrobbler.ws

import com.kanedasoftware.masterscrobbler.services.LastFmSecureService
import com.kanedasoftware.masterscrobbler.services.LastFmService
import com.kanedasoftware.masterscrobbler.utils.Utils
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.koin.core.KoinComponent
import org.koin.core.inject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RetrofitInitializer : KoinComponent {

    private val utils: Utils by inject()
    private val okHttpClient:OkHttpClient by inject()

    private val lastFm = Retrofit.Builder()
            .baseUrl("http://ws.audioscrobbler.com/2.0/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    private val secureLastFm = Retrofit.Builder()
            .baseUrl("https://ws.audioscrobbler.com/2.0/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    fun lastFmService(): LastFmService = lastFm.create(LastFmService::class.java)

    fun lastFmSecureService(): LastFmSecureService = secureLastFm.create(LastFmSecureService::class.java)
}