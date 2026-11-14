package io.cosgriff.flywaybracket.domain.outing

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

// Bird names, county codes and dates are made up.
class SeasonFiguresTest {
    private val zone = ZoneId.of("America/Denver")
    private val since = LocalDate.of(2027, 1, 1)
    private var nextId = 1L

    private fun log(date: LocalDate, vararg birds: String, at: LocalTime = LocalTime.NOON): OutingLog {
        val id = nextId++
        val start = date.atTime(at).atZone(zone).toInstant()
        return OutingLog(
            Outing(id, "", "US-ZZ-001", start, start.plusSeconds(1800)),
            birds.map { Sighting(nextId++, id, it, count = 2, seenAt = start) },
        )
    }

    private val logs = listOf(
        log(LocalDate.of(2026, 10, 3), "Test Warbler", "Sample Sparrow"),
        log(LocalDate.of(2027, 1, 1), "Sample Sparrow", "Made-up Heron"),
        log(LocalDate.of(2027, 3, 14), "Made-up Heron", "Example Tanager"),
        log(LocalDate.of(2027, 4, 2)),
    )

    @Test
    fun `species since a date are counted once however often they were seen`() {
        assertEquals(setOf("Sample Sparrow", "Made-up Heron", "Example Tanager"), speciesSince(logs, since, zone))
    }

    @Test
    fun `outings since a date include the date itself and an outing with no birds`() {
        assertEquals(3, outingsSince(logs, since, zone))
        assertEquals(1, outingsSince(logs, LocalDate.of(2027, 3, 15), zone))
    }

    @Test
    fun `a new lifer was never logged before the period`() {
        // Sample Sparrow was seen in 2026, so it is not new.
        assertEquals(setOf("Made-up Heron", "Example Tanager"), newLifersSince(logs, since, zone))
    }

    @Test
    fun `with nothing logged before the period every species is a lifer`() {
        val early = LocalDate.of(2026, 1, 1)
        assertEquals(speciesSince(logs, early, zone), newLifersSince(logs, early, zone))
    }

    @Test
    fun `no outings give no figures`() {
        assertEquals(emptySet<String>(), speciesSince(emptyList(), since, zone))
        assertEquals(0, outingsSince(emptyList(), since, zone))
        assertEquals(emptySet<String>(), newLifersSince(emptyList(), since, zone))
    }

    @Test
    fun `the player's zone decides which side of the date an outing falls`() {
        // 23:30 on 31 December in Denver is already 1 January in UTC.
        val newYearsEve = listOf(log(LocalDate.of(2026, 12, 31), "Test Warbler", at = LocalTime.of(23, 30)))
        assertEquals(0, outingsSince(newYearsEve, since, zone))
        assertEquals(1, outingsSince(newYearsEve, since, ZoneId.of("UTC")))
    }
}
