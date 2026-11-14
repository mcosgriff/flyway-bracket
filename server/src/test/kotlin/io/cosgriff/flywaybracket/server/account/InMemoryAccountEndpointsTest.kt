package io.cosgriff.flywaybracket.server.account

import io.cosgriff.flywaybracket.api.ApiPaths
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.Base64

/** The account endpoints over a store in memory: these run without Docker. */
class InMemoryAccountEndpointsTest : AccountEndpointsTest() {
    override fun store(): PlayerStore = InMemoryPlayerStore()

    @Test
    fun `no log line has a secret or an Authorization header`() {
        // The server's log (slf4j-simple) goes to standard error, read again at every line.
        val log = ByteArrayOutputStream()
        val standardError = System.err
        System.setErr(PrintStream(log, true, Charsets.UTF_8))
        val secrets = mutableListOf<String>()
        try {
            server {
                val account = newAccount()
                val wrong = account.copy(secret = WRONG_SECRET)
                secrets += account.secret
                secrets += Base64.getEncoder().encodeToString("${account.playerId}:${account.secret}".toByteArray())
                secrets += Base64.getEncoder().encodeToString("${account.playerId}:$WRONG_SECRET".toByteArray())

                profile(account)
                patch(account, """{"displayName":"Player One"}""")
                client.get(ApiPaths.ACCOUNT) { signedBy(wrong) }
                client.get(ApiPaths.ACCOUNT) { header(HttpHeaders.Authorization, "Bearer ${account.playerId}.${account.secret}") }
                client.delete(ApiPaths.ACCOUNT) { signedBy(account) }
                client.get(ApiPaths.ACCOUNT) { signedBy(account) }
            }
        } finally {
            System.setErr(standardError)
        }
        val text = log.toString(Charsets.UTF_8)

        // The log was caught: it has the request lines.
        assertTrue(text, text.contains("201 POST ${ApiPaths.ACCOUNTS}"))
        assertTrue(text, text.contains("401 GET ${ApiPaths.ACCOUNT}"))
        (secrets + WRONG_SECRET).forEach { assertFalse(text, text.contains(it)) }
        assertFalse(text, text.contains(HttpHeaders.Authorization, ignoreCase = true))
        assertFalse(text, text.contains("Basic "))
        assertFalse(text, text.contains("Bearer"))
    }

    private companion object {
        const val WRONG_SECRET = "made-up-wrong-secret-6d3a"
    }
}
