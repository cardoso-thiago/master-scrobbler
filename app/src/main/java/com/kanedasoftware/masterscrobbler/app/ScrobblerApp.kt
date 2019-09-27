package com.kanedasoftware.masterscrobbler.app

import android.content.Context
import com.jaredrummler.cyanea.CyaneaApp
import com.squareup.picasso.Picasso
import com.squareup.picasso.OkHttp3Downloader



class ScrobblerApp: CyaneaApp() {

    override fun onCreate() {
        super.onCreate()

        val builder = Picasso.Builder(this)
        builder.downloader(OkHttp3Downloader(this, Integer.MAX_VALUE.toLong()))
        val built = builder.build()
        built.setIndicatorsEnabled(true)
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
