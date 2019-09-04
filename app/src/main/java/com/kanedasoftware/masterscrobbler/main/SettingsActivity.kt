package com.kanedasoftware.masterscrobbler.main

import android.os.Bundle
import com.jaredrummler.cyanea.app.CyaneaAppCompatActivity

class SettingsActivity : CyaneaAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
    }
}
