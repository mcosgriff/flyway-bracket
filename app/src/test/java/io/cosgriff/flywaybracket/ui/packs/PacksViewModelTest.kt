package io.cosgriff.flywaybracket.ui.packs

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.flywaybracket.api.PackSummary
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.DownloadProgress
import io.cosgriff.flywaybracket.data.Downloads
import io.cosgriff.flywaybracket.data.FailedDownload
import io.cosgriff.flywaybracket.data.InstalledPack
import io.cosgriff.flywaybracket.data.PackDownload
import io.cosgriff.flywaybracket.data.PackListing
import io.cosgriff.flywaybracket.data.PackRepository
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.data.packVersion
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.ui.FakePackServer
import io.cosgriff.flywaybracket.ui.components.MeteredAsk
import io.cosgriff.flywaybracket.domain.pack.PackState
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import io.cosgriff.flywaybracket.domain.pack.StatePackResult
import io.cosgriff.flywaybracket.domain.pack.county
import io.cosgriff.flywaybracket.domain.pack.rectangle
import io.cosgriff.flywaybracket.domain.pack.resident
import io.cosgriff.flywaybracket.domain.pack.testStatePack
import io.cosgriff.flywaybracket.domain.place.CountyTableParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// Every state, county and figure is made up: Testland from the shared fixture, and a second state beside it.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PacksViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val directory = File(context.filesDir, "packs_test").apply { deleteRecursively() }
    private val packs = PackRepository(directory)
    private val settings = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { File(context.filesDir, "packs_test.preferences_pb").apply { delete() } },
    )

    private val testland = testStatePack()
    private val testlandBytes = StatePackCodec.encode(testland)
    private val testlandInstalled = InstalledPack(testland, sizeBytes = 2_048, version = "a1")
    private val testlandRow = PackRow("XX-TS", "Testland", counties = 3, LocalDate.of(2026, 3, 14), 2_048, "Made-up figures for tests")

    private val otherland = testland.copy(
        state = PackState("XX-OT", "Otherland"),
        counties = listOf(county("XX-OT-001", "Far Square", listOf(resident("Test Sparrow", 0.5)), rectangle(10.0, 0.0, 11.0, 1.0))),
    )
    private val otherlandInstalled = InstalledPack(otherland, sizeBytes = 512, version = "b1")
    private val otherlandRow = testlandRow.copy(stateCode = "XX-OT", state = "Otherland", counties = 1, sizeBytes = 512)

    // A made-up table: the codes have the real shape and name no real state.
    private val table = CountyTableParser.parse("US-ZA\tAlder\nUS-ZA-001\tHeron\nUS-ZA-003\tLark Hollow\n")

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        scope.cancel()
    }

    @Test
    fun `no packs and no home county give the empty state`() {
        assertEquals(PacksState(), packsState(emptyList(), homeCountyCode = null, table))
    }

    @Test
    fun `with no pack for it, the home county is named from the bundled table and has no species figure`() {
        val state = packsState(listOf(testlandInstalled), "US-ZA-003", table)
        assertEquals(HomeCountyLine(county = "Lark Hollow", state = "Alder", species = null), state.home)
        assertNull(state.homePack)
        assertEquals(listOf(testlandRow), state.extraPacks)
    }

    @Test
    fun `a home county that nothing names is left out`() {
        assertNull(packsState(listOf(testlandInstalled), "US-ZZ-001", table).home)
    }

    @Test
    fun `the pack that holds the home county is set apart, and the others are extra`() {
        val state = packsState(listOf(otherlandInstalled, testlandInstalled), "XX-TS-002", table)
        assertEquals(testlandRow, state.homePack)
        assertEquals(listOf(otherlandRow), state.extraPacks)
        assertEquals(listOf(testlandRow, otherlandRow), state.packs)
    }

    @Test
    fun `the home county's species are the birds expected there at some time of year`() {
        // East Square has a sandpiper expected in September only and a sparrow expected all year.
        val state = packsState(listOf(testlandInstalled), "XX-TS-002", table)
        assertEquals(HomeCountyLine(county = "East Square", state = "Testland", species = 2), state.home)
    }

    @Test
    fun `a bird never on 3 percent of checklists is not one of the home county's species`() {
        val rare = testland.copy(
            counties = listOf(
                county(
                    "XX-TS-001",
                    "West Square",
                    listOf(resident("Test Sparrow", 0.6), resident("Test Vagrant", 0.004)),
                    rectangle(0.0, 0.0, 1.0, 1.0),
                ),
            ),
        )
        assertEquals(1, packsState(listOf(InstalledPack(rare, 1, "c1")), "XX-TS-001", table).home?.species)
    }

    @Test
    fun `each reason a pack file is rejected has plain words to show`() {
        assertEquals(PackProblem.NOT_A_PACK, packProblem(StatePackResult.Reason.NOT_GZIP))
        assertEquals(PackProblem.NOT_A_PACK, packProblem(StatePackResult.Reason.NOT_JSON))
        assertEquals(PackProblem.DAMAGED, packProblem(StatePackResult.Reason.MISSING_FIELD))
        assertEquals(PackProblem.DAMAGED, packProblem(StatePackResult.Reason.BAD_BIN_COUNT))
        assertEquals(PackProblem.TOO_NEW, packProblem(StatePackResult.Reason.TOO_NEW))
    }

    @Test
    fun `only the pack with the home county in it holds it`() {
        val installed = listOf(otherlandInstalled, testlandInstalled)
        assertTrue(holds(installed, "XX-TS", "XX-TS-002"))
        assertFalse(holds(installed, "XX-OT", "XX-TS-002"))
        assertFalse(holds(installed, "XX-TS", null))
    }

    private val server = FakePackServer(packs, settings, scope)
    private var metered = false

    private fun viewModel(server: FakePackServer = this.server) =
        PacksViewModel(packs, settings, BundledCounties(context), server.downloads, server.accounts, { metered }, context)

    /** A file the system's file picker could hand back. */
    private fun file(name: String, content: InputStream): Uri =
        Uri.parse("content://io.cosgriff.flywaybracket.test/$name").also { shadowOf(context.contentResolver).registerInputStream(it, content) }

    private suspend fun PacksViewModel.awaitState(matches: (PacksState) -> Boolean): PacksState =
        withTimeout(10_000) { state.first(matches) }

    @Test
    fun `a chosen pack file is installed and listed`() = runBlocking {
        settings.setHomeCountyCode("XX-TS-001")
        val viewModel = viewModel()

        viewModel.onFileChosen(file("XX-TS.fbpack", testlandBytes.inputStream()))

        val state = viewModel.awaitState { it.homePack != null }
        assertEquals(testlandRow.copy(sizeBytes = testlandBytes.size.toLong()), state.homePack)
        assertEquals(HomeCountyLine(county = "West Square", state = "Testland", species = 2), state.home)
        assertNull(state.problem)
    }

    @Test
    fun `a chosen file that is not a pack is not installed, and the state says why`() = runBlocking {
        val viewModel = viewModel()

        viewModel.onFileChosen(file("notes.txt", "not a pack".byteInputStream()))

        assertEquals(PackProblem.NOT_A_PACK, viewModel.awaitState { it.problem != null }.problem)
        assertEquals(emptyList<InstalledPack>(), packs.installed().first())
    }

    @Test
    fun `a file larger than any pack is turned away before it is read whole`() = runBlocking {
        val viewModel = viewModel()
        val endless = object : InputStream() {
            override fun read(): Int = 0
        }

        viewModel.onFileChosen(file("film.fbpack", endless))

        assertEquals(PackProblem.TOO_LARGE, viewModel.awaitState { it.problem != null }.problem)
    }

    @Test
    fun `a file that cannot be read is reported`() = runBlocking {
        val viewModel = viewModel()
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("made to fail")
        }

        viewModel.onFileChosen(file("gone.fbpack", broken))

        assertEquals(PackProblem.NOT_READ, viewModel.awaitState { it.problem != null }.problem)
    }

    @Test
    fun `adding a pack clears the reason the file before it was turned away`() = runBlocking {
        val viewModel = viewModel()
        viewModel.onFileChosen(file("notes.txt", "not a pack".byteInputStream()))
        viewModel.awaitState { it.problem != null }

        viewModel.onFileChosen(file("XX-TS.fbpack", testlandBytes.inputStream()))

        // The pack is listed a moment before the reason goes, so wait for both.
        val state = viewModel.awaitState { it.extraPacks.isNotEmpty() && it.problem == null }
        assertEquals(listOf("XX-TS"), state.extraPacks.map { it.stateCode })
    }

    @Test
    fun `an extra pack is removed, and the pack that holds the home county is not`() = runBlocking {
        settings.setHomeCountyCode("XX-TS-001")
        packs.install(testlandBytes)
        packs.install(StatePackCodec.encode(otherland))
        val viewModel = viewModel()
        viewModel.awaitState { it.extraPacks.isNotEmpty() }

        viewModel.onRemove("XX-TS")
        viewModel.onRemove("XX-OT")

        val state = viewModel.awaitState { it.extraPacks.isEmpty() }
        assertEquals("XX-TS", state.homePack?.stateCode)
        assertEquals(listOf("XX-TS"), packs.installed().first().map { it.pack.state.code })
    }

    // The server's packs (#39).

    private fun summary(pack: InstalledPack, version: String = pack.version, size: Long = pack.sizeBytes) =
        PackSummary(pack.pack.state.code, pack.pack.state.name, size, pack.pack.builtOn, version)

    @Test
    fun `with no account the server's packs wait for the consent page`() {
        assertEquals(ServerPacks.NeedsAccount, packsState(emptyList(), null, table, listing = PackListing.NoAccount).server)
        assertEquals(ServerPacks.Loading, packsState(emptyList(), null, table, listing = null).server)
        val failed = PackListing.Failed(ServerProblem.NoNetwork)
        assertEquals(ServerPacks.Failed(ServerProblem.NoNetwork), packsState(emptyList(), null, table, listing = failed).server)
    }

    @Test
    fun `the server's packs not on the phone are offered, with how far a download has come`() {
        val listing = PackListing.Listed(listOf(summary(otherlandInstalled), summary(testlandInstalled)))
        val downloads = Downloads(running = mapOf("XX-OT" to DownloadProgress(100, 512)))

        val state = packsState(listOf(testlandInstalled), null, table, listing = listing, downloads = downloads)

        val otherland = ServerPackRow("XX-OT", "Otherland", 512, LocalDate.of(2026, 3, 14), DownloadProgress(100, 512))
        assertEquals(ServerPacks.Listed(available = listOf(otherland), onServer = 2), state.server)
        assertNull(state.extraPacks.single().update)
    }

    @Test
    fun `an installed pack whose version differs from the server's has an update`() {
        val listing = PackListing.Listed(listOf(summary(testlandInstalled, version = "a2", size = 2_100)))
        val failed = FailedDownload("XX-TS", PackDownload.Failed(ServerProblem.NoAnswer))

        val state = packsState(listOf(testlandInstalled), null, table, listing = listing, downloads = Downloads(failed = failed))

        assertEquals(PackUpdate(sizeBytes = 2_100), state.extraPacks.single().update)
        assertEquals(failed, state.failedDownload)
        assertEquals(ServerPacks.Listed(available = emptyList(), onServer = 1), state.server)
    }

    @Test
    fun `with no account the server is not asked, and the screen offers the consent page`() = runBlocking {
        val server = FakePackServer(packs, settings, scope, account = false)

        assertEquals(ServerPacks.NeedsAccount, viewModel(server).awaitState { it.server != ServerPacks.Loading }.server)
        assertEquals(emptyList<Any>(), server.requests)
    }

    @Test
    fun `a pack from the server's list is downloaded and installed, and leaves the list`() = runBlocking {
        val bytes = server.hold(testland)
        val viewModel = viewModel()
        assertEquals(listOf("XX-TS"), viewModel.awaitState { it.server is ServerPacks.Listed }.available().map { it.stateCode })

        viewModel.onDownload("XX-TS")

        val state = viewModel.awaitState { it.extraPacks.isNotEmpty() }
        assertEquals(emptyList<ServerPackRow>(), state.available())
        assertEquals(packVersion(bytes), packs.installed().first().single().version)
    }

    @Test
    fun `an update replaces the pack, and a failed one leaves it in place and says why`() = runBlocking {
        packs.install(testlandBytes)
        val newer = server.hold(testland.copy(builtOn = testland.builtOn.plusMonths(1)))
        val viewModel = viewModel()
        viewModel.awaitState { it.extraPacks.singleOrNull()?.update != null }

        server.offline = true
        viewModel.onDownload("XX-TS")
        val failed = viewModel.awaitState { it.failedDownload != null }
        assertEquals(FailedDownload("XX-TS", PackDownload.Failed(ServerProblem.NoNetwork)), failed.failedDownload)
        assertEquals(packVersion(testlandBytes), packs.installed().first().single().version)

        server.offline = false
        viewModel.onDownload("XX-TS")
        val updated = viewModel.awaitState { it.extraPacks.singleOrNull()?.update == null }
        assertNull(updated.failedDownload)
        assertEquals(packVersion(newer), packs.installed().first().single().version)
    }

    @Test
    fun `a pack over 5 MB on a metered connection asks first, and Not now downloads nothing`() = runBlocking {
        server.hold(testland)
        server.listedSizes["XX-TS"] = 6_000_000
        metered = true
        val viewModel = viewModel()
        viewModel.awaitState { it.server is ServerPacks.Listed }

        viewModel.onDownload("XX-TS")
        val asking = viewModel.awaitState { it.askMetered != null }
        assertEquals(MeteredAsk("XX-TS", "Testland", 6_000_000), asking.askMetered)
        viewModel.onMeteredDismissed()
        viewModel.awaitState { it.askMetered == null }
        assertEquals(1, server.requests.size)

        viewModel.onDownload("XX-TS")
        viewModel.awaitState { it.askMetered != null }
        viewModel.onMeteredConfirmed()
        assertEquals(listOf("XX-TS"), viewModel.awaitState { it.extraPacks.isNotEmpty() }.extraPacks.map { it.stateCode })
    }

    @Test
    fun `a list the server cannot give says why, and Try again asks again`() = runBlocking {
        server.offline = true
        val viewModel = viewModel()
        assertEquals(ServerPacks.Failed(ServerProblem.NoNetwork), viewModel.awaitState { it.server is ServerPacks.Failed }.server)

        server.offline = false
        server.hold(testland)
        viewModel.onRetry()

        assertEquals(listOf("XX-TS"), viewModel.awaitState { it.server is ServerPacks.Listed }.available().map { it.stateCode })
    }

    private fun PacksState.available() = (server as ServerPacks.Listed).available
}
