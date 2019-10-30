package com.kanedasoftware.masterscrobbler.main

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.OvershootInterpolator
import android.view.animation.RotateAnimation
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import butterknife.BindView
import butterknife.ButterKnife
import com.github.clans.fab.FloatingActionMenu
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
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.LibsBuilder
import de.adorsys.android.securestoragelibrary.SecurePreferences
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.koin.android.ext.android.inject
import java.util.*
import kotlin.collections.ArrayList

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

    @BindView(android.R.id.content)
    lateinit var parentLayout: View

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ButterKnife.bind(this)
        setSupportActionBar(toolbar)

        //Obtém os resources
        topArtists = getString(R.string.top_artists)
        topAlbums = getString(R.string.top_albums)
        valuesArtistsAlbums = mutableListOf(topAlbums, topArtists)

        sevenDays = getString(R.string.period_7day)
        oneMonth = getString(R.string.period_1month)
        threeMonths = getString(R.string.period_3month)
        sixMonths = getString(R.string.period_6month)
        oneYear = getString(R.string.period_12month)
        overall = getString(R.string.period_overall)
        valuesPeriods = mutableListOf(overall, oneYear, sixMonths, threeMonths, oneMonth, sevenDays)

        validateTheme()

        val user: String? = SecurePreferences.getStringValue(applicationContext, Constants.SECURE_USER_TAG, "")
        if (utils.isValidSessionKey()) {
            if (user != null) {
                profile.setOnClickListener {
                    utils.openUrl("https://www.last.fm/user/$user")
                }
                initService()
                updateData(user)
            }
        } else {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        swipeRefreshLayout.setOnRefreshListener {
            updateData(user)
        }

        fab_menu.setClosedOnTouchOutside(true)

        fab_update.setOnClickListener {
            lastArtistsAlbumsSpinner = ""
            lastPeriodSpinner = ""
            updateData(user)
        }

        fab_wall.setOnClickListener {
            fab_menu.close(true)
            doAsync {
                try {
                    val finalBitmap = getBitmapFromGrid(false)
                    val wallManager = WallpaperManager.getInstance(applicationContext)
                    wallManager.setBitmap(finalBitmap)

                    uiThread {
                        Snackbar.make(parentLayout, getString(R.string.applied_wallpaper), Snackbar.LENGTH_LONG).show()
                    }

                    finalBitmap.recycle()
                } catch (e: Exception) {
                    uiThread {
                        Snackbar.make(parentLayout, getString(R.string.error_wallpaper), Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }

        fab_share.setOnClickListener {
            fab_menu.close(true)
            doAsync {
                val finalBitmap = getBitmapFromGrid(true)
                val imageToShareUri = imageUtils.saveImageToShare(finalBitmap)
                if (imageToShareUri != null) {
                    uiThread {
                        var messageId = R.string.share_image_text
                        if (periodSpinner.selectedItem == getString(R.string.period_overall)) {
                            messageId = R.string.share_image_all_time_text
                        }
                        imageUtils.shareImage(imageToShareUri, getString(messageId,
                                artistsAlbumsSpinner.selectedItem.toString().toLowerCase(Locale.getDefault()),
                                periodSpinner.selectedItem.toString().toLowerCase(Locale.getDefault())))
                        finalBitmap.recycle()
                    }
                }
            }
        }
    }

    private fun getBitmapFromGrid(full: Boolean): Bitmap {
        val destSize = Resources.getSystem().displayMetrics.widthPixels / 3
        val listBitmaps = mutableListOf<Bitmap>()
        val topAdapter = gridView.adapter as GridViewTopAdapter

        for (item in topAdapter.getList()) {
            val futureBitmap = imageUtils.getBitmapSync(item.url, destSize)
            listBitmaps.add(futureBitmap.get())
        }

        return imageUtils.mergeMultiple(listBitmaps, full, destSize)
    }

    private fun updateData(user: String?) {
        if (!utils.isConnected()) {
            Snackbar.make(parentLayout, getString(R.string.no_connection), Snackbar.LENGTH_LONG).show()
        } else {
            if (utils.isValidSessionKey()) {
                if (user != null) {
                    utils.scrobblePendingMediaService()
                    getUserInfo(user)
                    initSpinners(user)
                    getRecentTracks(user)
                }
            }
        }
    }

    private fun startFabAnimation() {
        if (fab_menu.isOpened) {
            val rotate = RotateAnimation(0f, 360f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
            )
            rotate.duration = 900
            rotate.repeatCount = Animation.INFINITE
            fab_update.startAnimation(rotate)
        }
    }

    private fun stopAnimations() {
        fab_update.clearAnimation()
        fab_menu.close(true)
        swipeRefreshLayout.isRefreshing = false
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
            utils.startMediaService()
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
            R.id.action_about -> {
                LibsBuilder().withActivityStyle(Libs.ActivityStyle.LIGHT_DARK_TOOLBAR)
                        .withAboutIconShown(true)
                        .withAboutVersionShown(true)
                        .withAboutDescription(getString(R.string.about_description))
                        .start(this)
                return true
            }
            R.id.action_logoff -> {
                notificationUtils.cancelNoPlayerNotification()
                utils.stopMediaService()
                SecurePreferences.clearAllValues(applicationContext)
                applicationContext.defaultSharedPreferences.edit().clear().apply()
                utils.setNotFirstExecution()

                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
                            timestamp = utils.convertUTCToLocalPretty(track.date.text)
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
                stopAnimations()
            }
        }
    }

    private fun searchImages(user: String, artistAlbum: String, period: String) {
        if (artistAlbum == lastArtistsAlbumsSpinner && period == lastPeriodSpinner) {
            utils.log("Não houve mudança, não vai carregar de novo o grid")
        } else {
            utils.log("Carregando a grid. Spinner: $artistAlbum - Period: $period")
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
                val listTopInfo = ArrayList<TopInfo>()
                val artists = response.body()?.topartists?.artist
                if (artists != null) {
                    for (artist in artists) {
                        val url = "https://tse2.mm.bing.net/th?q=${artist.name} Band&w=500&h=500&c=7&rs=1&p=0&dpr=3&pid=1.7&mkt=en-IN&adlt=on"
                        val topBean = TopInfo(url, artist.name, artist.playcount.plus(" plays"), artist.url)
                        listTopInfo.add(topBean)
                    }
                }
                uiThread {
                    showArtistWarningDialog()
                    gridView.adapter = GridViewTopAdapter(applicationContext, listTopInfo, Constants.ARTISTS)
                }
            } else {
                snackDataError()
            }
        }
    }

    private fun showArtistWarningDialog() {
        if (showDialog()) {
            var style = R.style.Cyanea_AlertDialog_Theme_Light
            if (Cyanea.instance.isDark) {
                style = R.style.Cyanea_AlertDialog_Theme_Dark
            }
            val builder = AlertDialog.Builder(this@MainActivity, style)
            val view = View.inflate(this@MainActivity, R.layout.dialog_artists, null)
            val checkBox = view.findViewById<CheckBox>(R.id.checkBox)
            builder.setTitle(getString(R.string.warning_title))
            builder.setMessage(getString(R.string.warning_message))
            builder.setView(view)
            builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            builder.create().show()
            checkBox.setOnCheckedChangeListener { buttonView, _ ->
                if (buttonView.isChecked) {
                    setShowDialog(!buttonView.isChecked)
                }
            }
        }
    }

    private fun setShowDialog(showDialog: Boolean) = PreferenceManager.getDefaultSharedPreferences(applicationContext).edit().putBoolean("show_dialog", showDialog).apply()

    private fun showDialog() = PreferenceManager.getDefaultSharedPreferences(applicationContext).getBoolean("show_dialog", true)


    private fun getTopAlbums(user: String, period: String) {
        doAsync {
            val response = lastFmService.topAlbums(user,
                    utils.getPeriodParameter(period), Constants.API_KEY).execute()
            if (response.isSuccessful) {
                val albums = response.body()?.topalbums?.album
                val listTopInfo = ArrayList<TopInfo>()
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
            } else {
                snackDataError()
            }
        }
    }

    private fun snackDataError() {
        var snackMessage = R.string.error_loading_data
        if (!utils.isConnected()) {
            snackMessage = R.string.no_connection
        }
        Snackbar.make(parentLayout, getString(snackMessage), Snackbar.LENGTH_LONG).show()
    }
}