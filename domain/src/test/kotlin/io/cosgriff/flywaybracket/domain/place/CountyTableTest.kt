package io.cosgriff.flywaybracket.domain.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CountyTableTest {
    // Made-up places: the codes have the real shape and name no real state.
    private val text = "# a comment\nUS-ZA\tAlder\nUS-ZA-001\tHeron\nUS-ZA-003\tLark Hollow\n\nUS-ZB\tBirch\nUS-ZB-001\tHeron\n"
    private val table = CountyTableParser.parse(text)

    @Test
    fun `states keep their order and their counties`() {
        assertEquals(listOf("Alder", "Birch"), table.states.map { it.name })
        assertEquals(
            listOf(CountyEntry("US-ZA-001", "Heron"), CountyEntry("US-ZA-003", "Lark Hollow")),
            table.states[0].counties,
        )
        assertEquals(listOf(CountyEntry("US-ZB-001", "Heron")), table.states[1].counties)
    }

    @Test
    fun `a state's abbreviation is the end of its code`() {
        assertEquals("ZA", table.states[0].abbreviation)
    }

    @Test
    fun `a county is found by its code, with its state`() {
        val found = table.locate("US-ZB-001")
        assertEquals("Birch", found?.state?.name)
        assertEquals("Heron", found?.county?.name)
    }

    @Test
    fun `a code the table lacks is not found`() {
        assertNull(table.locate("US-ZA-005"))
        assertNull(table.locate("US-ZA"))
    }

    @Test
    fun `no lines make an empty table`() {
        assertEquals(emptyList<StateEntry>(), CountyTableParser.parse("").states)
    }

    @Test
    fun `Windows line ends are read`() {
        assertEquals("Heron", CountyTableParser.parse("US-ZA\tAlder\r\nUS-ZA-001\tHeron\r\n").locate("US-ZA-001")?.county?.name)
    }

    @Test
    fun `a county before any state is refused`() {
        assertThrows(IllegalArgumentException::class.java) { CountyTableParser.parse("US-ZA-001\tHeron") }
    }

    @Test
    fun `a county under another state is refused`() {
        assertThrows(IllegalArgumentException::class.java) { CountyTableParser.parse("US-ZA\tAlder\nUS-ZB-001\tHeron") }
    }

    @Test
    fun `a code listed twice is refused`() {
        assertThrows(IllegalArgumentException::class.java) { CountyTableParser.parse("US-ZA\tAlder\nUS-ZA-001\tHeron\nUS-ZA-001\tLark") }
    }

    @Test
    fun `a line without a name is refused`() {
        assertThrows(IllegalArgumentException::class.java) { CountyTableParser.parse("US-ZA") }
        assertThrows(IllegalArgumentException::class.java) { CountyTableParser.parse("US-ZA\t ") }
    }

    @Test
    fun `a code of another shape is refused`() {
        assertThrows(IllegalArgumentException::class.java) { CountyTableParser.parse("Alder\tAlder") }
        assertThrows(IllegalArgumentException::class.java) { CountyTableParser.parse("US-ZA\tAlder\nUS-ZA-01\tHeron") }
    }
}
