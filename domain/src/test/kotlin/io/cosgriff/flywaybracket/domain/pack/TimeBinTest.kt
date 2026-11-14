package io.cosgriff.flywaybracket.domain.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class TimeBinTest {
    @Test
    fun `a month's four parts start on days 1, 8, 15 and 22`() {
        assertEquals(TimeBin.of(5, 1), TimeBin.of(LocalDate.of(2027, 5, 7)))
        assertEquals(TimeBin.of(5, 2), TimeBin.of(LocalDate.of(2027, 5, 8)))
        assertEquals(TimeBin.of(5, 3), TimeBin.of(LocalDate.of(2027, 5, 21)))
        assertEquals(TimeBin.of(5, 4), TimeBin.of(LocalDate.of(2027, 5, 22)))
    }

    @Test
    fun `the last part runs to the month's end`() {
        assertEquals(TimeBin.of(1, 4), TimeBin.of(LocalDate.of(2027, 1, 31)))
        assertEquals(TimeBin.of(2, 4), TimeBin.of(LocalDate.of(2028, 2, 29)))
    }

    @Test
    fun `a year has 48 bins, January first`() {
        assertEquals(0, TimeBin.of(LocalDate.of(2027, 1, 1)).index)
        assertEquals(47, TimeBin.of(LocalDate.of(2027, 12, 31)).index)
    }

    @Test
    fun `month and part read back`() {
        val bin = TimeBin.of(9, 3)
        assertEquals(9, bin.month)
        assertEquals(3, bin.part)
    }

    @Test
    fun `the year wraps`() {
        assertEquals(TimeBin.of(12, 4), TimeBin.of(1, 1).previous())
        assertEquals(TimeBin.of(1, 1), TimeBin.of(12, 4).next())
    }

    @Test
    fun `there is no bin outside the year`() {
        assertThrows(IllegalArgumentException::class.java) { TimeBin(48) }
        assertThrows(IllegalArgumentException::class.java) { TimeBin.of(13, 1) }
    }
}
