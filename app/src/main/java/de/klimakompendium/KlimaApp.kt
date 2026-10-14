package de.klimakompendium

import android.app.Application

class KlimaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.seedAsync()
    }
}
