package com.kanedasoftware.masterscrobbler.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.Matrix.ScaleToFit
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.ImageView
import com.kanedasoftware.masterscrobbler.R
import com.kanedasoftware.masterscrobbler.picasso.CircleTransformation
import com.kanedasoftware.masterscrobbler.picasso.CustomTarget
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

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

    fun mergeMultiple(listBitmaps: MutableList<Bitmap>): Bitmap {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val result = Bitmap.createBitmap(listBitmaps[0].width * 3, metrics.heightPixels, Bitmap.Config.ARGB_8888)
        val space = (metrics.heightPixels - listBitmaps[0].height * 3)/2
        val canvas = Canvas(result)
        val paint = Paint()
        for (i in listBitmaps.indices) {
            canvas.drawBitmap(listBitmaps[i], listBitmaps[i].width * (i % 3).toFloat(), listBitmaps[i].height * (i / 3).toFloat() + space, paint)
            listBitmaps[i].recycle()
        }
        return result
    }

    fun resizeImage(origin:String): Bitmap {
        val destSize = (Resources.getSystem().displayMetrics.widthPixels/3).toFloat()
        var options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        var openStream = URL(origin).openStream()
        BitmapFactory.decodeStream(openStream, null, options)
        openStream.close()

        val inWidth = options.outWidth
        val inHeight = options.outHeight

        options = BitmapFactory.Options()
        options.inSampleSize = max(inWidth / destSize, inHeight / destSize).roundToInt()
        openStream = URL(origin).openStream()
        val roughBitmap = BitmapFactory.decodeStream(openStream, null, options)
        openStream.close()

        val m = Matrix()
        val inRect = RectF(0f, 0f, roughBitmap!!.width.toFloat(), roughBitmap.height.toFloat())
        val outRect = RectF(0f, 0f, destSize, destSize)
        m.setRectToRect(inRect, outRect, ScaleToFit.CENTER)
        val values = FloatArray(9)
        m.getValues(values)

        return Bitmap.createScaledBitmap(roughBitmap, (roughBitmap.width * values[0]).toInt(), (roughBitmap.height * values[4]).toInt(), true)
    }
}