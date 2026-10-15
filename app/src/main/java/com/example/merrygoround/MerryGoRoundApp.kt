package com.example.merrygoround

import android.app.Application
import com.example.merrygoround.di.AppContainer

class MerryGoRoundApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
