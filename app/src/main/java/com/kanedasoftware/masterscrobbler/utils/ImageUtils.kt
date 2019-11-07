package com.kanedasoftware.masterscrobbler.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.ImageView
import androidx.annotation.Nullable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.FutureTarget
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.kanedasoftware.masterscrobbler.R
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.koin.core.KoinComponent
import org.koin.core.inject
import androidx.core.content.FileProvider
import android.content.Intent
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class ImageUtils constructor(appContext: Context) : KoinComponent {

    private val utils: Utils by inject()
    private val notificationUtils: NotificationUtils by inject()
    private val context = appContext

    fun getImageCache(imageUrl: String, imageView: ImageView) {
        Glide.with(context).load(imageUrl).placeholder(R.drawable.ic_placeholder).error(R.drawable.ic_image_error).fitCenter().into(imageView)
    }

    fun getAvatarImage(imageUrl: String, imageView: ImageView) {
        Glide.with(context).load(imageUrl).placeholder(R.drawable.ic_placeholder).error(R.drawable.ic_image_error).fitCenter().apply(RequestOptions.circleCropTransform()).into(imageView)
    }

    fun updateNotificationWithImage(imageUrl: String, title: String, text: String) {
        doAsync {
            Glide.with(context).asBitmap().load(imageUrl).listener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(@Nullable e: GlideException?, o: Any, target: Target<Bitmap>, b: Boolean): Boolean {
                    utils.log("Bitmap falhou")
                    val errorImage = BitmapFactory.decodeResource(utils.getAppContext().resources, R.drawable.ic_image_error)
                    uiThread {
                        notificationUtils.updateNotification(title, text, errorImage)
                    }
                    return false
                }

                override fun onResourceReady(bitmap: Bitmap, o: Any, target: Target<Bitmap>, dataSource: DataSource, b: Boolean): Boolean {
                    utils.log("Bitmap carregado")
                    uiThread {
                        notificationUtils.updateNotification(title, text, bitmap)
                    }
                    return false
                }
            }
            ).submit()
        }
    }

    fun getErrorImage(image: ImageView) {
        Glide.with(context).load(R.drawable.ic_image_error).fitCenter().into(image)
    }

    fun mergeMultiple(listBitmaps: MutableList<Bitmap>, full: Boolean, destSize: Int): Bitmap {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val result: Bitmap?
        var space = 0
        if (full) {
            result = Bitmap.createBitmap(destSize * 3, destSize * 3, Bitmap.Config.ARGB_8888)
        } else {
            result = Bitmap.createBitmap(destSize * 3, metrics.heightPixels, Bitmap.Config.ARGB_8888)
            space = (metrics.heightPixels - destSize * 3) / 2
        }

        val canvas = Canvas(result)
        val paint = Paint()
        for (i in listBitmaps.indices) {
            canvas.drawBitmap(listBitmaps[i], destSize * (i % 3).toFloat(), destSize * (i / 3).toFloat() + space, paint)
        }
        return result
    }

    fun getBitmapSync(url: String, destSize: Int): FutureTarget<Bitmap> {
        return Glide.with(context).asBitmap().load(url).apply(RequestOptions()
                .override(destSize, destSize)).submit()
    }

    fun saveImageToShare(image: Bitmap): Uri? {
        val imagesFolder = File(context.cacheDir, "images")
        try {
            imagesFolder.mkdirs()
            val file = File(imagesFolder, "shared_image.png")

            val stream = FileOutputStream(file)
            image.compress(Bitmap.CompressFormat.PNG, 90, stream)
            stream.flush()
            stream.close()
            return FileProvider.getUriForFile(context, "com.kanedasoftware.masterscrobbler.fileprovider", file)

        } catch (e: IOException) {
            utils.logError("Erro ao salvar a imagem em disco")
        }
        return null
    }

    fun shareImage(uri: Uri, message: String) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.putExtra(Intent.EXTRA_TEXT, message)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        intent.type = "image/png"
        context.startActivity(intent)
    }
}