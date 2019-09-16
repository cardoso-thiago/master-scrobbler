package com.kanedasoftware.masterscrobbler.adapters

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.jaredrummler.cyanea.Cyanea
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.beans.RecentBean
import com.kanedasoftware.masterscrobbler.components.SquaredImageView
import com.squareup.picasso.Picasso
import io.gresse.hugo.vumeterlibrary.VuMeterView
import java.util.*

internal class ViewHolder {
    var image: SquaredImageView? = null
    var track: TextView? = null
    var artist: TextView? = null
    var timestamp: TextView? = null
    var icon: ImageView? = null
    var equalizer: VuMeterView? = null
}

class ListViewTrackAdapter(context: Context, private val beanList: ArrayList<RecentBean>) :
        ArrayAdapter<RecentBean>(context, R.layout.list_recent_tracks, beanList) {

    override fun getView(position: Int, view: View?, parent: ViewGroup): View? {
        var rowView = view
        val viewHolder: ViewHolder
        if (rowView == null) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            rowView = inflater.inflate(R.layout.list_recent_tracks, parent, false)

            viewHolder = ViewHolder()
            viewHolder.image = rowView.findViewById(R.id.item_list_image) as SquaredImageView
            viewHolder.track = rowView.findViewById(R.id.item_list_track) as TextView
            viewHolder.artist = rowView.findViewById(R.id.item_list_artist) as TextView
            viewHolder.timestamp = rowView.findViewById(R.id.item_list_timestamp) as TextView
            viewHolder.icon = rowView.findViewById(R.id.item_list_icon) as ImageView
            viewHolder.equalizer = rowView.findViewById(R.id.item_list_equalizer) as VuMeterView
            rowView.tag = viewHolder
        } else {
            viewHolder = rowView.tag as ViewHolder
        }

        viewHolder.track?.text = beanList[position].track
        viewHolder.artist?.text = beanList[position].artist
        viewHolder.timestamp?.text = beanList[position].timestamp

        if(Cyanea.instance.isDark){
            viewHolder.track?.setTextColor(ContextCompat.getColor(context, R.color.white))
            viewHolder.artist?.setTextColor(ContextCompat.getColor(context, R.color.white))
            viewHolder.timestamp?.setTextColor(ContextCompat.getColor(context, R.color.white))
        }

        val albumImageUrl = beanList[position].imageUrl
        if (albumImageUrl.isBlank()) {
            Picasso.get().load(R.drawable.ic_placeholder).fit().tag(context).into(viewHolder.image)
        } else {
            Picasso.get().load(albumImageUrl).placeholder(R.drawable.ic_placeholder).error(R.drawable.ic_placeholder).fit().tag(context).into(viewHolder.image)
        }

        val icon = viewHolder.icon
        val equalizer = viewHolder.equalizer
        when {
            beanList[position].scrobbling -> icon?.visibility = View.GONE
            beanList[position].loved -> {
                equalizer?.visibility = View.GONE
                icon?.setImageResource(R.drawable.ic_heart)
            }
            else -> {
                equalizer?.visibility = View.GONE
                icon?.visibility = View.GONE
            }
        }

        //TODO colocar opção para abrir no navegador direto nas configurações
        //TODO tratamento para possível url vazia
        rowView?.setOnClickListener {
            val builder = CustomTabsIntent.Builder()
            val customTabsIntent = builder.build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, Uri.parse(beanList[position].lastFmUrl))
        }

        return rowView
    }
}