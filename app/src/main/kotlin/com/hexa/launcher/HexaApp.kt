package com.hexa.launcher

import android.app.Application
import android.content.ComponentCallbacks2

class HexaApp : Application() {

    lateinit var graph: LauncherGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = LauncherGraph(this)
        graph.start()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            graph.icons.trimSmall()
            graph.images.trim()
        }
    }
}

val android.content.Context.graph: LauncherGraph
    get() = (applicationContext as HexaApp).graph
