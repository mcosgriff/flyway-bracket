package io.cosgriff.flywaybracket.ui.packs

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.InstalledPack
import io.cosgriff.flywaybracket.data.PackInstall
import io.cosgriff.flywaybracket.data.PackRepository
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.domain.pack.StatePackResult
import io.cosgriff.flywaybracket.domain.place.CountyTable
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The packs on the phone and the home county's line (#24). A pack is added from a file the player
 * chooses; downloading takes its place in #39.
 */
@HiltViewModel
class PacksViewModel @Inject constructor(
    private val packs: PackRepository,
    private val settings: SettingsRepository,
    counties: BundledCounties,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val problem = MutableStateFlow<PackProblem?>(null)

    val state: StateFlow<PacksState> = combine(packs.installed(), settings.settings(), problem) { installed, player, problem ->
        packsState(installed, player.homeCountyCode, counties.table(), problem)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), PacksState())

    /** The player chose a file in the system's file picker. */
    fun onFileChosen(file: Uri) {
        viewModelScope.launch { problem.value = add(file) }
    }

    /** Removes a state's pack, unless it holds the home county. */
    fun onRemove(stateCode: String) {
        viewModelScope.launch {
            // Read afresh, not from [state], which stands still while no screen is watching it.
            val home = settings.settings().first().homeCountyCode
            if (!holds(packs.installed().first(), stateCode, home)) packs.delete(stateCode)
        }
    }

    /** Null when the pack was added. */
    private suspend fun add(file: Uri): PackProblem? {
        val bytes = when (val read = read(file)) {
            FileRead.NotRead -> return PackProblem.NOT_READ
            FileRead.TooLarge -> return PackProblem.TOO_LARGE
            is FileRead.Bytes -> read.bytes
        }
        return when (val result = packs.install(bytes)) {
            is PackInstall.Installed -> null
            is PackInstall.Rejected -> packProblem(result.reason)
            PackInstall.NotSaved -> PackProblem.NOT_SAVED
        }
    }

    private suspend fun read(file: Uri): FileRead = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(file)?.use { input ->
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    bytes.write(buffer, 0, count)
                    if (bytes.size() > MAX_PACK_BYTES) return@withContext FileRead.TooLarge
                }
                FileRead.Bytes(bytes.toByteArray())
            } ?: FileRead.NotRead
        } catch (_: IOException) {
            FileRead.NotRead
        } catch (_: SecurityException) {
            // The file's owner took the picker's permission back.
            FileRead.NotRead
        }
    }

    private sealed interface FileRead {
        class Bytes(val bytes: ByteArray) : FileRead

        data object TooLarge : FileRead

        data object NotRead : FileRead
    }

    private companion object {
        /** Long enough to keep the list across a rotation. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}

/**
 * The most a chosen file is read before it is turned away: a wrong file, a film say, must not be read
 * whole into memory. A state's pack is a few megabytes.
 */
internal const val MAX_PACK_BYTES = 32 * 1024 * 1024

/** Whether the pack of the state [stateCode] holds the county [homeCountyCode]. */
internal fun holds(installed: List<InstalledPack>, stateCode: String, homeCountyCode: String?): Boolean =
    installed.any { it.pack.state.code == stateCode && it.pack.counties.any { county -> county.code == homeCountyCode } }

/** The codec's reason, as the screen tells them apart. */
internal fun packProblem(reason: StatePackResult.Reason): PackProblem = when (reason) {
    StatePackResult.Reason.NOT_GZIP, StatePackResult.Reason.NOT_JSON -> PackProblem.NOT_A_PACK
    StatePackResult.Reason.MISSING_FIELD, StatePackResult.Reason.BAD_BIN_COUNT -> PackProblem.DAMAGED
    StatePackResult.Reason.TOO_NEW -> PackProblem.TOO_NEW
}

/**
 * What the Packs screen shows for the [installed] packs and the home county [homeCountyCode]. The pack
 * that holds the home county is set apart from the others. The county's name and its number of species
 * come from that pack; with no such pack, the name comes from the bundled [table] and there is no number.
 */
internal fun packsState(
    installed: List<InstalledPack>,
    homeCountyCode: String?,
    table: CountyTable,
    problem: PackProblem? = null,
): PacksState {
    val homeInstalled = installed.firstOrNull { it.pack.counties.any { county -> county.code == homeCountyCode } }
    val inPack = homeInstalled?.pack?.counties?.firstOrNull { it.code == homeCountyCode }
    val inTable = homeCountyCode?.let(table::locate)
    val home = when {
        homeInstalled != null && inPack != null ->
            HomeCountyLine(inPack.name, homeInstalled.pack.state.name, species = inPack.chart.expectedAnyTime().size)
        inTable != null -> HomeCountyLine(inTable.county.name, inTable.state.name, species = null)
        else -> null
    }
    return PacksState(
        home = home,
        homePack = homeInstalled?.toRow(),
        extraPacks = installed.filter { it !== homeInstalled }.map { it.toRow() },
        problem = problem,
    )
}

private fun InstalledPack.toRow() = PackRow(
    stateCode = pack.state.code,
    state = pack.state.name,
    counties = pack.counties.size,
    builtOn = pack.builtOn,
    sizeBytes = sizeBytes,
    source = pack.source,
)
