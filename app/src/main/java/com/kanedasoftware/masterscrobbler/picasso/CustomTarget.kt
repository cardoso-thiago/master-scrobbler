package com.kanedasoftware.masterscrobbler.picasso

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.kanedasoftware.masterscrobbler.utils.Utils
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target

class CustomTarget(var title: String, var text: String):Target {

    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
    }

    override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
        Utils.updateNotification(title, text)
    }

    override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
        Utils.updateNotification(title, text, bitmap)
    }
}