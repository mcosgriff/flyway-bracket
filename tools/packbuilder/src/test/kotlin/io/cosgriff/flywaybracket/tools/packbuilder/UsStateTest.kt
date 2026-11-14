package io.cosgriff.flywaybracket.tools.packbuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsStateTest {
    @Test
    fun `finds a state by its eBird region code`() {
        assertEquals(UsState("CO", "08", "Colorado"), UsState.ofRegionCode("US-CO"))
        assertEquals("56", UsState.ofRegionCode("US-WY")?.fips)
    }

    @Test
    fun `a code that is not a state's finds nothing`() {
        listOf("CO", "US-", "US-ZZ", "us-co", "US-CO-059", "").forEach { assertNull(it, UsState.ofRegionCode(it)) }
    }

    @Test
    fun `holds the fifty states and the District of Columbia, each once`() {
        assertEquals(51, UsState.all.size)
        assertEquals(51, UsState.all.map { it.postalCode }.toSet().size)
        assertEquals(51, UsState.all.map { it.fips }.toSet().size)
        assertEquals(51, UsState.all.map { it.name }.toSet().size)
        assertTrue(UsState.all.all { it.fips.matches(Regex("""\d{2}""")) && it.postalCode.matches(Regex("[A-Z]{2}")) })
    }
}
