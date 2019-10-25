package com.kanedasoftware.masterscrobbler.app

import androidx.preference.PreferenceManager
import androidx.room.Room
import com.jaredrummler.cyanea.Cyanea
import com.jaredrummler.cyanea.CyaneaApp
import com.jaredrummler.cyanea.CyaneaThemes
import com.jaredrummler.cyanea.prefs.CyaneaTheme
import com.kanedasoftware.masterscrobbler.db.ScrobbleDb
import com.kanedasoftware.masterscrobbler.utils.ImageUtils
import com.kanedasoftware.masterscrobbler.utils.NotificationUtils
import com.kanedasoftware.masterscrobbler.utils.Utils
import com.kanedasoftware.masterscrobbler.ws.RetrofitInitializer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

class ScrobblerApp : CyaneaApp() {

    private val themesJsonAssetPath get() = "themes/cyanea_themes.json"

    private val applicationModules = module(override = true) {
        single {
            Room.databaseBuilder(androidContext(),
                    ScrobbleDb::class.java, "MasterScrobbler.db")
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build()
        }
        single { get<ScrobbleDb>().scrobbleDao() }
        single {
            OkHttpClient.Builder()
                    .cache(Cache(applicationContext.cacheDir, Long.MAX_VALUE))
                    .addInterceptor { chain ->
                        var request = chain.request()
                        request = if (utils.isConnected())
                            request.newBuilder().header("Cache-Control", "public, max-age=" + 5).build()
                        else
                            request.newBuilder().header("Cache-Control", "public, only-if-cached, max-stale=" + 60 * 60 * 24 * 7).build()
                        chain.proceed(request)
                    }
                    .cache(Cache(utils.getAppContext().cacheDir, Long.MAX_VALUE))
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build()
        }
        single { Utils(androidContext()) }
        single { NotificationUtils(androidContext()) }
        single { ImageUtils(androidContext()) }
        single { RetrofitInitializer().lastFmService() }
        single { RetrofitInitializer().lastFmSecureService() }
    }

    private val utils: Utils by inject()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@ScrobblerApp)
            modules(listOf(applicationModules))
        }

        if(utils.isFirstExecution()){
            CyaneaTheme.from(this.assets, themesJsonAssetPath)[0].apply(cyanea)
            utils.setNotFirstExecution()
        }
    }
}
