package com.kanedasoftware.masterscrobbler.adapters

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import butterknife.BindView
import butterknife.ButterKnife
import com.bumptech.glide.Glide
import com.github.ybq.android.spinkit.SpinKitView
import com.google.android.material.snackbar.Snackbar
import com.jaredrummler.cyanea.Cyanea
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.beans.Recent
import com.kanedasoftware.masterscrobbler.components.SquaredImageView
import com.kanedasoftware.masterscrobbler.services.LastFmService
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.ImageUtils
import com.kanedasoftware.masterscrobbler.utils.Utils
import de.adorsys.android.securestoragelibrary.SecurePreferences
import io.gresse.hugo.vumeterlibrary.VuMeterView
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*

internal class ViewHolder(view: View) {
    //ButterKnife
    @BindView(R.id.item_list_image)
    lateinit var image: SquaredImageView

    @BindView(R.id.item_list_track)
    lateinit var track: TextView

    @BindView(R.id.item_list_artist)
    lateinit var artist: TextView

    @BindView(R.id.item_list_timestamp)
    lateinit var timestamp: TextView

    @BindView(R.id.item_list_icon)
    lateinit var icon: ImageView

    @BindView(R.id.item_list_equalizer)
    lateinit var equalizer: VuMeterView

    @BindView(R.id.list_item_spin)
    lateinit var spin: SpinKitView

    init {
        ButterKnife.bind(this, view)
    }
}

class ListViewTrackAdapter(context: Context, private val list: ArrayList<Recent>) :
        ArrayAdapter<Recent>(context, R.layout.list_recent_tracks, list), KoinComponent {

    private val utils: Utils by inject()
    private val imageUtils: ImageUtils by inject()
    private val lastFmService: LastFmService by inject()

    override fun getView(position: Int, view: View?, parent: ViewGroup): View? {
        var rowView = view
        val viewHolder: ViewHolder
        if (rowView == null) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            rowView = inflater.inflate(R.layout.list_recent_tracks, parent, false)

            viewHolder = ViewHolder(rowView)
            rowView.tag = viewHolder
        } else {
            viewHolder = rowView.tag as ViewHolder
        }

        viewHolder.track.text = list[position].track
        viewHolder.artist.text = list[position].artist
        viewHolder.timestamp.text = list[position].timestamp

        if(Cyanea.instance.isDark){
            viewHolder.track.setTextColor(ContextCompat.getColor(context, R.color.white))
            viewHolder.artist.setTextColor(ContextCompat.getColor(context, R.color.white))
            viewHolder.timestamp.setTextColor(ContextCompat.getColor(context, R.color.white))
        }

        val albumImageUrl = list[position].imageUrl
        if (albumImageUrl.isBlank()) {
            Glide.with(context).load(R.drawable.ic_placeholder).fitCenter().into(viewHolder.image)
        } else {
            imageUtils.getImageCache(albumImageUrl, viewHolder.image as ImageView)
        }

        val icon = viewHolder.icon
        val equalizer = viewHolder.equalizer
        val spin = viewHolder.spin
        spin.visibility = View.GONE

        when {
            list[position].scrobbling -> icon.visibility = View.GONE
            list[position].loved -> {
                equalizer.visibility = View.GONE
                icon.setImageResource(R.drawable.ic_heart)
            }
            else -> {
                equalizer.visibility = View.GONE
                if (Cyanea.instance.isDark) {
                    icon.setImageResource(R.drawable.ic_empty_heart_white)
                } else {
                    icon.setImageResource(R.drawable.ic_empty_heart)
                }
            }
        }

        rowView?.setOnClickListener{
            icon.visibility = View.GONE
            spin.visibility = View.VISIBLE

            val listItem = list[position]
            if(!listItem.scrobbling){
                if(listItem.loved){
                    doAsync {
                        val sessionKey = SecurePreferences.getStringValue(context, Constants.SECURE_SESSION_TAG, "")
                        if (sessionKey != null) {
                            val params = mutableMapOf("track" to listItem.track, "artist" to listItem.artist, "sk" to sessionKey)
                            val sig = utils.getSig(params, Constants.API_TRACK_UNLOVE)
                            val response = lastFmService.unlove(listItem.artist, listItem.track, Constants.API_KEY, sig, sessionKey).execute()
                            uiThread {
                                icon.visibility = View.VISIBLE
                                spin.visibility = View.GONE

                                if (response.isSuccessful) {
                                    if (Cyanea.instance.isDark) {
                                        viewHolder.icon.setImageResource(R.drawable.ic_empty_heart_white)
                                    } else {
                                        viewHolder.icon.setImageResource(R.drawable.ic_empty_heart)
                                    }
                                    listItem.loved = false
                                } else {
                                    Snackbar.make(rowView, context.getString(R.string.error_love_unlove_action), Snackbar.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                } else {
                    doAsync {
                        val sessionKey = SecurePreferences.getStringValue(context, Constants.SECURE_SESSION_TAG, "")
                        if (sessionKey != null) {
                            val params = mutableMapOf("track" to listItem.track, "artist" to listItem.artist, "sk" to sessionKey)
                            val sig = utils.getSig(params, Constants.API_TRACK_LOVE)
                            val response = lastFmService.love(listItem.artist, listItem.track, Constants.API_KEY, sig, sessionKey).execute()
                            uiThread {
                                icon.visibility = View.VISIBLE
                                spin.visibility = View.GONE

                                if (response.isSuccessful) {
                                    viewHolder.icon.setImageResource(R.drawable.ic_heart)
                                    listItem.loved = true
                                } else {
                                    Snackbar.make(rowView, context.getString(R.string.error_love_unlove_action), Snackbar.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }

        //TODO colocar opção para abrir no navegador direto nas configurações
        //TODO tratamento para possível url vazia
        viewHolder.image.setOnClickListener {
            val builder = CustomTabsIntent.Builder()
            val customTabsIntent = builder.build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, Uri.parse(list[position].lastFmUrl))
        }

        return rowView
    }
}