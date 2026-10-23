package com.example.myapplication

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.myapplication.di.AppContainer

class MusicApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.installImageLoader()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The covers of the songs are kept in memory. When the system is short on memory they
        // are dropped first, because they can be read from the files again.
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            container.imageLoader.memoryCache?.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        container.imageLoader.memoryCache?.clear()
    }
}
