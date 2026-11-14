package io.cosgriff.flywaybracket.domain.outing

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// Places, bird names, county codes and times are made up.
class OutingTest {
    private val start = Instant.parse("2027-04-18T03:30:00Z")

    private fun outing(endedAt: Instant? = null) = Outing(1, "Test Pond", "US-ZZ-001", start, endedAt)

    @Test
    fun `an outing with no end is in progress`() {
        assertTrue(outing().inProgress)
        assertFalse(outing(endedAt = start.plusSeconds(600)).inProgress)
    }

    @Test
    fun `an outing cannot end before it starts`() {
        assertThrows(IllegalArgumentException::class.java) { outing(endedAt = start.minusSeconds(1)) }
    }

    @Test
    fun `the date is the local date the outing started`() {
        assertEquals(LocalDate.of(2027, 4, 18), outing().date(ZoneOffset.UTC))
        // 03:30 UTC is still the evening before in Denver.
        assertEquals(LocalDate.of(2027, 4, 17), outing().date(ZoneId.of("America/Denver")))
    }

    @Test
    fun `an outing that runs past midnight keeps its starting date`() {
        val late = Outing(1, "", "US-ZZ-001", Instant.parse("2027-04-18T23:50:00Z"), Instant.parse("2027-04-19T00:40:00Z"))
        assertEquals(LocalDate.of(2027, 4, 18), late.date(ZoneOffset.UTC))
    }

    @Test
    fun `a sighting counts at least one bird`() {
        assertEquals(1, Sighting(1, 1, "Test Warbler", count = 1, seenAt = start).count)
        assertThrows(IllegalArgumentException::class.java) { Sighting(1, 1, "Test Warbler", count = 0, seenAt = start) }
    }

    @Test
    fun `a log's species are its birds by name`() {
        val log = OutingLog(
            outing(),
            listOf(Sighting(1, 1, "Test Warbler", 3, start), Sighting(2, 1, "Sample Sparrow", 1, start)),
        )
        assertEquals(setOf("Test Warbler", "Sample Sparrow"), log.species)
    }

    @Test
    fun `a log's birds are its sightings' counts added up`() {
        val log = OutingLog(
            outing(),
            listOf(Sighting(1, 1, "Test Warbler", 3, start), Sighting(2, 1, "Sample Sparrow", 1, start)),
        )
        assertEquals(4, log.birds)
        assertEquals(0, OutingLog(outing(), emptyList()).birds)
    }
}
