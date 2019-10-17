package com.kanedasoftware.masterscrobbler.picasso

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.utils.NotificationUtils
import com.kanedasoftware.masterscrobbler.utils.Utils
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import org.koin.core.KoinComponent
import org.koin.core.inject

class CustomTarget : Target, KoinComponent {

    private val utils: Utils by inject()
    private val notificationUtils: NotificationUtils by inject()

    var title: String = ""
    var text: String = ""

    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
        utils.log("onPrepareLoad")
    }

    override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
        utils.log("Bitmap falhou")
        val errorImage = BitmapFactory.decodeResource(utils.getAppContext().resources, R.drawable.ic_placeholder)
        notificationUtils.updateNotification(title, text, errorImage)
    }

    override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
        utils.log("Bitmap carregado")
        notificationUtils.updateNotification(title, text, bitmap)
    }
}