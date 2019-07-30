package com.kanedasoftware.masterscrobbler.main

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import butterknife.BindView
import butterknife.ButterKnife
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.adapters.GridViewTopAdapter
import com.kanedasoftware.masterscrobbler.adapters.ListViewTrackAdapter
import com.kanedasoftware.masterscrobbler.beans.RecentBean
import com.kanedasoftware.masterscrobbler.beans.TopBean
import com.kanedasoftware.masterscrobbler.enums.EnumArtistsAlbums
import com.kanedasoftware.masterscrobbler.enums.EnumPeriod
import com.kanedasoftware.masterscrobbler.enums.EnumTopType
import com.kanedasoftware.masterscrobbler.picasso.CircleTransformation
import com.kanedasoftware.masterscrobbler.services.MediaService
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.Utils
import com.kanedasoftware.masterscrobbler.ws.RetrofitInitializer
import com.squareup.picasso.Picasso
import de.adorsys.android.securestoragelibrary.SecurePreferences
import io.multimoon.colorful.CAppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class MainActivity : CAppCompatActivity() {
    @JvmField
    @BindView(R.id.profile)
    var profile: ImageView? = null
    @JvmField
    @BindView(R.id.username)
    var username: TextView? = null
    @JvmField
    @BindView(R.id.info)
    var info: TextView? = null
    @JvmField
    @BindView(R.id.list_tracks)
    var listTracks: ListView? = null
    @JvmField
    @BindView(R.id.grid_view)
    var gridView: GridView? = null
    @JvmField
    @BindView(R.id.top_artists_albuns)
    var artistsAlbumsSpinner: Spinner? = null
    @JvmField
    @BindView(R.id.period)
    var periodSpinner: Spinner? = null

    private var lastArtistsAlbumsSpinner = ""
    private var lastPeriodSpinner = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)
        setSupportActionBar(toolbar)

        val user: String? = SecurePreferences.getStringValue(applicationContext, Constants.SECURE_USER_TAG, "")
        if (Utils.isValidSessionKey(applicationContext)) {
            if (user != null) {
                initService()
                getUserInfo(user)
                initSpinners(user)
                getRecentTracks(user)
            }
        } else {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        fab.setOnClickListener {
            if (Utils.isValidSessionKey(applicationContext)) {
                if (user != null) {
                    getRecentTracks(user)
                }
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        initService()
        if (!Utils.isValidSessionKey(applicationContext)) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!Utils.isValidSessionKey(applicationContext)) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    private fun initService() {
        if (!Utils.verifyNotificationAccess(this)) {
            Utils.changeNotificationAccess(this)
        } else {
            val i = Intent(applicationContext, MediaService::class.java)
            i.action = Constants.START_SERVICE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext?.startForegroundService(i)
            } else {
                applicationContext?.startService(i)
            }
        }
    }

    private fun stopService() {
        val i = Intent(applicationContext, MediaService::class.java)
        i.action = Constants.STOP_SERVICE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext?.startForegroundService(i)
        } else {
            applicationContext?.startService(i)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                return true
            }
            R.id.action_Logoff -> {
                SecurePreferences.clearAllValues(applicationContext)
                applicationContext.defaultSharedPreferences.edit().clear().apply()
                stopService()
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getUserInfo(user: String) {
        Utils.log("User Info", applicationContext)
        doAsync {
            val response = RetrofitInitializer(applicationContext).lastFmService().userInfo(user, Constants.API_KEY).execute()
            if (response.isSuccessful) {
                val profileUrl = response.body()?.user?.image?.last()?.text
                val name = response.body()?.user?.name
                var realName = response.body()?.user?.realname
                if (realName.isNullOrEmpty()) {
                    realName = name
                }
                val registered = response.body()?.user?.registered?.text
                uiThread {
                    Picasso.get().load(profileUrl).transform(CircleTransformation()).into(profile)
                    username?.text = name
                    if (registered != null) {
                        info?.text = applicationContext.getString(R.string.scrobbling_since, realName, Utils.getDateTimeFromEpoch(registered))
                    } else {
                        info?.text = realName
                    }
                }
            }
        }
    }

    private fun initSpinners(user: String) {
        if (artistsAlbumsSpinner != null) {
            val artistsAlbumsAdapter = ArrayAdapter<EnumArtistsAlbums>(this, R.layout.spinner_item_artist_album, EnumArtistsAlbums.values())
            artistsAlbumsAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            artistsAlbumsSpinner!!.adapter = artistsAlbumsAdapter
            artistsAlbumsSpinner!!.setSelection(getSharedPreferences("Spinners", Context.MODE_PRIVATE).getInt("artistsAlbums", 0))

            artistsAlbumsSpinner!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    //Do nothing
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    getSharedPreferences("Spinners", Context.MODE_PRIVATE).edit().putInt("artistsAlbums", position).apply()
                    val artistImage = parent?.getItemAtPosition(position) as EnumArtistsAlbums
                    searchImages(user, artistImage, periodSpinner?.selectedItem as EnumPeriod)
                }
            }
        }

        if (periodSpinner != null) {
            val periodAdapter = ArrayAdapter<EnumPeriod>(this, R.layout.spinner_item_period, EnumPeriod.values())
            periodAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            periodSpinner!!.adapter = periodAdapter
            periodSpinner!!.setSelection(getSharedPreferences("Spinners", Context.MODE_PRIVATE).getInt("period", 0))


            periodSpinner!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    //Do nothing
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    getSharedPreferences("Spinners", Context.MODE_PRIVATE).edit().putInt("period", position).apply()
                    val period = parent?.getItemAtPosition(position) as EnumPeriod
                    searchImages(user, artistsAlbumsSpinner?.selectedItem as EnumArtistsAlbums, period)
                }
            }
        }
    }

    private fun getRecentTracks(user: String) {
        Utils.log("Recent Tracks", applicationContext)
        doAsync {
            val recentTracksList = ArrayList<RecentBean>()
            val response = RetrofitInitializer(applicationContext).lastFmService().recentTracks(user, Constants.API_KEY).execute()
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
                            if(imageUrl.isBlank()){
                                imageUrl = "https://tse2.mm.bing.net/th?q=${track.artist.name} ${track.name} Album&w=500&h=500&c=7&rs=1&p=0&dpr=3&pid=1.7&mkt=en-IN&adlt=on"
                            }
                            timestamp = Utils.convertUTCToLocal(track.date.text)
                            loved = track.loved == "1"
                        }
                        recentTracksList.add(RecentBean(imageUrl, name, artist, timestamp, loved, scrobbling))
                    }
                }
            }
            uiThread {
                if (listTracks != null) {
                    val listAdapter = ListViewTrackAdapter(applicationContext, recentTracksList)
                    listTracks!!.adapter = listAdapter
                    Utils.setListViewHeightBasedOnItems(listTracks!!)
                }
            }
        }
    }

    private fun searchImages(user: String, artistAlbum: EnumArtistsAlbums, period: EnumPeriod) {
        if (artistAlbum.id == lastArtistsAlbumsSpinner && period.id == lastPeriodSpinner) {
            Utils.log("Não houve mudança, não vai carregar de novo o grid", applicationContext)
        } else {
            lastArtistsAlbumsSpinner = artistAlbum.id
            lastPeriodSpinner = period.id
            if (artistAlbum == EnumArtistsAlbums.ARTISTS) {
                getTopArtists(user, period)
            } else {
                getTopAlbums(user, period)
            }
        }
    }

    private fun getTopArtists(user: String, period: EnumPeriod) {
        doAsync {
            Utils.log("Top Artists Grid", applicationContext)
            val response = RetrofitInitializer(applicationContext).lastFmService().topArtists(user,
                    period.id, Constants.API_KEY).execute()
            if (response.isSuccessful) {
                val artists = response.body()?.topartists?.artist
                val list = ArrayList<TopBean>()
                if (artists != null) {
                    for (artist in artists) {
                        val url = "https://tse2.mm.bing.net/th?q=${artist.name} Band&w=500&h=500&c=7&rs=1&p=0&dpr=3&pid=1.7&mkt=en-IN&adlt=on"
                        val topBean = TopBean(url, artist.name, artist.playcount.plus(" plays"))
                        list.add(topBean)
                    }
                }
                uiThread {
                    if (gridView != null) {
                        gridView!!.adapter = GridViewTopAdapter(applicationContext, list, EnumTopType.ARTIST)
                    }
                }
            }
        }
    }

    private fun getTopAlbums(user: String, period: EnumPeriod) {
        Utils.log("Top Albums Grid", applicationContext)
        doAsync {
            val response = RetrofitInitializer(applicationContext).lastFmService().topAlbums(user,
                    period.id, Constants.API_KEY).execute()
            if (response.isSuccessful) {
                val albums = response.body()?.topalbums?.album
                val list = ArrayList<TopBean>()
                if (albums != null) {
                    for (album in albums) {
                        var imageUrl = album.image.last().text
                        if(imageUrl.isBlank()){
                            imageUrl = "https://tse2.mm.bing.net/th?q=${album.artist.name} ${album.name} Album&w=500&h=500&c=7&rs=1&p=0&dpr=3&pid=1.7&mkt=en-IN&adlt=on"
                        }
                        val topBean = TopBean(imageUrl, album.name, album.artist.name)
                        topBean.text3 = album.playcount.plus(" plays")
                        list.add(topBean)
                    }
                }
                uiThread {
                    if (gridView != null) {
                        gridView!!.adapter = GridViewTopAdapter(applicationContext, list, EnumTopType.ALBUM)
                    }
                }
            }
        }
    }
}