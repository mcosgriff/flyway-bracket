package io.cosgriff.flywaybracket.data

import io.cosgriff.flywaybracket.domain.pack.PackState
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import io.cosgriff.flywaybracket.domain.pack.StatePackResult
import io.cosgriff.flywaybracket.domain.pack.county
import io.cosgriff.flywaybracket.domain.pack.rectangle
import io.cosgriff.flywaybracket.domain.pack.resident
import io.cosgriff.flywaybracket.domain.pack.testStatePack
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Every pack here is made up: Testland from the shared fixture, and a second state beside it.
class PackRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val directory: File by lazy { File(folder.root, "packs") }
    private val packs by lazy { PackRepository(directory) }

    private val testland = testStatePack()
    private val testlandBytes = StatePackCodec.encode(testland)

    /** A second made-up state, east of Testland, whose name sorts before it. */
    private val otherland = testland.copy(
        state = PackState("XX-OT", "Otherland"),
        builtOn = LocalDate.of(2026, 4, 2),
        counties = listOf(
            county("XX-OT-001", "Far Square", listOf(resident("Test Sparrow", 0.5)), rectangle(10.0, 0.0, 11.0, 1.0)),
        ),
    )
    private val otherlandBytes = StatePackCodec.encode(otherland)

    private suspend fun installed() = packs.installed().first()

    @Test
    fun `no packs are installed at first`() = runBlocking {
        assertEquals(emptyList<InstalledPack>(), installed())
        assertNull(packs.chartFor("XX-TS-001"))
        assertNull(packs.countyAt(0.5, 0.5))
    }

    @Test
    fun `an installed pack is listed with the size of its file, and saved under its state's code`() = runBlocking {
        val result = packs.install(testlandBytes)

        val expected = InstalledPack(testland, testlandBytes.size.toLong(), packVersion(testlandBytes))
        assertEquals(PackInstall.Installed(expected), result)
        assertEquals(listOf(expected), installed())
        assertEquals(listOf("XX-TS.fbpack"), directory.list()?.toList())
        assertTrue(File(directory, "XX-TS.fbpack").readBytes().contentEquals(testlandBytes))
    }

    @Test
    fun `a pack's version is the SHA-256 of its file, as the server names it, and is read again from the file`() = runBlocking {
        packs.install(testlandBytes)
        // The SHA-256 of the empty file, in lowercase hex, so the encoding is fixed.
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", packVersion(ByteArray(0)))
        assertEquals(packVersion(testlandBytes), PackRepository(directory).installed().first().single().version)
    }

    @Test
    fun `packs are listed in order of state name`() = runBlocking {
        packs.install(testlandBytes)
        packs.install(otherlandBytes)
        assertEquals(listOf("Otherland", "Testland"), installed().map { it.pack.state.name })
    }

    @Test
    fun `a file that is not a pack is rejected with the reason and not saved`() = runBlocking {
        assertEquals(PackInstall.Rejected(StatePackResult.Reason.NOT_GZIP), packs.install("not a pack".toByteArray()))
        assertEquals(emptyList<InstalledPack>(), installed())
        assertFalse(directory.exists())
    }

    @Test
    fun `a pack whose state code could not name a file is rejected and not saved`() = runBlocking {
        val bytes = StatePackCodec.encode(testland.copy(state = PackState("../XX-TS", "Testland")))
        assertEquals(PackInstall.Rejected(StatePackResult.Reason.MISSING_FIELD), packs.install(bytes))
        assertEquals(emptyList<InstalledPack>(), installed())
        assertFalse(directory.exists())
    }

    @Test
    fun `a pack that cannot be written is not saved and not listed`() = runBlocking {
        // A file where the directory should be.
        directory.writeText("in the way")
        assertEquals(PackInstall.NotSaved, packs.install(testlandBytes))
        assertEquals(emptyList<InstalledPack>(), installed())
    }

    @Test
    fun `installing a state again replaces its pack`() = runBlocking {
        packs.install(testlandBytes)
        val newer = testland.copy(builtOn = LocalDate.of(2026, 9, 1), counties = testland.counties.take(1))
        packs.install(StatePackCodec.encode(newer))

        assertEquals(listOf(newer), installed().map { it.pack })
        assertEquals(listOf("XX-TS.fbpack"), directory.list()?.toList())
        assertNull(packs.chartFor("XX-TS-002"))
    }

    @Test
    fun `a deleted pack is gone from the list, from the phone and from lookups`() = runBlocking {
        packs.install(testlandBytes)
        packs.install(otherlandBytes)

        packs.delete("XX-TS")

        assertEquals(listOf(otherland), installed().map { it.pack })
        assertEquals(listOf("XX-OT.fbpack"), directory.list()?.toList())
        assertNull(packs.chartFor("XX-TS-001"))
        assertNull(packs.countyAt(0.5, 0.5))
    }

    @Test
    fun `deleting a state that is not installed changes nothing`() = runBlocking {
        packs.install(testlandBytes)
        packs.delete("XX-OT")
        assertEquals(listOf(testland), installed().map { it.pack })
    }

    @Test
    fun `a county's chart is found by its code across the installed packs`() = runBlocking {
        packs.install(testlandBytes)
        packs.install(otherlandBytes)
        assertEquals(testland.counties[1].chart, packs.chartFor("XX-TS-002"))
        assertEquals(otherland.counties[0].chart, packs.chartFor("XX-OT-001"))
        assertNull(packs.chartFor("XX-TS-999"))
    }

    @Test
    fun `the county a place is in is found across the installed packs`() = runBlocking {
        packs.install(testlandBytes)
        packs.install(otherlandBytes)
        assertEquals("XX-TS-001", packs.countyAt(0.5, 0.5)?.code)
        // The second island of Twin Islands.
        assertEquals("XX-TS-003", packs.countyAt(5.5, 0.5)?.code)
        assertEquals("XX-OT-001", packs.countyAt(10.5, 0.5)?.code)
        // Between the islands, in no county.
        assertNull(packs.countyAt(4.5, 0.5))
    }

    @Test
    fun `packs saved earlier are read when the app starts again`() = runBlocking {
        packs.install(testlandBytes)
        packs.install(otherlandBytes)

        val restarted = PackRepository(directory)

        assertEquals(listOf(otherland, testland), restarted.installed().first().map { it.pack })
        assertEquals(testland.counties[0].chart, restarted.chartFor("XX-TS-001"))
    }

    @Test
    fun `a pack is parsed once, not on every lookup`() = runBlocking {
        packs.install(testlandBytes)
        val restarted = PackRepository(directory)

        // Parsing again would give an equal chart, but not the same one.
        val first = restarted.chartFor("XX-TS-001")
        assertSame(first, restarted.chartFor("XX-TS-001"))
        assertSame(first, restarted.countyAt(0.5, 0.5)?.chart)
        assertSame(first, restarted.installed().first().single().pack.counties[0].chart)

        // With the file gone behind its back, the answers still come from memory.
        assertTrue(File(directory, "XX-TS.fbpack").delete())
        assertSame(first, restarted.chartFor("XX-TS-001"))
    }

    @Test
    fun `a saved file that no longer reads as a pack is passed over`() = runBlocking {
        packs.install(testlandBytes)
        File(directory, "XX-OT.fbpack").writeText("damaged")

        assertEquals(listOf(testland), PackRepository(directory).installed().first().map { it.pack })
    }
}
