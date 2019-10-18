package com.kanedasoftware.masterscrobbler.main

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import butterknife.BindView
import butterknife.ButterKnife
import com.google.android.material.snackbar.Snackbar
import com.jaredrummler.cyanea.Cyanea
import com.jaredrummler.cyanea.app.CyaneaAppCompatActivity
import com.jaredrummler.cyanea.prefs.CyaneaThemePickerActivity
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.adapters.GridViewTopAdapter
import com.kanedasoftware.masterscrobbler.adapters.ListViewTrackAdapter
import com.kanedasoftware.masterscrobbler.beans.Recent
import com.kanedasoftware.masterscrobbler.beans.TopInfo
import com.kanedasoftware.masterscrobbler.services.LastFmService
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.ImageUtils
import com.kanedasoftware.masterscrobbler.utils.NotificationUtils
import com.kanedasoftware.masterscrobbler.utils.Utils
import de.adorsys.android.securestoragelibrary.SecurePreferences
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.koin.android.ext.android.inject


class MainActivity : CyaneaAppCompatActivity() {

    //Koin
    private val utils: Utils by inject()
    private val imageUtils: ImageUtils by inject()
    private val notificationUtils: NotificationUtils by inject()
    private val lastFmService: LastFmService by inject()

    //ButterKnife
    @BindView(R.id.profile)
    lateinit var profile: ImageView

    @BindView(R.id.username)
    lateinit var username: TextView

    @BindView(R.id.info)
    lateinit var info: TextView

    @BindView(R.id.list_tracks)
    lateinit var listTracks: ListView

    @BindView(R.id.grid_view)
    lateinit var gridView: GridView

    @BindView(R.id.top_artists_albuns)
    lateinit var artistsAlbumsSpinner: Spinner

    @BindView(R.id.period)
    lateinit var periodSpinner: Spinner

    @BindView(R.id.text_recent_tracks)
    lateinit var recentTracks: TextView

    @BindView(R.id.swipe_container)
    lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private var lastArtistsAlbumsSpinner = ""
    private var lastPeriodSpinner = ""

    private var topArtists: String = ""
    private var topAlbums: String = ""
    private lateinit var valuesArtistsAlbums: List<String>

    private var sevenDays: String = ""
    private var oneMonth: String = ""
    private var threeMonths: String = ""
    private var sixMonths: String = ""
    private var oneYear: String = ""
    private var overall: String = ""
    private lateinit var valuesPeriods: List<String>
    private var listTopInfo = ArrayList<TopInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ButterKnife.bind(this)
        setSupportActionBar(toolbar)

        //Obtém os resources
        topArtists = getString(R.string.top_artists)
        topAlbums = getString(R.string.top_albums)
        valuesArtistsAlbums = mutableListOf(topArtists, topAlbums)

        sevenDays = getString(R.string.period_7day)
        oneMonth = getString(R.string.period_1month)
        threeMonths = getString(R.string.period_3month)
        sixMonths = getString(R.string.period_6month)
        oneYear = getString(R.string.period_12month)
        overall = getString(R.string.period_overall)
        valuesPeriods = mutableListOf(sevenDays, oneMonth, threeMonths, sixMonths, oneYear, overall)

        validateTheme()

        val user: String? = SecurePreferences.getStringValue(applicationContext, Constants.SECURE_USER_TAG, "")
        if (utils.isValidSessionKey()) {
            if (user != null) {
                initService()
                updateData(user)
            }
        } else {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        swipeRefreshLayout.setOnRefreshListener {
            updateData(user)
            swipeRefreshLayout.isRefreshing = false
        }

        fab_menu.setClosedOnTouchOutside(true)

        fab_update.setOnClickListener {
            lastArtistsAlbumsSpinner = ""
            lastPeriodSpinner = ""

            if(!utils.isConnected()) {
                val parentLayout = findViewById<View>(android.R.id.content)
                Snackbar.make(parentLayout, getString(R.string.no_connection), Snackbar.LENGTH_LONG).show()
            }
            updateData(user)
        }

        //TODO verificar se é a melhor maneira de obter a lista de imagens
        //TODO Verificar para obter a lista das imagens já baixadas cache do Picasso
        //TODO Se não, tratamento para offline
        //TODO Snack pra sucesso ou erro
        fab_wall.setOnClickListener{
            fab_menu.close(true)
            doAsync {
                val listBitmaps = mutableListOf<Bitmap>()
                for(item in listTopInfo) {
                    listBitmaps.add(imageUtils.resizeImage(item.url))
                }
                val finalBitmap = imageUtils.mergeMultiple(listBitmaps)
                val wallManager = WallpaperManager.getInstance(applicationContext)
                wallManager.setBitmap(finalBitmap)
                finalBitmap.recycle()
            }
        }
    }

