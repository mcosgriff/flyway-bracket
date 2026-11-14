package io.cosgriff.flywaybracket.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.flywaybracket.data.AccountRepository
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.PackRepository
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.data.account.AccountStore
import io.cosgriff.flywaybracket.data.account.StoredAccount
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.data.server.serverHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import java.io.File
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
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
import org.robolectric.annotation.Config

// The names, ids and secrets are made up; the counties come from the table bundled with the app. Ktor's
// mock engine stands in for the server.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val settings = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { File(context.filesDir, "settings_test.preferences_pb").apply { delete() } },
    )
    private val store = MemoryStore()
    private val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())
    private var offline = false
    private val accounts = AccountRepository(
        server = ServerClient(
            serverHttpClient(
                MockEngine { request ->
                    requests += request
                    if (offline) throw UnknownHostException("server.example")
                    respond("", HttpStatusCode.NoContent)
                },
            ),
            "https://server.example",
        ),
        store = store,
        settings = settings,
    )
    private val counties = BundledCounties(context)

    // One thread, as the phone's main thread is: the test runs on it too, so no two writes of the state race.
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(main)
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        main.close()
        scope.cancel()
    }

    private fun viewModel() = SettingsViewModel(
        settings,
        accounts,
        PackRepository(File(context.filesDir, "settings_test_packs").apply { deleteRecursively() }),
        counties,
    )

    private suspend fun SettingsViewModel.await(matches: (SettingsState) -> Boolean): SettingsState = withTimeout(10_000) {
        while (!matches(state)) delay(10)
        state
    }

    @Test
    fun `the profile starts from what is saved, and Save waits for a change`() = runBlocking(main) {
        val county = counties.table().states.first().counties.first()
        settings.setDisplayName("Wren Example")
        settings.setHomeCountyCode(county.code)

        val viewModel = viewModel()
        val state = viewModel.await { it.profile.county != null && it.hasAccount != null }

        assertEquals("Wren Example", state.profile.name)
        assertEquals(county, state.profile.county)
        assertEquals(false, state.hasAccount)
        assertFalse(state.profile.canSave)
        viewModel.onName("W")
        assertFalse("too short", viewModel.state.profile.canSave)
        viewModel.onName("Robin Example ")
        assertTrue(viewModel.state.profile.canSave)
    }

    @Test
    fun `with no account, a saved profile stays on the phone`() = runBlocking(main) {
        val counties = counties.table().states.first().counties
        settings.setDisplayName("Wren Example")
        settings.setHomeCountyCode(counties.first().code)
        val viewModel = viewModel()
        viewModel.await { it.profile.county != null }

        viewModel.onName(" Robin Example ")
        viewModel.onCounty(counties.last())
        viewModel.onSave()

        // The name and the county are saved one after the other.
        withTimeout(10_000) { settings.settings().first { it.displayName == "Robin Example" && it.homeCountyCode == counties.last().code } }
        assertFalse(viewModel.state.profile.canSave)
        assertEquals(emptyList<HttpRequestData>(), requests.toList())
    }

    @Test
    fun `with an account, a saved profile is sent, and a failure to send is shown`() = runBlocking(main) {
        store.account = StoredAccount("player-1", "made-up-secret", sentDisplayName = "Wren Example")
        settings.setDisplayName("Wren Example")
        settings.setHomeCountyCode(counties.table().states.first().counties.first().code)
        offline = true
        val viewModel = viewModel()
        viewModel.await { it.profile.county != null && it.hasAccount == true }

        viewModel.onName("Robin Example")
        viewModel.onSave()

        assertEquals(ServerProblem.NoNetwork, viewModel.await { it.profileProblem != null }.profileProblem)
        assertTrue(requests.any { it.method == HttpMethod.Patch })
    }

    @Test
    fun `deleting asks twice, then deletes the account on the server and the phone`() = runBlocking(main) {
        store.account = StoredAccount("player-1", "made-up-secret")
        val viewModel = viewModel()
        viewModel.await { it.hasAccount == true }

        viewModel.onDelete()
        assertEquals(DeleteStep.ASKED, viewModel.state.delete)
        viewModel.onDeleteConfirm()
        assertEquals("the first question's yes is not enough", DeleteStep.ASKED, viewModel.state.delete)
        viewModel.onDeleteContinue()
        assertEquals(DeleteStep.ASKED_AGAIN, viewModel.state.delete)
        assertTrue(requests.isEmpty())
        viewModel.onDeleteConfirm()

        val state = viewModel.await { it.deleted && it.hasAccount == false }
        assertEquals(DeleteStep.NONE, state.delete)
        assertEquals(listOf(HttpMethod.Delete), requests.map { it.method })
        assertEquals("/v1/account", requests.single().url.encodedPath)
        assertNull(store.account)
    }

    @Test
    fun `cancelling either question sends nothing`() = runBlocking(main) {
        store.account = StoredAccount("player-1", "made-up-secret")
        val viewModel = viewModel()
        viewModel.await { it.hasAccount == true }

        viewModel.onDelete()
        viewModel.onDeleteCancel()
        viewModel.onDelete()
        viewModel.onDeleteContinue()
        viewModel.onDeleteCancel()

        assertEquals(DeleteStep.NONE, viewModel.state.delete)
        assertTrue(requests.isEmpty())
        assertEquals(true, viewModel.state.hasAccount)
    }

    @Test
    fun `a delete that fails keeps the account and says why`() = runBlocking(main) {
        store.account = StoredAccount("player-1", "made-up-secret")
        offline = true
        val viewModel = viewModel()
        viewModel.await { it.hasAccount == true }

        viewModel.onDelete()
        viewModel.onDeleteContinue()
        viewModel.onDeleteConfirm()

        val state = viewModel.await { it.deleteProblem != null }
        assertEquals(ServerProblem.NoNetwork, state.deleteProblem)
        assertEquals(DeleteStep.NONE, state.delete)
        assertFalse(state.deleted)
        assertEquals(true, state.hasAccount)
    }

    private class MemoryStore : AccountStore {
        @Volatile var account: StoredAccount? = null

        override suspend fun read() = account

        override suspend fun write(account: StoredAccount) {
            this.account = account
        }

        override suspend fun clear() {
            account = null
        }
    }
}
