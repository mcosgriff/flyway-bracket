package io.cosgriff.flywaybracket.domain.place

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The codes have the real shape; `ZA` names no real state. */
class CountyCodeTest {
    @Test
    fun `a country, a state and a three-digit county`() {
        assertTrue(CountyCode.isWellFormed("US-ZA-001"))
        assertTrue(CountyCode.isWellFormed("US-ZA-999"))
    }

    @Test
    fun `anything else is not a county code`() {
        listOf(
            "",
            "US-ZA",
            "US-ZA-01",
            "US-ZA-0001",
            "us-za-001",
            "US-ZA-00A",
            "US_ZA_001",
            " US-ZA-001",
            "US-ZA-001 ",
            "US-ZA-001\n",
        ).forEach { assertFalse(it, CountyCode.isWellFormed(it)) }
    }
}
