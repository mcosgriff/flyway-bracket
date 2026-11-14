package io.cosgriff.flywaybracket.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** What looking for the phone's county gave. */
sealed interface CountyFix {
    /** The phone is in this county (`US-CO-059`), which an installed pack holds. */
    data class Found(val countyCode: String) : CountyFix

    /** The phone is in no county of an installed pack. */
    data object OutsidePacks : CountyFix

    /** The phone gave no location: location is off, the permission is missing, or no provider answered. */
    data object NoLocation : CountyFix
}

/**
 * Finds the county the phone is in from its approximate location, once, when an outing starts (#27).
 * The coordinates go no further than the call that turns them into a county: they are never saved,
 * logged or sent. Only the county's code comes out. Made by `LocationModule`.
 */
interface CountyLocator {
    /** Whether the player has let the app use the phone's approximate location. */
    fun permitted(): Boolean

    /**
     * The county the phone is in now. It can take a while, so the caller sets its own time limit, and
     * cancelling stops the request.
     */
    suspend fun locate(): CountyFix
}

/**
 * [CountyLocator] with Android's own `LocationManager` (no Google Play services) and the approximate
 * location permission, `ACCESS_COARSE_LOCATION`: a county is far larger than its error.
 */
class AndroidCountyLocator(private val context: Context, private val packs: PackRepository) : CountyLocator {
    override fun permitted(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override suspend fun locate(): CountyFix {
        val location = currentLocation() ?: return CountyFix.NoLocation
        // The coordinates are read here and nowhere else.
        val county = packs.countyAt(longitude = location.longitude, latitude = location.latitude) ?: return CountyFix.OutsidePacks
        return CountyFix.Found(county.code)
    }

    private suspend fun currentLocation(): Location? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null
        val provider = providers().firstOrNull { manager.isProviderEnabled(it) } ?: return null
        return suspendCancellableCoroutine { waiting ->
            val signal = CancellationSignal()
            waiting.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, context.mainExecutor) { location ->
                waiting.resume(location)
            }
        }
    }

    /**
     * Android's providers, best first: the platform's fused one (Android 12 and later), the network's,
     * then satellite. With only the approximate permission, Android blurs what any of them gives.
     */
    private fun providers(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }
}
