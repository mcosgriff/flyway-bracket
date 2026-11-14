package io.cosgriff.flywaybracket.server.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/** The ids and secrets are made up. */
class BasicCredentialsTest {
    private fun basic(text: String) = "Basic " + Base64.getEncoder().encodeToString(text.toByteArray())

    @Test
    fun `the user and the password, split at the first colon`() {
        assertEquals("player-1" to "made-up-secret", basicCredentials(basic("player-1:made-up-secret")))
        assertEquals("player-1" to "made:up", basicCredentials(basic("player-1:made:up")))
        assertEquals("player-1" to "", basicCredentials(basic("player-1:")))
    }

    @Test
    fun `the scheme is read whatever its case`() {
        assertEquals("player-1" to "made-up-secret", basicCredentials(basic("player-1:made-up-secret").replace("Basic", "bAsIc")))
    }

    @Test
    fun `anything else is no credentials`() {
        listOf(
            null,
            "",
            "Basic",
            "Basic ",
            "Basic not*base64",
            basic("no-colon"),
            "Bearer player-1.made-up-secret",
            basic("player-1:made-up-secret").removePrefix("Basic "),
        ).forEach { assertNull(it, basicCredentials(it)) }
    }
}
