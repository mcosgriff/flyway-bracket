package io.cosgriff.flywaybracket.server.account

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Turns a player's secret into what the server stores, and judges a secret sent back against it. */
interface SecretHasher {
    /** What to store for [secret]. Never the secret itself, and different each time for the same secret. */
    fun hash(secret: String): String

    /**
     * Whether [secret] is the one [stored] was made from. Takes the same time whether or not it is, so the
     * time of a refusal says nothing about how close a guess was.
     */
    fun matches(secret: String, stored: String): Boolean
}

/**
 * Argon2id (RFC 9106), stored as a PHC string: `$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>`. The
 * parameters are read back from the stored text, so raising them later leaves older hashes readable.
 *
 * Argon2id rather than bcrypt: it is the first choice of OWASP's password storage advice, it uses memory as
 * well as time so a stolen table is costly to search on a GPU, and it reads every byte of the secret where
 * bcrypt stops at 72. The defaults are OWASP's lowest recommended setting: a secret is 32 random bytes, so
 * the hash guards a stolen table, not a guessable password, and every signed request pays for one.
 */
class Argon2idHasher(
    private val memoryKib: Int = MEMORY_KIB,
    private val iterations: Int = ITERATIONS,
    private val parallelism: Int = PARALLELISM,
    private val random: SecureRandom = SecureRandom(),
) : SecretHasher {
    override fun hash(secret: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val derived = derive(secret, salt, memoryKib, iterations, parallelism, HASH_BYTES)
        return "\$$ID\$v=$VERSION\$m=$memoryKib,t=$iterations,p=$parallelism\$${encode(salt)}\$${encode(derived)}"
    }

    override fun matches(secret: String, stored: String): Boolean {
        val parsed = Stored.parse(stored) ?: return false
        val derived = derive(secret, parsed.salt, parsed.memoryKib, parsed.iterations, parsed.parallelism, parsed.hash.size)
        // Looks at every byte whatever the first difference: the time does not depend on where it is.
        return MessageDigest.isEqual(derived, parsed.hash)
    }

    private fun derive(secret: String, salt: ByteArray, memoryKib: Int, iterations: Int, parallelism: Int, length: Int): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(memoryKib)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .build()
        val out = ByteArray(length)
        Argon2BytesGenerator().apply { init(parameters) }.generateBytes(secret.toByteArray(Charsets.UTF_8), out)
        return out
    }

    /** A stored hash taken apart. */
    private class Stored(
        val memoryKib: Int,
        val iterations: Int,
        val parallelism: Int,
        val salt: ByteArray,
        val hash: ByteArray,
    ) {
        companion object {
            private val parameters = Regex("m=([0-9]{1,7}),t=([0-9]{1,3}),p=([0-9]{1,2})")

            /** Null for text that is not an Argon2id hash this class wrote. */
            fun parse(text: String): Stored? {
                val parts = text.split('$')
                if (parts.size != 6 || parts[0].isNotEmpty() || parts[1] != ID || parts[2] != "v=$VERSION") return null
                val (m, t, p) = parameters.matchEntire(parts[3])?.destructured ?: return null
                val salt = decode(parts[4]) ?: return null
                val hash = decode(parts[5])?.takeIf { it.isNotEmpty() } ?: return null
                return Stored(m.toInt(), t.toInt(), p.toInt(), salt, hash)
            }
        }
    }

    companion object {
        /** OWASP's lowest recommended Argon2id setting: 19 MiB, two passes, one lane. */
        const val MEMORY_KIB = 19 * 1024
        const val ITERATIONS = 2
        const val PARALLELISM = 1

        private const val ID = "argon2id"
        private const val VERSION = 19
        private const val SALT_BYTES = 16
        private const val HASH_BYTES = 32

        // PHC strings use standard Base64 with no padding.
        private fun encode(bytes: ByteArray): String = Base64.getEncoder().withoutPadding().encodeToString(bytes)

        private fun decode(text: String): ByteArray? = try {
            Base64.getDecoder().decode(text)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
