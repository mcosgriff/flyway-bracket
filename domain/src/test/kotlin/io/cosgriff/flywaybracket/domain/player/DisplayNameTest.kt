package io.cosgriff.flywaybracket.domain.player

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNameTest {
    @Test
    fun `nothing typed is empty, and so is only spaces`() {
        assertEquals(DisplayNameCheck.EMPTY, DisplayName.check(""))
        assertEquals(DisplayNameCheck.EMPTY, DisplayName.check("   "))
    }

    @Test
    fun `one character is too short`() {
        assertEquals(DisplayNameCheck.TOO_SHORT, DisplayName.check("A"))
    }

    @Test
    fun `two to twenty-four characters are valid`() {
        assertEquals(DisplayNameCheck.VALID, DisplayName.check("Al"))
        assertEquals(DisplayNameCheck.VALID, DisplayName.check("Wren Example"))
        assertEquals(DisplayNameCheck.VALID, DisplayName.check("x".repeat(24)))
    }

    @Test
    fun `twenty-five characters are too long`() {
        assertEquals(DisplayNameCheck.TOO_LONG, DisplayName.check("x".repeat(25)))
    }

    @Test
    fun `spaces at the ends do not count`() {
        assertEquals(DisplayNameCheck.TOO_SHORT, DisplayName.check("  A  "))
        assertEquals(DisplayNameCheck.VALID, DisplayName.check("  " + "x".repeat(24) + "  "))
    }

    @Test
    fun `spaces inside the name count`() {
        assertEquals(DisplayNameCheck.TOO_LONG, DisplayName.check("x".repeat(12) + " " + "x".repeat(12)))
    }

    @Test
    fun `a character outside the basic plane counts once`() {
        // U+1F426 takes two UTF-16 units and is one character to the person typing it.
        assertEquals(DisplayNameCheck.TOO_SHORT, DisplayName.check("🐦"))
        assertEquals(DisplayNameCheck.VALID, DisplayName.check("🐦".repeat(24)))
    }
}
