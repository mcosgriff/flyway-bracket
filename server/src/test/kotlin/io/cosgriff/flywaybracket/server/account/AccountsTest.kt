package io.cosgriff.flywaybracket.server.account

import io.cosgriff.flywaybracket.api.ProfileResponse
import io.cosgriff.flywaybracket.api.UpdateProfileRequest
import io.cosgriff.flywaybracket.server.league.InMemoryLeagueStore
import io.cosgriff.flywaybracket.server.league.Leagues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64
import java.util.UUID

/** Accounts over a store in memory. All names, codes and secrets are made up. */
class AccountsTest {
    /** The real hash, counting how often a secret is checked. */
    private class CountingHasher : SecretHasher {
        private val real = Argon2idHasher()
        var checks = 0

        override fun hash(secret: String): String = real.hash(secret)

        override fun matches(secret: String, stored: String): Boolean {
            checks++
            return real.matches(secret, stored)
        }
    }

    private val store = InMemoryPlayerStore()
    private val hasher = CountingHasher()
    private val leagues = Leagues(InMemoryLeagueStore(store))
    private val accounts = Accounts(store, leagues, creationLimit = { true }, hasher = hasher)

    private fun newPlayer(): Pair<String, String> {
        val created = checkNotNull(accounts.create(ADDRESS))
        return created.playerId to created.secret
    }

    private fun signedIn(): SignedInPlayer {
        val (id, secret) = newPlayer()
        return checkNotNull(accounts.authenticate(id, secret))
    }

    @Test
    fun `a secret is 32 random bytes in URL-safe Base64 with no padding`() {
        val (_, secret) = newPlayer()
        val (_, other) = newPlayer()

        assertEquals(43, secret.length)
        assertEquals(32, Base64.getUrlDecoder().decode(secret).size)
        assertNotEquals(secret, other)
    }

    @Test
    fun `the store keeps a hash of the secret, never the secret`() {
        val (id, secret) = newPlayer()

        val stored = checkNotNull(store.secretHash(UUID.fromString(id)))

        assertFalse(stored.contains(secret))
        assertEquals(true, Argon2idHasher().matches(secret, stored))
    }

    @Test
    fun `a new account holds nothing about the player`() {
        assertEquals(ProfileResponse(), accounts.profile(signedIn()))
    }

    @Test
    fun `the right id and secret sign in`() {
        val (id, secret) = newPlayer()

        assertEquals(SignedInPlayer(UUID.fromString(id)), accounts.authenticate(id, secret))
    }

    @Test
    fun `a wrong secret, another player's secret or an unknown id does not`() {
        val (id, secret) = newPlayer()
        val (otherId, otherSecret) = newPlayer()

        // The secret with its last character changed.
        val wrong = secret.dropLast(1) + if (secret.endsWith("A")) "B" else "A"

        assertNull(accounts.authenticate(id, wrong))
        assertNull(accounts.authenticate(id, otherSecret))
        assertNull(accounts.authenticate(otherId, secret))
        assertNull(accounts.authenticate(UUID.randomUUID().toString(), secret))
        assertNull(accounts.authenticate("not-an-id", secret))
        assertNull(accounts.authenticate(id.uppercase(), secret))
        assertNull(accounts.authenticate(id, ""))
    }

    @Test
    fun `an unknown or malformed id is checked against a hash all the same`() {
        val (_, secret) = newPlayer()
        hasher.checks = 0

        accounts.authenticate(UUID.randomUUID().toString(), secret)
        accounts.authenticate("not-an-id", secret)

        assertEquals(2, hasher.checks)
    }

    @Test
    fun `a deleted account no longer signs in`() {
        val (id, secret) = newPlayer()
        val player = checkNotNull(accounts.authenticate(id, secret))

        accounts.delete(player)

        assertNull(accounts.authenticate(id, secret))
        assertNull(accounts.profile(player))
        assertEquals(ProfileUpdate.NoSuchPlayer, accounts.update(player, UpdateProfileRequest(displayName = "Player One")))
    }

    @Test
    fun `deleting one account leaves the others`() {
        val (id, secret) = newPlayer()

        accounts.delete(signedIn())

        assertNotNull(accounts.authenticate(id, secret))
    }

    @Test
    fun `the display name is stored trimmed and the county as sent`() {
        val player = signedIn()

        val result = accounts.update(player, UpdateProfileRequest(displayName = "  Player One ", homeCounty = "US-ZZ-001"))

        assertEquals(ProfileUpdate.Updated, result)
        assertEquals(ProfileResponse(displayName = "Player One", homeCounty = "US-ZZ-001"), accounts.profile(player))
    }

    @Test
    fun `a field left out is not changed`() {
        val player = signedIn()
        accounts.update(player, UpdateProfileRequest(displayName = "Player One", homeCounty = "US-ZZ-001"))

        accounts.update(player, UpdateProfileRequest(homeCounty = "US-ZZ-003"))
        accounts.update(player, UpdateProfileRequest(displayName = "Player Two"))
        accounts.update(player, UpdateProfileRequest())

        assertEquals(ProfileResponse(displayName = "Player Two", homeCounty = "US-ZZ-003"), accounts.profile(player))
    }

    @Test
    fun `a display name outside 2 to 24 characters changes nothing`() {
        val player = signedIn()
        accounts.update(player, UpdateProfileRequest(displayName = "Player One", homeCounty = "US-ZZ-001"))

        listOf("", "   ", "P", " P ", "P".repeat(25)).forEach { name ->
            val result = accounts.update(player, UpdateProfileRequest(displayName = name, homeCounty = "US-ZZ-003"))
            assertEquals(name, ProfileUpdate.DisplayNameInvalid, result)
        }

        assertEquals(ProfileResponse(displayName = "Player One", homeCounty = "US-ZZ-001"), accounts.profile(player))
    }

    @Test
    fun `display names of 2 and 24 characters are allowed`() {
        val player = signedIn()

        assertEquals(ProfileUpdate.Updated, accounts.update(player, UpdateProfileRequest(displayName = "Pl")))
        assertEquals(ProfileUpdate.Updated, accounts.update(player, UpdateProfileRequest(displayName = "P".repeat(24))))
    }

    @Test
    fun `a home county must be well-formed, and changes nothing when it is not`() {
        val player = signedIn()

        listOf("", "US-ZZ", "us-zz-001", "US-ZZ-1", "Heron County").forEach { code ->
            val result = accounts.update(player, UpdateProfileRequest(displayName = "Player One", homeCounty = code))
            assertEquals(code, ProfileUpdate.HomeCountyInvalid, result)
        }

        assertEquals(ProfileResponse(), accounts.profile(player))
    }

    @Test
    fun `a well-formed county need not be in a pack`() {
        val player = signedIn()

        // No pack has a state ZZ.
        assertEquals(ProfileUpdate.Updated, accounts.update(player, UpdateProfileRequest(homeCounty = "US-ZZ-999")))
    }

    @Test
    fun `creating accounts stops at the rate limit for that address`() {
        val limited = Accounts(InMemoryPlayerStore(), leagues, creationLimit = FixedWindowRateLimiter(2, Accounts.CREATION_WINDOW))

        assertNotNull(limited.create(ADDRESS))
        assertNotNull(limited.create(ADDRESS))
        assertNull(limited.create(ADDRESS))
        assertNotNull(limited.create(OTHER_ADDRESS))
    }

    private companion object {
        const val ADDRESS = "192.0.2.10"
        const val OTHER_ADDRESS = "198.51.100.20"
    }
}
