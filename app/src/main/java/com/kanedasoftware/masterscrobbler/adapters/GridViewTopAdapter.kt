package com.kanedasoftware.masterscrobbler.adapters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView.ScaleType.CENTER_CROP
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.picasso.SquaredImageView
import com.squareup.picasso.Picasso
import java.util.*


internal class GridViewTopAdapter(private val context: Context, imageUrls: ArrayList<String>) : BaseAdapter() {

    private val urls = imageUrls

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
        var view: SquaredImageView? = if (convertView == null) {
            SquaredImageView(context)
        } else {
            convertView as SquaredImageView
        }

        view?.scaleType = CENTER_CROP
        // Get the image URL for the current position.
        val url = getItem(position)
        Picasso.get().load(url).placeholder(R.drawable.placeholder).error(R.drawable.placeholder).fit().tag(context).into(view)
        return view
    }

    override fun getCount(): Int {
        return urls.size
    }

    override fun getItem(position: Int): String {
        return urls[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }
}