package com.kanedasoftware.masterscrobbler.app

import android.content.Context
import com.jaredrummler.cyanea.CyaneaApp

class ScrobblerApp: CyaneaApp() {

    override fun onCreate() {
        super.onCreate()
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
