package com.kanedasoftware.masterscrobbler.ws

import android.content.Context
import com.kanedasoftware.masterscrobbler.services.LastFmSecureService
import com.kanedasoftware.masterscrobbler.services.LastFmService
import com.kanedasoftware.masterscrobbler.utils.Utils
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitInitializer(context: Context) {

    private val cacheSize = (5 * 1024 * 1024).toLong()
    private val myCache = Cache(context.cacheDir, cacheSize)

    private val okHttpClient = OkHttpClient.Builder()
            .cache(myCache)
            .addInterceptor { chain ->
                var request = chain.request()
                request = if (Utils.isConnected(context))
                    request.newBuilder().header("Cache-Control", "public, max-age=" + 5).build()
                else
                    request.newBuilder().header("Cache-Control", "public, only-if-cached, max-stale=" + 60 * 60 * 24 * 7).build()
                chain.proceed(request)
            }
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