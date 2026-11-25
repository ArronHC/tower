package com.jzb.tower.android

import android.app.Application
import com.jzb.tower.android.di.TowerAppContainer

class TowerApplication : Application() {
    lateinit var container: TowerAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = TowerAppContainer(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        if (::container.isInitialized) {
            container.shutdown()
        }
    }
}
