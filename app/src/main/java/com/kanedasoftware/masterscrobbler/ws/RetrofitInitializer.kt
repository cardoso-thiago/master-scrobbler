package com.kanedasoftware.masterscrobbler.ws

import com.kanedasoftware.masterscrobbler.app.ScrobblerApp
import com.kanedasoftware.masterscrobbler.services.LastFmSecureService
import com.kanedasoftware.masterscrobbler.services.LastFmService
import com.kanedasoftware.masterscrobbler.utils.Utils
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RetrofitInitializer {

    private val cacheSize = (5 * 1024 * 1024).toLong()
    private val myCache = Cache(ScrobblerApp.getContext().cacheDir, cacheSize)

    private val okHttpClient = OkHttpClient.Builder()
            .cache(myCache)
            .addInterceptor { chain ->
                var request = chain.request()
                request = if (Utils.isConnected())
                    request.newBuilder().header("Cache-Control", "public, max-age=" + 5).build()
                else
                    request.newBuilder().header("Cache-Control", "public, only-if-cached, max-stale=" + 60 * 60 * 24 * 7).build()
                chain.proceed(request)
            }
            .cache(Cache(ScrobblerApp.getContext().cacheDir, Long.MAX_VALUE))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

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