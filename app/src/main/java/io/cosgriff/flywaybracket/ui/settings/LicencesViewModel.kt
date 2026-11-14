package io.cosgriff.flywaybracket.ui.settings

import android.content.Context
import android.content.res.AssetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reads the font's licence and the build's list of libraries from the app's assets (#44). */
@HiltViewModel
class LicencesViewModel @Inject constructor(@ApplicationContext context: Context) : ViewModel() {
    private val mutableState = MutableStateFlow(LicencesState())
    val state: StateFlow<LicencesState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { mutableState.value = withContext(Dispatchers.IO) { readLicences(context.assets) } }
    }
}

/** The font's licence and the libraries, from [assets]. A part that cannot be read is left out. */
internal fun readLicences(assets: AssetManager): LicencesState {
    fun read(name: String): String? = try {
        assets.open(name).bufferedReader().use { it.readText() }
    } catch (_: IOException) {
        null
    }
    val libraries = read(LIBRARIES_ASSET)?.let(::parseLibraries)
    return LicencesState(fontLicence = read(FONT_LICENCE_ASSET), libraries = libraries.orEmpty(), notRead = libraries == null)
}
