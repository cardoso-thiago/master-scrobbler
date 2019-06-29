package com.kanedasoftware.masterscrobbler

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import com.kanedasoftware.masterscrobbler.model.LoginInfo
import com.kanedasoftware.masterscrobbler.services.NotificationService
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.Utils
import com.kanedasoftware.masterscrobbler.ws.LastFmInitializer
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        if(!Utils.verifyNotificationAccess(this)){
            Utils.changeNotificationAccess(this)
        } else {
            val i = Intent(applicationContext, NotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext?.startForegroundService(i)
            } else {
                applicationContext?.startService(i)
            }
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        //Ativar modo debug de notificação
        preferences.edit().putBoolean("debug", true).apply()

        var sessionKey = preferences.getString("sessionKey", "")

        //TODO voltar para valiação isBlank depois de tratar o invalid session key
        if (sessionKey.isNullOrBlank()) {
            //TODO pegar login e senha do usuário
            val params = mutableMapOf("password" to "Fennec@147", "username" to "brownstein666")
            val sig = Utils.getSig(params, Constants.API_GET_MOBILE_SESSION)

            Utils.logInfo(sig)

            LastFmInitializer().lastFmSecureService()
                    .getMobileSession("Fennec@147", "brownstein666", Constants.API_KEY,
                            sig, "auth.getMobileSession").enqueue(object : Callback<LoginInfo> {
                        override fun onResponse(call: Call<LoginInfo>, response: Response<LoginInfo>) {
                            sessionKey = response.body()?.session?.key.toString()
                            //TODO remover esse log
                            Utils.logInfo(sessionKey)
                            preferences.edit().putString("sessionKey", sessionKey).apply()
                        }

                        override fun onFailure(call: Call<LoginInfo>, t: Throwable) {
                            //TODO tratamento de erro
                            Utils.logInfo("Erro ao obter o session key".plus(t.localizedMessage))
                        }

                    })
        }

    }

    override fun onRestart() {
        super.onRestart()
        if(!Utils.verifyNotificationAccess(this)){
            Utils.changeNotificationAccess(this)
        } else {
            val i = Intent(applicationContext, NotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext?.startForegroundService(i)
            } else {
                applicationContext?.startService(i)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
