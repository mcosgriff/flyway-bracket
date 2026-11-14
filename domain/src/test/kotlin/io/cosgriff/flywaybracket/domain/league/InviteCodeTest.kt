package io.cosgriff.flywaybracket.domain.league

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class InviteCodeTest {
    @Test
    fun `a code is 8 characters from the alphabet`() {
        val random = SecureRandom()
        repeat(100) {
            val code = InviteCode.random(random)
            assertEquals(InviteCode.LENGTH, code.length)
            assertTrue(code, code.all { it in InviteCode.ALPHABET })
            assertEquals(code, InviteCode.normalize(code))
        }
    }

    @Test
    fun `the alphabet has no look-alikes`() {
        "0O1IL".forEach { assertTrue("$it", it !in InviteCode.ALPHABET) }
    }

    @Test
    fun `a typed code is read without case and without spaces at either end`() {
        assertEquals("WREN4825", InviteCode.normalize("  wren4825 "))
        assertEquals("WREN4825", InviteCode.normalize("Wren4825"))
    }

    @Test
    fun `text that cannot be a code is not one`() {
        assertNull(InviteCode.normalize(""))
        assertNull(InviteCode.normalize("WREN482"))
        assertNull(InviteCode.normalize("WREN48252"))
        // A hyphen, a space inside, and look-alikes are not in the alphabet.
        assertNull(InviteCode.normalize("WREN-482"))
        assertNull(InviteCode.normalize("WREN 482"))
        assertNull(InviteCode.normalize("WREN4810"))
        assertNull(InviteCode.normalize("WRENLOIS"))
    }
}
