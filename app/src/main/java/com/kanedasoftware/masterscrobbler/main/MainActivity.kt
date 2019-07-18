package com.kanedasoftware.masterscrobbler.main

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.adapters.GridViewTopAdapter
import com.kanedasoftware.masterscrobbler.adapters.ListViewTrackAdapter
import com.kanedasoftware.masterscrobbler.beans.RecentBean
import com.kanedasoftware.masterscrobbler.beans.TopBean
import com.kanedasoftware.masterscrobbler.components.WrappedGridView
import com.kanedasoftware.masterscrobbler.enums.EnumArtistsAlbums
import com.kanedasoftware.masterscrobbler.enums.EnumPeriod
import com.kanedasoftware.masterscrobbler.enums.EnumTopType
import com.kanedasoftware.masterscrobbler.picasso.CircleTransformation
import com.kanedasoftware.masterscrobbler.services.MediaService
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.Utils
import com.kanedasoftware.masterscrobbler.ws.RetrofitInitializer
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
        initService()
        getSessionKey()
        getUserInfo()
        initSpinners()
        getRecentTracks()
    }

    override fun onRestart() {
        super.onRestart()
        initService()
    }

    private fun initService() {
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
                    val response = RetrofitInitializer().lastFmSecureService().getMobileSession("Fennec@147", "brownstein666",
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

    private fun getUserInfo() {
        val profile = findViewById<ImageView>(R.id.profile)
        val username = findViewById<TextView>(R.id.username)
        val info = findViewById<TextView>(R.id.info)
        doAsync {
            //TODO pegar usuário logado
            //TODO tratar conexão ativa e/ou try/catch
            val response = RetrofitInitializer().lastFmService().userInfo("brownstein666", Constants.API_KEY).execute()
            if (response.isSuccessful) {
                val profileUrl = response.body()?.user?.image?.last()?.text
                val name = response.body()?.user?.name
                val realName = response.body()?.user?.realname
                val registered = response.body()?.user?.registered?.text
                uiThread {
                    Picasso.get().load(profileUrl).transform(CircleTransformation()).into(profile)
                    username.text = name
                    if (registered != null) {
                        info.text = applicationContext.getString(R.string.scrobbling_since, realName, Utils.getDateTimeFromEpoch(registered))
                    } else {
                        info.text = realName
                    }
                }
            }
        }
    }

    private fun initSpinners() {
        val artistsAlbumsSpinner = findViewById<Spinner>(R.id.top_artists_albuns)
        val artistsAlbumsAdapter = ArrayAdapter<EnumArtistsAlbums>(this, R.layout.spinner_item_artist_album, EnumArtistsAlbums.values())
        artistsAlbumsAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        artistsAlbumsSpinner.adapter = artistsAlbumsAdapter

        val periodSpinner = findViewById<Spinner>(R.id.period)
        val periodAdapter = ArrayAdapter<EnumPeriod>(this, R.layout.spinner_item_period, EnumPeriod.values())
        periodAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        periodSpinner.adapter = periodAdapter

        artistsAlbumsSpinner.setSelection(getSharedPreferences("Spinners", Context.MODE_PRIVATE).getInt("artistsAlbums", 0))
        periodSpinner.setSelection(getSharedPreferences("Spinners", Context.MODE_PRIVATE).getInt("period", 0))

        artistsAlbumsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                //Do nothing
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                getSharedPreferences("Spinners", Context.MODE_PRIVATE).edit().putInt("artistsAlbums", position).apply()
                val artistImage = parent?.getItemAtPosition(position) as EnumArtistsAlbums
                searchImages(artistImage, periodSpinner.selectedItem as EnumPeriod)
            }
        }

        periodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                //Do nothing
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                getSharedPreferences("Spinners", Context.MODE_PRIVATE).edit().putInt("period", position).apply()
                val period = parent?.getItemAtPosition(position) as EnumPeriod
                searchImages(artistsAlbumsSpinner.selectedItem as EnumArtistsAlbums, period)
            }
        }
    }

    private fun getRecentTracks() {
        doAsync {
            //TODO pegar usuário logado
            //TODO colocar nos settings quantas músicas vai exibir
            //TOOD tratar now playing
            val recentTracksList = ArrayList<RecentBean>()
            val response = RetrofitInitializer().lastFmService().recentTracks("brownstein666", Constants.API_KEY).execute()
            Utils.log(RetrofitInitializer().lastFmService().recentTracks("brownstein666", Constants.API_KEY).request().url().toString(), applicationContext)
            if (response.isSuccessful) {
                val tracks = response.body()?.recenttracks?.track
                if (tracks != null) {
                    for (track in tracks) {
                        val name = track.name
                        val artist = track.artist.name
                        var imageUrl: String
                        var timestamp: String
                        var loved = false
                        var scrobbling = false
                        if (track.attr != null && track.attr.nowplaying.toBoolean()) {
                            imageUrl = "https://tse2.mm.bing.net/th?q=${track.artist.name} Band&w=500&h=500&c=7&rs=1&p=0&dpr=3&pid=1.7&mkt=en-IN&adlt=on"
                            timestamp = "Scrobbling now"
                            scrobbling = true
                        } else {
                            imageUrl = track.image.last().text
                            timestamp = track.date.text
                            loved = track.loved == "1"
                        }
                        recentTracksList.add(RecentBean(imageUrl, name, artist, timestamp, loved, scrobbling))
                    }
                }
            }
            uiThread {
                val listTracks = findViewById<ListView>(R.id.list_tracks)
                val listAdapter = ListViewTrackAdapter(applicationContext, recentTracksList)
                listTracks.adapter = listAdapter
                Utils.setListViewHeightBasedOnItems(listTracks)
            }
        }
    }

    private fun searchImages(artistAlbum: EnumArtistsAlbums, period: EnumPeriod) {
        if (artistAlbum == EnumArtistsAlbums.ARTISTS) {
            getTopArtists(period)
        } else {
            getTopAlbums(period)
        }
    }

    private fun getTopArtists(period: EnumPeriod) {
        doAsync {
            //TODO pegar o usuário logado
            //TODO tratar conexão ativa e/ou try/catch
            Utils.log(RetrofitInitializer().lastFmService().topArtists("brownstein666", period.id, Constants.API_KEY).request().url().toString(), applicationContext)
            val response = RetrofitInitializer().lastFmService().topArtists("brownstein666",
                    period.id, Constants.API_KEY).execute()
            if (response.isSuccessful) {
                val artists = response.body()?.topartists?.artist
                val list = ArrayList<TopBean>()
                if (artists != null) {
                    for (artist in artists) {
                        val url = "https://tse2.mm.bing.net/th?q=${artist.name} Band&w=500&h=500&c=7&rs=1&p=0&dpr=3&pid=1.7&mkt=en-IN&adlt=on"
                        //TODO pegar o texto "plays" do resources
                        val topBean = TopBean(url, artist.name, artist.playcount.plus(" plays"))
                        list.add(topBean)
                    }
                }
                uiThread {
                    val gv = findViewById<WrappedGridView>(R.id.grid_view)
                    gv.adapter = GridViewTopAdapter(applicationContext, list, EnumTopType.ARTIST)
                }
            }
        }
    }

    private fun getTopAlbums(period: EnumPeriod) {
        doAsync {
            //TODO pegar o usuário logado
            //TODO tratar conexão ativa e/ou try/catch
            val response = RetrofitInitializer().lastFmService().topAlbums("brownstein666",
                    period.id, Constants.API_KEY).execute()
            if (response.isSuccessful) {
                val albums = response.body()?.topalbums?.album
                val list = ArrayList<TopBean>()
                if (albums != null) {
                    for (album in albums) {
                        val topBean = TopBean(album.image.last().text, album.name, album.artist.name)
                        //TODO pegar o texto "plays" do resources
                        topBean.text3 = album.playcount.plus(" plays")
                        list.add(topBean)
                    }
                }
                uiThread {
                    val gv = findViewById<WrappedGridView>(R.id.grid_view)
                    gv.adapter = GridViewTopAdapter(applicationContext, list, EnumTopType.ALBUM)
                }
            }
        }
    }
}
