package com.kanedasoftware.masterscrobbler.adapters

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.beans.RecentBean
import com.kanedasoftware.masterscrobbler.picasso.SquaredImageView
import com.squareup.picasso.Picasso
import org.jetbrains.anko.doAsync
import java.util.*


class ListViewTrackAdapter(context: Context, private val beanList: ArrayList<RecentBean>) :
        ArrayAdapter<RecentBean>(context, R.layout.list_recent_tracks, beanList) {

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val rowView = inflater.inflate(R.layout.list_recent_tracks, null, true)

        val albumImage = rowView.findViewById(R.id.item_list_album_image) as SquaredImageView
        val track = rowView.findViewById(R.id.item_list_track) as TextView
        val albumName = rowView.findViewById(R.id.item_list_album_name) as TextView
        val timestamp = rowView.findViewById(R.id.item_list_timestamp) as TextView

        Picasso.get().load(beanList[position].albumImageUrl).placeholder(R.drawable.placeholder).error(R.drawable.placeholder).fit().tag(context).into(albumImage)
        track.text = beanList[position].track
        albumName.text = beanList[position].albumName
        timestamp.text = beanList[position].timestamp
        return rowView
    }
}