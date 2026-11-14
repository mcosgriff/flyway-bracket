package io.cosgriff.flywaybracket.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The table that ships: it must parse, and a list a person scrolls must be in a steady order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BundledCountiesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val table = runBlocking { BundledCounties(context).table() }

    @Test
    fun `the table has a state, and every state has counties`() {
        assertTrue(table.states.isNotEmpty())
        table.states.forEach { assertTrue("${it.code} has no counties", it.counties.isNotEmpty()) }
    }

    @Test
    fun `states are in order of name, and counties in the order of their codes, as the Census lists them`() {
        assertEquals(table.states.map { it.name }.sorted(), table.states.map { it.name })
        table.states.forEach { state ->
            assertEquals(state.code, state.counties.map { it.code }.sorted(), state.counties.map { it.code })
        }
    }

    @Test
    fun `Colorado has its 64 counties`() {
        val colorado = table.states.single { it.code == "US-CO" }
        assertEquals("Colorado", colorado.name)
        assertEquals(64, colorado.counties.size)
        assertEquals("Jefferson", table.locate("US-CO-059")?.county?.name)
    }
}