    private fun updateData(user: String?) {
        if (utils.isValidSessionKey()) {
            if (user != null) {
                getUserInfo(user)
                initSpinners(user)
                getRecentTracks(user)
            }
        }
    }

    private fun startFabAnimation() {
        if(fab_menu.isOpened){
            val rotate = RotateAnimation(0f, 360f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
            )
            rotate.duration = 900
            rotate.repeatCount = Animation.INFINITE
            fab_update.startAnimation(rotate)
        }
    }

    private fun stopFabAnimation() {
        fab_update.clearAnimation()
        fab_menu.close(true)
    }

    private fun validateTheme() {
        if (Cyanea.instance.isDark) {
            username.setTextColor(ContextCompat.getColor(applicationContext, R.color.white))
            info.setTextColor(ContextCompat.getColor(applicationContext, R.color.white))
            recentTracks.setTextColor(ContextCompat.getColor(applicationContext, R.color.white))
        }
        if (Cyanea.instance.isActionBarDark) {
            toolbar.setTitleTextColor(ContextCompat.getColor(applicationContext, R.color.white))
            val colorFilter = PorterDuffColorFilter(ContextCompat.getColor(applicationContext, R.color.white), PorterDuff.Mode.MULTIPLY)
            toolbar.overflowIcon?.let { it.colorFilter = colorFilter }
        }
    }

