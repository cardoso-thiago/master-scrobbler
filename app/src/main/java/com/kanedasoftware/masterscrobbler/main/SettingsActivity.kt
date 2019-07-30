package com.kanedasoftware.masterscrobbler.main

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import com.kanedasoftware.masterscrobbler.R
import io.multimoon.colorful.CAppCompatActivity


class SettingsActivity : CAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupActionBar()
        supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
    }

    private fun setupActionBar() {
        val rootView = findViewById<View>(R.id.action_bar_root) as ViewGroup
        val view = layoutInflater.inflate(R.layout.app_bar_layout, rootView, false)
        rootView.addView(view, 0)
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }
}
