package com.kanedasoftware.masterscrobbler.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.beans.RecentBean
import com.kanedasoftware.masterscrobbler.components.SquaredImageView
import com.squareup.picasso.Picasso
import java.util.*

internal class ViewHolder {
    var albumImage: SquaredImageView? = null
    var track: TextView? = null
    var albumName: TextView? = null
    var timestamp: TextView? = null
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
            viewHolder.albumImage = rowView.findViewById(R.id.item_list_album_image) as SquaredImageView
            viewHolder.track = rowView.findViewById(R.id.item_list_track) as TextView
            viewHolder.albumName = rowView.findViewById(R.id.item_list_album_name) as TextView
            viewHolder.timestamp = rowView.findViewById(R.id.item_list_timestamp) as TextView
            rowView.tag = viewHolder
        } else {
            viewHolder = rowView.tag as ViewHolder
        }

        viewHolder.track?.text = beanList[position].track
        viewHolder.albumName?.text = beanList[position].albumName
        viewHolder.timestamp?.text = beanList[position].timestamp

        val albumImageUrl = beanList[position].albumImageUrl
        if (albumImageUrl.isBlank()) {
            Picasso.get().load(R.drawable.placeholder).fit().tag(context).into(viewHolder.albumImage)
        } else {
            Picasso.get().load(albumImageUrl).placeholder(R.drawable.placeholder).error(R.drawable.placeholder).fit().tag(context).into(viewHolder.albumImage)
        }
        return rowView
    }
}