package dev.hydrogen.tieba

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder

class HydrogenApplication : Application(), SingletonImageLoader.Factory {
    fun clearImageMemory() {
        SingletonImageLoader.get(this).memoryCache?.clear()
    }
    override fun newImageLoader(context: android.content.Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
        }.build()
}
