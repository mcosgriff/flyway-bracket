package io.cosgriff.flywaybracket.data

import io.cosgriff.flywaybracket.api.PackSummary
import io.cosgriff.flywaybracket.data.server.PackFetch
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.data.server.ServerResult
import io.cosgriff.flywaybracket.domain.pack.StatePackResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What asking the server for its list of packs gave. */
sealed interface PackListing {
    /** The phone has no account, so nothing was asked: the consent page comes first (#38). */
    data object NoAccount : PackListing

    data class Failed(val problem: ServerProblem) : PackListing

    /** The server's packs, in order of state name. */
    data class Listed(val packs: List<PackSummary>) : PackListing
}

/** How far a download has come. [totalBytes] is the file's size, or null when it is not known. */
data class DownloadProgress(val receivedBytes: Long, val totalBytes: Long?) {
    /** From 0 to 1, or null when the size is not known. */
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (receivedBytes.toFloat() / it).coerceIn(0f, 1f) }
}

/** What downloading a pack gave. Only [Installed] changed anything on the phone. */
sealed interface PackDownload {
    data class Installed(val pack: InstalledPack) : PackDownload

    /** The phone already holds the server's version of the pack. */
    data object Unchanged : PackDownload

    /** The phone has no account, so nothing was asked. */
    data object NoAccount : PackDownload

    /** The server could not be reached, or refused. */
    data class Failed(val problem: ServerProblem) : PackDownload

    /**
     * The file that came is not a pack this app can read, or not the file the server named. Nothing was
     * saved, and the pack on the phone, if there is one, stays as it was.
     *
     * @property reason why the pack format refused it, or null when it was larger than any pack or was
     *   changed on the way
     */
    data class Refused(val reason: StatePackResult.Reason?) : PackDownload

    /** The pack is good and the phone could not save it; the one on the phone stays. */
    data object NotSaved : PackDownload
}

/**
 * The downloads running, by state code, and the last one that did not install: kept until that state is
 * downloaded again.
 */
data class Downloads(val running: Map<String, DownloadProgress> = emptyMap(), val failed: FailedDownload? = null)

/** A download of the state [stateCode] that gave [outcome] and installed nothing. */
data class FailedDownload(val stateCode: String, val outcome: PackDownload)

/** A download on a metered connection asks first when the file is larger than this: 5 MB (#39). */
const val METERED_ASK_BYTES = 5_000_000L

/** Whether a download of [sizeBytes] asks the player first, on a [metered] connection. */
fun asksBeforeDownload(sizeBytes: Long, metered: Boolean): Boolean = metered && sizeBytes > METERED_ASK_BYTES

/**
 * State packs from the league server (#39): its list, and downloading one into [packs]. Both need the
 * account, so with none they ask nothing. A download replaces the pack on the phone only once the new file
 * has been read in full, matched to the version the server named and checked by `StatePackCodec`; anything
 * short of that leaves the old pack where it was. Made by `PacksModule`.
 *
 * @param scope where downloads run: one started on a screen goes on when the player leaves it
 */
class PackDownloads(
    private val server: ServerClient,
    private val accounts: AccountRepository,
    private val packs: PackRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(Downloads())

    /** The downloads running, and the last that failed. */
    val state: StateFlow<Downloads> = mutableState.asStateFlow()

    /** The packs the server holds. With no account, no call is made. */
    suspend fun available(): PackListing {
        val credentials = accounts.credentials() ?: return PackListing.NoAccount
        return when (val listed = server.packs(credentials)) {
            is ServerResult.Ok -> PackListing.Listed(listed.value.packs)
            is ServerResult.Failed -> PackListing.Failed(listed.problem)
        }
    }

    /** Starts downloading [pack] in [scope], unless it is downloading already. [state] follows it. */
    fun start(pack: PackSummary) {
        val code = pack.stateCode
        var started = false
        mutableState.update { now ->
            started = code !in now.running
            if (!started) {
                now
            } else {
                now.copy(
                    running = now.running + (code to DownloadProgress(0, pack.sizeBytes)),
                    failed = now.failed?.takeIf { it.stateCode != code },
                )
            }
        }
        if (!started) return
        scope.launch {
            var outcome: PackDownload? = null
            try {
                outcome = download(code, pack.version, pack.sizeBytes) { progress -> moved(code, progress) }
            } finally {
                // Cancelled, there is no outcome and nothing to report.
                mutableState.update { now ->
                    now.copy(
                        running = now.running - code,
                        failed = when (outcome) {
                            null, is PackDownload.Installed, PackDownload.Unchanged -> now.failed
                            else -> FailedDownload(code, outcome)
                        },
                    )
                }
            }
        }
    }

    /**
     * Downloads the pack of the state [stateCode] and installs it. The version of the pack on the phone is
     * sent, so a pack the server has not changed is not sent again.
     *
     * @param version the version the server's list gave, checked against the file when the server names
     *   none with it
     * @param sizeBytes the size the server's list gave, for [onProgress] when the server sends none
     */
    suspend fun download(
        stateCode: String,
        version: String?,
        sizeBytes: Long?,
        onProgress: (DownloadProgress) -> Unit = {},
    ): PackDownload {
        val credentials = accounts.credentials() ?: return PackDownload.NoAccount
        val held = packs.installed().first().firstOrNull { it.pack.state.code == stateCode }?.version
        val fetched = server.downloadPack(credentials, stateCode, held) { received, total ->
            onProgress(DownloadProgress(received, total ?: sizeBytes))
        }
        val file = when (fetched) {
            is ServerResult.Failed -> return PackDownload.Failed(fetched.problem)
            is ServerResult.Ok -> when (val fetch = fetched.value) {
                PackFetch.Unchanged -> return PackDownload.Unchanged
                PackFetch.TooLarge -> return PackDownload.Refused(reason = null)
                is PackFetch.Received -> fetch
            }
        }
        // The version is the file's SHA-256, so a file that does not match it was changed on the way.
        val named = file.version ?: version
        if (named != null && named != packVersion(file.bytes)) return PackDownload.Refused(reason = null)
        return when (val installed = packs.install(file.bytes)) {
            is PackInstall.Installed -> PackDownload.Installed(installed.pack)
            is PackInstall.Rejected -> PackDownload.Refused(installed.reason)
            PackInstall.NotSaved -> PackDownload.NotSaved
        }
    }

    /** A step of progress: kept when the percentage changes, so the screen is not redrawn for each read. */
    private fun moved(code: String, progress: DownloadProgress) {
        mutableState.update { now ->
            val before = now.running[code] ?: return@update now
            if (before.step == progress.step) now else now.copy(running = now.running + (code to progress))
        }
    }

    private val DownloadProgress.step: Long
        get() = fraction?.let { (it * PERCENT).toLong() } ?: (receivedBytes / UNKNOWN_SIZE_STEP)

    private companion object {
        const val PERCENT = 100

        /** With no size known, progress moves every 64 KiB. */
        const val UNKNOWN_SIZE_STEP = 64 * 1024L
    }
}
