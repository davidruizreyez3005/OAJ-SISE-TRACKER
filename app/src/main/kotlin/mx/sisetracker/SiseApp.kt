package mx.sisetracker

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras

class SiseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** The app's dependencies, from a ViewModel factory's extras. */
val CreationExtras.container: AppContainer
    get() = (checkNotNull(this[APPLICATION_KEY]) as SiseApp).container
