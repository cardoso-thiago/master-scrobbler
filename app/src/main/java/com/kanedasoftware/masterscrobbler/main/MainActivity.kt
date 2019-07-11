package com.kanedasoftware.masterscrobbler.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.picasso.CircleTransform
import com.kanedasoftware.masterscrobbler.services.MediaService
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.Utils
import com.kanedasoftware.masterscrobbler.ws.LastFmInitializer
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import com.kanedasoftware.masterscrobbler.adapters.GridViewTopAdapter
import com.kanedasoftware.masterscrobbler.enums.EnumArtistsAlbums
import com.kanedasoftware.masterscrobbler.enums.EnumPeriod

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        if (!Utils.verifyNotificationAccess(this)) {
            Utils.changeNotificationAccess(this)
        } else {
            val i = Intent(applicationContext, MediaService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext?.startForegroundService(i)
            } else {
                applicationContext?.startService(i)
            }
        }

        getSessionKey()
        getUserInfo()
        getTopAlbums(EnumPeriod.WEEK)

        val artistsAlbumsSpinner = findViewById<Spinner>(R.id.top_artists_albuns)
        val artistsAlbumsAdapter = ArrayAdapter<EnumArtistsAlbums>(this, R.layout.spinner_item_artist_album, EnumArtistsAlbums.values())
        artistsAlbumsAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        artistsAlbumsSpinner.adapter = artistsAlbumsAdapter
        artistsAlbumsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                //Do nothing
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            }
        }

        val periodSpinner = findViewById<Spinner>(R.id.period)
        val periodAdapter = ArrayAdapter<EnumPeriod>(this, R.layout.spinner_item_period, EnumPeriod.values())
        periodAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        periodSpinner.adapter = periodAdapter
        periodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                //Do nothing
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val period = parent?.getItemAtPosition(position) as EnumPeriod
                getTopAlbums(period)
            }
        }
    }

    private fun getTopAlbums(period: EnumPeriod) {
        doAsync {
            //TODO pegar o usuário logado
            //TODO tratar conexão ativa e/ou try/catch
            val response = LastFmInitializer().lastFmService().topAlbums("brownstein666",
                    period.id, Constants.API_KEY).execute()
            if (response.isSuccessful) {
                uiThread {
                    val albums = response.body()?.topalbums?.album
                    val gv = findViewById<GridView>(R.id.grid_view)
                    val list = ArrayList<String>()
                    if (albums != null) {
                        for (album in albums) {
                            list.add(album.image.last().text)
                        }
                    }
                    gv.adapter = GridViewTopAdapter(applicationContext, list)
                }
            }
        }
    }

    private fun getUserInfo() {
        val profile = findViewById<ImageView>(R.id.profile)
        val username = findViewById<TextView>(R.id.username)
        val info = findViewById<TextView>(R.id.info)
        doAsync {
            //TODO pegar usuário logado
            //TODO tratar conexão ativa e/ou try/catch
            val response = LastFmInitializer().lastFmService().userInfo("brownstein666", Constants.API_KEY).execute()
            if (response.isSuccessful) {
                val profileUrl = response.body()?.user?.image?.last()?.text
                val name = response.body()?.user?.name
                val realName = response.body()?.user?.realname
                val registered = response.body()?.user?.registered?.text
                uiThread {
                    Picasso.get().load(profileUrl).transform(CircleTransform()).into(profile)
                    username.text = name
                    if (registered != null) {
                        info.text = "$realName • scrobbling since ${Utils.getDateTimeFromEpoch(registered)}"
                    } else {
                        info.text = realName
                    }
                }
            }
        }
    }

    private fun getSessionKey() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        var sessionKey = preferences.getString("sessionKey", "")

        //TODO voltar para valiação isBlank depois de tratar o invalid session key
        if (sessionKey.isNullOrBlank()) {
            //TODO pegar login e senha do usuário
            val params = mutableMapOf("password" to "Fennec@147", "username" to "brownstein666")
            val sig = Utils.getSig(params, Constants.API_GET_MOBILE_SESSION)

            if (Utils.isConnected(this)) {
                doAsync {
                    //TODO tratar erro visualmente para o usuário, verificar tratamentos para erros diversos
                    val response = LastFmInitializer().lastFmSecureService().getMobileSession("Fennec@147", "brownstein666",
                            Constants.API_KEY, sig, "auth.getMobileSession").execute()
                    if (!response.isSuccessful) {
                        Utils.logDebug("Logando o erro da obtenção do session key para tentar capturar situações: ${response.code()}", applicationContext)
                    }
                    sessionKey = response.body()?.session?.key.toString()
                    //TODO verificar melhor maneira de armazenar a sessionkey
                    preferences.edit().putString("sessionKey", sessionKey).apply()
                }
            } else {
                Utils.logError("Conexão necessária para obter o id da sessão do usuário.", applicationContext)
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        if (!Utils.verifyNotificationAccess(this)) {
            Utils.changeNotificationAccess(this)
        } else {
            val i = Intent(applicationContext, MediaService::class.java)
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
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
