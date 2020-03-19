package com.soonsim.kktlogviewer

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.stfalcon.chatkit.commons.ImageLoader

class KKTImageLoader(val context: Context) : ImageLoader {
    override fun loadImage(imageView: ImageView?, url: String?, payload: Any?) {
        Glide.with(context).load(url).into(imageView!!)
    }
}