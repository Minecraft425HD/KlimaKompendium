package de.klimakompendium

import android.app.Application

open class KlimaApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Überschreibbar für Tests. */
    protected open fun createContainer(): AppContainer = AppContainer(this)

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        container.seedAsync()
    }
}
