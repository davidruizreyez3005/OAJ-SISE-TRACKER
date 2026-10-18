package mx.sisetracker

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.concurrent.thread
import mx.sisetracker.core.Catalogo
import mx.sisetracker.ui.search.BundledOrganos

class SiseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The search screen's dropdowns come from the bundled catalog: read it
        // now, off the main thread, so every list is ready when first opened.
        thread(name = "catalog-warm-up", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            Catalogo.warmUp()
            BundledOrganos.warmUp()
        }
    }
}

/** The app's dependencies, from a ViewModel factory's extras. */
val CreationExtras.container: AppContainer
    get() = (checkNotNull(this[APPLICATION_KEY]) as SiseApp).container
