package io.cosgriff.flywaybracket.server.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The secrets are made up. */
class Argon2idHasherTest {
    private val hasher = Argon2idHasher()
    private val secret = "made-up-secret-0123456789abcdefghijklmnopq"

    @Test
    fun `a hash is an Argon2id PHC string with the default parameters, and not the secret`() {
        val stored = hasher.hash(secret)

        assertTrue(stored, stored.startsWith("\$argon2id\$v=19\$m=19456,t=2,p=1\$"))
        assertFalse(stored.contains(secret))
    }

    @Test
    fun `the secret it was made from matches`() {
        assertTrue(hasher.matches(secret, hasher.hash(secret)))
    }

    @Test
    fun `any other secret does not`() {
        val stored = hasher.hash(secret)

        assertFalse(hasher.matches(secret.dropLast(1) + "r", stored))
        assertFalse(hasher.matches("", stored))
        assertFalse(hasher.matches(secret + secret, stored))
    }

    @Test
    fun `the same secret hashes differently each time, and both match`() {
        val first = hasher.hash(secret)
        val second = hasher.hash(secret)

        assertNotEquals(first, second)
        assertTrue(hasher.matches(secret, first))
        assertTrue(hasher.matches(secret, second))
    }

    @Test
    fun `a hash made with other parameters is read with its own`() {
        val stored = Argon2idHasher(memoryKib = 8 * 1024, iterations = 1, parallelism = 1).hash(secret)

        assertTrue(stored, stored.startsWith("\$argon2id\$v=19\$m=8192,t=1,p=1\$"))
        assertTrue(hasher.matches(secret, stored))
    }

    @Test
    fun `stored text that is not a hash matches nothing`() {
        val good = hasher.hash(secret)
        listOf(
            "",
            secret,
            good.replace("argon2id", "argon2i"),
            good.replace("v=19", "v=16"),
            good.replace("m=19456", "m=lots"),
            good.substringBeforeLast('$'),
            good.substringBeforeLast('$') + "$",
            good.substringBeforeLast('$') + "\$not*base64",
        ).forEach { assertFalse(it, hasher.matches(secret, it)) }
    }
}
