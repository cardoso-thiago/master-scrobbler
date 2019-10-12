package com.kanedasoftware.masterscrobbler.utils

import android.content.Context
import android.widget.ImageView
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.picasso.CircleTransformation
import com.kanedasoftware.masterscrobbler.picasso.CustomTarget
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import org.koin.core.KoinComponent
import org.koin.core.inject

class ImageUtils constructor(appContext: Context):KoinComponent{

    private val utils: Utils by inject()
    private val target: CustomTarget = CustomTarget()
    private val context = appContext

    fun getImageCache(imageUrl: String, imageView: ImageView) {
        getImageCache(imageUrl, imageView, false)
    }

    fun getImageCache(imageUrl: String, imageView: ImageView, circleTransformation: Boolean) {
        val picassoLoadCache = Picasso.get().load(imageUrl).tag(context).stableKey(imageUrl).placeholder(R.drawable.ic_placeholder)
        if (circleTransformation) {
            picassoLoadCache.transform(CircleTransformation())
        } else {
            picassoLoadCache.fit()
        }

        picassoLoadCache.networkPolicy(NetworkPolicy.OFFLINE).into(imageView, object : Callback {
            override fun onSuccess() {
                utils.logDebug("Imagem $imageUrl carregada do cache")
            }

            override fun onError(e: java.lang.Exception?) {
                utils.logDebug("Erro ao carregar a imagem $imageUrl do cache, vai tentar baixar.")

                val picassoLoad = Picasso.get().load(imageUrl).tag(context).stableKey(imageUrl).placeholder(R.drawable.ic_placeholder)
                if (circleTransformation) {
                    picassoLoad.transform(CircleTransformation())
                } else {
                    picassoLoad.fit()
                }
                picassoLoad.error(R.drawable.ic_placeholder).into(imageView, object : Callback {
                    override fun onSuccess() {
                        utils.logDebug("Imagem $imageUrl baixada com sucesso")
                    }

                    override fun onError(e: java.lang.Exception) {
                        utils.logDebug("Erro ao obter a imagem $imageUrl ${e.message}")
                    }
                })
            }
        })
    }

    fun getNotificationImageCache(imageUrl: String, title: String, text: String) {
        target.title = title
        target.text = text
        Picasso.get().load(imageUrl).stableKey(imageUrl).into(target)
    }
}