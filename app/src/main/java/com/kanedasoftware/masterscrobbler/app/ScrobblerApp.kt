package com.kanedasoftware.masterscrobbler.app

import android.content.Context
import com.jaredrummler.cyanea.CyaneaApp
import com.kanedasoftware.masterscrobbler.utils.Utils
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ScrobblerApp: CyaneaApp() {

    private lateinit var okHttpClient:OkHttpClient


    override fun onCreate() {
        super.onCreate()

        okHttpClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    var request = chain.request()
                    request = if (Utils.isConnected())
                        request.newBuilder().header("Cache-Control", "public, max-age=" + 5).build()
                    else
                        request.newBuilder().header("Cache-Control", "public, only-if-cached, max-stale=" + 60 * 60 * 24 * 7).build()
                    chain.proceed(request)
                }
                .cache(Cache(applicationContext.cacheDir, Long.MAX_VALUE))
                .build()

        val builder = Picasso.Builder(this)
        builder.downloader(OkHttp3Downloader(okHttpClient))
        val built = builder.build()
        built.setIndicatorsEnabled(true)
        built.isLoggingEnabled = true
        Picasso.setSingletonInstance(built)

        setContext(this)
    }

    companion object {
        private lateinit var mContext: ScrobblerApp

        fun setContext(context: ScrobblerApp) {
            mContext = context
        }

        fun getContext(): Context {
            return mContext.applicationContext
        }
    }
}