    override fun onRestart() {
        super.onRestart()
        initService()
        if (!utils.isValidSessionKey()) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!utils.isValidSessionKey()) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    private fun initService() {
        notificationUtils.createQuietNotificationChannel(this)
        notificationUtils.createNotificationChannel(this)

        if (notificationUtils.verifyNotificationAccess()) {
            val defaultSharedPreferences = android.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
            if (utils.hasAppsToScrobble(defaultSharedPreferences)) {
                utils.startMediaService()
            } else {
                notificationUtils.sendNoPlayerNotification()
            }
        } else {
            notificationUtils.changeNotificationAccess(this)
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
            R.id.action_theme_picker -> {
                val intent = Intent(this, CyaneaThemePickerActivity::class.java)
                startActivity(intent)
                return true
            }
            R.id.action_logoff -> {
                SecurePreferences.clearAllValues(applicationContext)
                applicationContext.defaultSharedPreferences.edit().clear().apply()
                utils.stopMediaService()

                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getUserInfo(user: String) {
        doAsync {
            val response = lastFmService.userInfo(user, Constants.API_KEY).execute()
            if (response.isSuccessful) {
                val profileUrl = response.body()?.user?.image?.last()?.text
                val name = response.body()?.user?.name
                var realName = response.body()?.user?.realname
                if (realName.isNullOrEmpty()) {
                    realName = name
                }
                val registered = response.body()?.user?.registered?.text
                uiThread {
                    if (profileUrl != null) {
                        profile.let { profileImage -> imageUtils.getAvatarImage(profileUrl, profileImage) }
                    }
                    username.text = name
                    if (registered != null) {
                        info.text = applicationContext.getString(R.string.scrobbling_since, realName, utils.getDateTimeFromEpoch(registered))
                    } else {
                        info.text = realName
                    }
                }
            }
        }
    }

    private fun initSpinners(user: String) {
        startFabAnimation()
        var artistsAlbumsAdapter = ArrayAdapter(this, R.layout.spinner_item_artist_album, valuesArtistsAlbums)
        artistsAlbumsAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        if (Cyanea.instance.isDark) {
            artistsAlbumsAdapter = ArrayAdapter(this, R.layout.spinner_item_artist_album_dark, valuesArtistsAlbums)
            artistsAlbumsAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        }
        artistsAlbumsSpinner.adapter = artistsAlbumsAdapter
        artistsAlbumsSpinner.setSelection(getSharedPreferences("Spinners", Context.MODE_PRIVATE).getInt("artistsAlbums", 0))

        artistsAlbumsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                //Do nothing
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                getSharedPreferences("Spinners", Context.MODE_PRIVATE).edit().putInt("artistsAlbums", position).apply()
                val artistImage = parent?.getItemAtPosition(position).toString()
                searchImages(user, artistImage, periodSpinner.selectedItem.toString())
            }
        }

        var periodAdapter = ArrayAdapter(this, R.layout.spinner_item_period, valuesPeriods)
        periodAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        if (Cyanea.instance.isDark) {
            periodAdapter = ArrayAdapter(this, R.layout.spinner_item_period_dark, valuesPeriods)
            periodAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        }
        periodSpinner.adapter = periodAdapter
        periodSpinner.setSelection(getSharedPreferences("Spinners", Context.MODE_PRIVATE).getInt("period", 0))


        periodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                //Do nothing
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                getSharedPreferences("Spinners", Context.MODE_PRIVATE).edit().putInt("period", position).apply()
                val period = parent?.getItemAtPosition(position).toString()
                searchImages(user, artistsAlbumsSpinner.selectedItem.toString(), period)
            }
        }
    }

    private fun getRecentTracks(user: String) {
        doAsync {
            val recentTracksList = ArrayList<Recent>()
            val response = lastFmService.recentTracks(user, Constants.API_KEY).execute()
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
                        val lastFmUrl = track.url
                        if (track.attr != null && track.attr.nowplaying.toBoolean()) {
                            imageUrl = "https://tse2.mm.bing.net/th?q=${track.artist.name} Band&w=500&h=500&c=7&rs=1&p=0&dpr=3&pid=1.7&mkt=en-IN&adlt=on"
                            timestamp = "Scrobbling now"
                            scrobbling = true
                        } else {
                            imageUrl = track.image.last().text
                            if (imageUrl.isBlank()) {
                                imageUrl = "https://tse2.mm.bing.net/th?q=${track.artist.name} ${track.name} Album&w=500&h=500&c=7&rs=1&p=0&dpr=3&pid=1.7&mkt=en-IN&adlt=on"
                            }
                            timestamp = utils.convertUTCToLocal(track.date.text)
                            loved = track.loved == "1"
                        }
                        recentTracksList.add(Recent(imageUrl, name, artist, timestamp, loved, scrobbling, lastFmUrl))
                    }
                }
            }
            uiThread {
                val listAdapter = ListViewTrackAdapter(applicationContext, recentTracksList)
                listTracks.adapter = listAdapter
                utils.setListViewHeightBasedOnItems(listTracks)
                stopFabAnimation()
            }
        }
    }

    private fun searchImages(user: String, artistAlbum: String, period: String) {
        var count = 0
        if(gridView.adapter != null) {
            count = gridView.adapter?.count!!
        }
        if (artistAlbum == lastArtistsAlbumsSpinner && period == lastPeriodSpinner && count > 0) {
            utils.log("Não houve mudança, não vai carregar de novo o grid")
        } else {
            utils.log("Carregando a grid. Spinner: $artistAlbum - Period: $period - Count: $count")
            lastArtistsAlbumsSpinner = artistAlbum
            lastPeriodSpinner = period
            if (artistAlbum == topArtists) {
                getTopArtists(user, period)
            } else {
                getTopAlbums(user, period)
            }
        }
    }

    private fun getTopArtists(user: String, period: String) {
        doAsync {
            val response = lastFmService.topArtists(user,
                    utils.getPeriodParameter(period), Constants.API_KEY).execute()
            if (response.isSuccessful) {
                listTopInfo = ArrayList()
                val artists = response.body()?.topartists?.artist
                if (artists != null) {
                    for (artist in artists) {
                        val url = "https://tse2.mm.bing.net/th?q=${artist.name} Band&w=500&h=500&c=7&rs=1&p=0&dpr=3&pid=1.7&mkt=en-IN&adlt=on"
                        val topBean = TopInfo(url, artist.name, artist.playcount.plus(" plays"), artist.url)
                        listTopInfo.add(topBean)
                    }
                }
                uiThread {
                    gridView.adapter = GridViewTopAdapter(applicationContext, listTopInfo, Constants.ARTISTS)
                }
            }
        }
    }

    private fun getTopAlbums(user: String, period: String) {
        doAsync {
            val response = lastFmService.topAlbums(user,
                    utils.getPeriodParameter(period), Constants.API_KEY).execute()
            if (response.isSuccessful) {
                val albums = response.body()?.topalbums?.album
                listTopInfo = ArrayList()
                if (albums != null) {
                    for (album in albums) {
                        var imageUrl = album.image.last().text
                        if (imageUrl.isBlank()) {
                            imageUrl = "https://tse2.mm.bing.net/th?q=${album.artist.name} ${album.name} Album&w=500&h=500&c=7&rs=1&p=0&dpr=3&pid=1.7&mkt=en-IN&adlt=on"
                        }
                        val topBean = TopInfo(imageUrl, album.name, album.artist.name, album.url)
                        topBean.text3 = album.playcount.plus(" plays")
                        listTopInfo.add(topBean)
                    }
                }
                uiThread {
                    gridView.adapter = GridViewTopAdapter(applicationContext, listTopInfo, Constants.ALBUMS)
                }
            }
        }
    }
}