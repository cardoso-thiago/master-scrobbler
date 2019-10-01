package com.kanedasoftware.masterscrobbler.picasso

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.app.ScrobblerApp
import com.kanedasoftware.masterscrobbler.utils.Utils
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target

class CustomTarget :Target {
    var title: String = ""
    var text: String = ""

    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
        Utils.log("onPrepareLoad")
    }

    override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
        Utils.log("Bitmap falhou")
        val errorImage = BitmapFactory.decodeResource(ScrobblerApp.getContext().resources, R.drawable.ic_placeholder)
        Utils.updateNotification(title, text, errorImage)
    }

    override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
        Utils.log("Bitmap carregado")
        Utils.updateNotification(title, text, bitmap)
    }
}