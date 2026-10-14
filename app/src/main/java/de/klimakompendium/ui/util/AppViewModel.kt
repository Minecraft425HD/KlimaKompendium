package de.klimakompendium.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.klimakompendium.AppContainer

val LocalAppContainer = compositionLocalOf<AppContainer> { error("AppContainer nicht bereitgestellt") }

/** Erzeugt ein ViewModel mit Zugriff auf den [AppContainer]. [key] trennt Instanzen mit unterschiedlichen Argumenten. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
