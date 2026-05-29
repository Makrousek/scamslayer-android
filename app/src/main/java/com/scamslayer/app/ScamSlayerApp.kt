package com.scamslayer.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.scamslayer.app.data.api.ApiClient

class ScamSlayerApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(ApiClient.okHttpClient)
            .build()
    }
}
