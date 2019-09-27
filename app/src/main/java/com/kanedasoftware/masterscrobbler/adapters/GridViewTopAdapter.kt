package com.kanedasoftware.masterscrobbler.adapters

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.beans.TopBean
import com.kanedasoftware.masterscrobbler.components.SquaredImageView
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.Utils
import java.util.*

internal class GridViewTopAdapter(private val context: Context, beanList: ArrayList<TopBean>, type: String) : BaseAdapter() {

    private val list = beanList
    private val gridType = type

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
        val gridViewAndroid: View
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        if (convertView == null) {
            if (gridType == Constants.ARTISTS) {
                gridViewAndroid = inflater.inflate(R.layout.gridview_artist_layout, parent, false)
                val textArtist = gridViewAndroid.findViewById<View>(R.id.gridview_artist_text) as TextView
                textArtist.text = list[position].text1

                val textPlays = gridViewAndroid.findViewById<View>(R.id.gridview_artist_plays) as TextView
                textPlays.text = list[position].text2

                val imageViewAndroid = gridViewAndroid.findViewById<View>(R.id.gridview_artist_image) as SquaredImageView
                imageViewAndroid.setOnClickListener {
                    val builder = CustomTabsIntent.Builder()
                    val customTabsIntent = builder.build()
                    customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    customTabsIntent.launchUrl(context, Uri.parse(list[position].lastFmUrl))
                }
                val url = list[position].url
                if (!url.isBlank()) {
                    Utils.getImageCache(url, imageViewAndroid)
                }
            } else {
                gridViewAndroid = inflater.inflate(R.layout.gridview_album_layout, parent, false)
                val textAlbum = gridViewAndroid.findViewById<View>(R.id.gridview_album_text) as TextView
                textAlbum.text = list[position].text1

                val textAlbumArtist = gridViewAndroid.findViewById<View>(R.id.gridview_album_artist) as TextView
                textAlbumArtist.text = list[position].text2

                val textAlbumPlays = gridViewAndroid.findViewById<View>(R.id.gridview_album_plays) as TextView
                textAlbumPlays.text = list[position].text3

                val imageViewAndroid = gridViewAndroid.findViewById<View>(R.id.gridview_album_image) as SquaredImageView
                imageViewAndroid.setOnClickListener {
                    val builder = CustomTabsIntent.Builder()
                    val customTabsIntent = builder.build()
                    customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    customTabsIntent.launchUrl(context, Uri.parse(list[position].lastFmUrl))
                }
                val url = list[position].url
                if (!url.isBlank()) {
                    Utils.getImageCache(url, imageViewAndroid)
                }
            }
        } else {
            gridViewAndroid = convertView
        }

        return gridViewAndroid
    }

    override fun getCount(): Int {
        return list.size
    }

    override fun getItem(position: Int): String? {
        return null
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }
}