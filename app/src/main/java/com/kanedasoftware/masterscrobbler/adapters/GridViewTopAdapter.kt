package com.kanedasoftware.masterscrobbler.adapters

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import butterknife.BindView
import butterknife.ButterKnife
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.beans.TopInfo
import com.kanedasoftware.masterscrobbler.components.SquaredImageView
import com.kanedasoftware.masterscrobbler.utils.Constants
import com.kanedasoftware.masterscrobbler.utils.ImageUtils
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*

internal class GridViewTopAdapter(private val context: Context, infoList: ArrayList<TopInfo>, type: String) : BaseAdapter(), KoinComponent {

    internal class ViewHolder(view: View) {
        //ButterKnife
        @BindView(R.id.gridview_image_top)
        lateinit var imageViewAndroid: SquaredImageView

        @BindView(R.id.gridview_album)
        lateinit var textAlbum: TextView

        @BindView(R.id.gridview_artist)
        lateinit var textArtist: TextView

        @BindView(R.id.gridview_plays)
        lateinit var textPlays: TextView

        init {
            ButterKnife.bind(this, view)
        }
    }

    //Koin
    private val imageUtils: ImageUtils by inject()

    private val list = infoList
    private val gridType = type

    override fun getView(position: Int, view: View?, parent: ViewGroup): View? {
        var rowView = view
        val viewHolder: ViewHolder
        if (rowView == null) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            rowView = inflater.inflate(R.layout.gridview_artist_album_layout, parent, false)
            viewHolder = ViewHolder(rowView)
            rowView.tag = viewHolder
        } else {
            viewHolder = rowView.tag as ViewHolder
        }

        if (gridType == Constants.ALBUMS) {
            val textAlbum = viewHolder.textAlbum
            textAlbum.setTypeface(textAlbum.typeface, Typeface.BOLD)
            textAlbum.text = list[position].text1
            viewHolder.textArtist.text = list[position].text2
            viewHolder.textPlays.text = list[position].text3
        } else {
            val textArtist = viewHolder.textArtist
            textArtist.setTypeface(textArtist.typeface, Typeface.BOLD)
            textArtist.text = list[position].text1
            viewHolder.textPlays.text = list[position].text2
        }

        viewHolder.imageViewAndroid.setOnClickListener {
            val builder = CustomTabsIntent.Builder()
            val customTabsIntent = builder.build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, Uri.parse(list[position].lastFmUrl))
        }
        val url = list[position].url
        if (!url.isBlank()) {
            imageUtils.getImageCache(url, viewHolder.imageViewAndroid)
        }

        return rowView
    }

    override fun getCount(): Int {
        return list.size
    }

    fun getList(): ArrayList<TopInfo> {
        return list
    }


    override fun getItem(position: Int): String? {
        return null
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }
}