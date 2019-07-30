package com.kanedasoftware.masterscrobbler.main

import android.app.Application
import com.kanedasoftware.masterscrobbler.R
import io.multimoon.colorful.Defaults
import io.multimoon.colorful.ThemeColor
import io.multimoon.colorful.initColorful

class CustomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaults = Defaults(
                primaryColor = ThemeColor.RED,
                accentColor = ThemeColor.GREY,
                useDarkTheme = false,
                translucent = false,
                customTheme = R.style.AppTheme)
        initColorful(this, defaults)
    }
}