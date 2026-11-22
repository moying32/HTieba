package dev.hydrogen.tieba

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled

class HydrogenApplication : Application(), SingletonImageLoader.Factory {
    // Separate memory cache prevents an animated drawable from the normal
    // loader being reused in minimal mode. Default decoders produce stills.
    val stillImageLoader: ImageLoader by lazy {
        ImageLoader.Builder(this).serviceLoaderEnabled(false)
            .components { add(OkHttpNetworkFetcherFactory()) }.build()
    }
    fun clearImageMemory() {
        SingletonImageLoader.get(this).memoryCache?.clear()
        stillImageLoader.memoryCache?.clear()
    }
    override fun newImageLoader(context: android.content.Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
        }.build()
}
