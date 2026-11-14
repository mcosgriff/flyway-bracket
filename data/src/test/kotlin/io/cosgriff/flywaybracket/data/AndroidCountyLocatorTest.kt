package io.cosgriff.flywaybracket.data

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import io.cosgriff.flywaybracket.domain.pack.testStatePack
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// The places are in made-up Testland (testStatePack()), on a made-up grid near 0° 0°: no real place.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidCountyLocatorTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(LocationManager::class.java)
    private val packs = PackRepository(File(context.filesDir, "locator_test_packs").apply { deleteRecursively() })
    private val locator = AndroidCountyLocator(context, packs)

    @Before
    fun install() = runBlocking {
        packs.install(StatePackCodec.encode(testStatePack()))
        shadowOf(manager).setLocationEnabled(true)
        listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .forEach { shadowOf(manager).setProviderEnabled(it, it == LocationManager.FUSED_PROVIDER) }
    }

    /** The phone is at this made-up place now. */
    private fun at(longitude: Double, latitude: Double) {
        shadowOf(manager).simulateLocation(
            Location(LocationManager.FUSED_PROVIDER).apply {
                this.longitude = longitude
                this.latitude = latitude
                accuracy = 2_000f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            },
        )
    }

    /**
     * Looks for the county while the phone is at this place. Android answers on the main looper, which is
     * the test's thread, so the test runs the looper until the answer comes.
     */
    private fun locateAt(longitude: Double, latitude: Double): CountyFix = runBlocking {
        withTimeout(10_000) {
            val fix = async(Dispatchers.IO) { locator.locate() }
            while (!fix.isCompleted) {
                at(longitude, latitude)
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
            fix.await()
        }
    }

    @Test
    fun `without the permission it is not permitted and finds no location`() {
        assertFalse(locator.permitted())
        assertEquals(CountyFix.NoLocation, locateAt(longitude = 0.5, latitude = 0.5))
    }

    @Test
    fun `with the permission it finds the county of an installed pack`() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertTrue(locator.permitted())
        assertEquals(CountyFix.Found("XX-TS-002"), locateAt(longitude = 1.5, latitude = 0.5))
    }

    @Test
    fun `a place in no installed pack is outside the packs`() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(CountyFix.OutsidePacks, locateAt(longitude = 2.5, latitude = 0.5))
    }

    @Test
    fun `with location off it finds no location`() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(manager).setLocationEnabled(false)
        assertEquals(CountyFix.NoLocation, locateAt(longitude = 0.5, latitude = 0.5))
    }
}
