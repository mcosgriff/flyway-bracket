package io.cosgriff.flywaybracket.server.pack

import io.cosgriff.flywaybracket.api.PackSummary
import io.cosgriff.flywaybracket.domain.pack.PackState
import io.cosgriff.flywaybracket.domain.pack.StatePackResult
import io.cosgriff.flywaybracket.domain.pack.testStatePack
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/** The packs directory, read and re-read. Every pack is the made-up Testland of `:domain`'s fixtures. */
class PacksTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val directory: Path get() = folder.root.toPath()

    private val testland = testStatePack()

    /** Testland under another made-up state's code and name. */
    private val otherland = testland.copy(state = PackState("XX-OT", "Otherland"))

    @Test
    fun `lists each pack with its name, size, date built and version`() {
        val bytes = writePack(directory, testland)

        val summaries = Packs(directory).summaries()

        val expected = PackSummary("XX-TS", "Testland", bytes.size.toLong(), LocalDate.of(2026, 3, 14), Packs.version(bytes))
        assertEquals(listOf(expected), summaries)
    }

    @Test
    fun `the version is the SHA-256 of the file, in hex`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Packs.version("abc".toByteArray()),
        )
    }

    @Test
    fun `packs are listed in order of state name`() {
        writePack(directory, testland)
        writePack(directory, otherland)

        assertEquals(listOf("Otherland", "Testland"), Packs(directory).summaries().map { it.name })
    }

    @Test
    fun `hands out the file's bytes as they are on disk`() {
        val bytes = writePack(directory, testland)

        assertArrayEquals(bytes, Packs(directory).file("XX-TS")?.bytes)
    }

    @Test
    fun `gives a county's chart`() {
        writePack(directory, testland)
        val packs = Packs(directory)

        assertEquals(testland.counties[1].chart, packs.chartFor("XX-TS-002"))
        assertNull(packs.chartFor("XX-TS-999"))
    }

    @Test
    fun `a file the codec rejects is skipped with its reason, and the others are still held`() {
        writePack(directory, testland)
        write(directory, "XX-OT.fbpack", "not a pack".toByteArray())

        val packs = Packs(directory)

        assertEquals(listOf("XX-TS"), packs.summaries().map { it.stateCode })
        assertEquals(
            listOf(SkippedPack("XX-OT.fbpack", SkipReason.Rejected(StatePackResult.Reason.NOT_GZIP))),
            packs.skipped(),
        )
    }

    @Test
    fun `a file whose name is not a state code is skipped`() {
        writePack(directory, testland, fileName = "testland.fbpack")

        val packs = Packs(directory)

        assertEquals(emptyList<PackSummary>(), packs.summaries())
        assertEquals(listOf(SkippedPack("testland.fbpack", SkipReason.NameNotAStateCode)), packs.skipped())
    }

    @Test
    fun `a file whose pack is for another state is skipped`() {
        writePack(directory, testland, fileName = "XX-OT.fbpack")

        val packs = Packs(directory)

        assertNull(packs.file("XX-OT"))
        assertNull(packs.file("XX-TS"))
        assertEquals(listOf(SkippedPack("XX-OT.fbpack", SkipReason.StateDoesNotMatchName)), packs.skipped())
    }

    @Test
    fun `a pack whose own state code is a path is never held`() {
        writePack(directory, testland.copy(state = PackState("../XX-TS", "Testland")), fileName = "XX-TS.fbpack")

        val packs = Packs(directory)

        assertNull(packs.file("XX-TS"))
        assertEquals(listOf(SkippedPack("XX-TS.fbpack", SkipReason.StateDoesNotMatchName)), packs.skipped())
    }

    @Test
    fun `files that are not pack files are passed over`() {
        writePack(directory, testland)
        write(directory, "README.txt", "made-up notes".toByteArray())
        write(directory, "XX-OT.fbpack.part", "half a pack".toByteArray())
        Files.createDirectory(directory.resolve("XX-ZZ.fbpack"))

        val packs = Packs(directory)

        assertEquals(listOf("XX-TS"), packs.summaries().map { it.stateCode })
        assertEquals(emptyList<SkippedPack>(), packs.skipped())
    }

    @Test
    fun `a state code that is not one is not looked up`() {
        writePack(directory, testland)
        val packs = Packs(directory)

        listOf("", "xx-ts", "XX-TS.fbpack", "../XX-TS", "XX-TS/..", "/etc/passwd", "XX-TS\u0000").forEach { code ->
            assertNull(code, packs.file(code))
        }
    }

    @Test
    fun `a file added after the start is held`() {
        val packs = Packs(directory)
        assertEquals(emptyList<PackSummary>(), packs.summaries())

        writePack(directory, testland)

        assertEquals(listOf("XX-TS"), packs.summaries().map { it.stateCode })
    }

    @Test
    fun `a changed file is read again, with a new version`() {
        writePack(directory, testland)
        val packs = Packs(directory)
        val before = packs.file("XX-TS")!!.version

        val rebuilt = writePack(directory, testland.copy(builtOn = LocalDate.of(2026, 4, 1)))

        val after = packs.file("XX-TS")!!
        assertNotEquals(before, after.version)
        assertEquals(Packs.version(rebuilt), after.version)
        assertEquals(LocalDate.of(2026, 4, 1), after.pack.builtOn)
    }

    @Test
    fun `a file that turns bad is dropped, and held again once it is fixed`() {
        writePack(directory, testland)
        val packs = Packs(directory)

        write(directory, "XX-TS.fbpack", "half a pack".toByteArray())
        assertNull(packs.file("XX-TS"))
        assertNull(packs.chartFor("XX-TS-001"))

        writePack(directory, testland)
        assertEquals(testland, packs.file("XX-TS")?.pack)
        assertEquals(emptyList<SkippedPack>(), packs.skipped())
    }

    @Test
    fun `a removed file is dropped`() {
        writePack(directory, testland)
        val packs = Packs(directory)

        Files.delete(directory.resolve("XX-TS.fbpack"))

        assertEquals(emptyList<PackSummary>(), packs.summaries())
        assertNull(packs.chartFor("XX-TS-001"))
    }

    @Test
    fun `a packs directory that is not there stops the start`() {
        assertThrows(IllegalArgumentException::class.java) { Packs(directory.resolve("missing")) }
    }
}
