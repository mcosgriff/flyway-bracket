package io.cosgriff.flywaybracket.server.account

import io.cosgriff.flywaybracket.api.CreateAccountResponse
import io.cosgriff.flywaybracket.api.ProfileResponse
import io.cosgriff.flywaybracket.api.UpdateProfileRequest
import io.cosgriff.flywaybracket.domain.place.CountyCode
import io.cosgriff.flywaybracket.domain.player.DisplayName
import io.cosgriff.flywaybracket.domain.player.DisplayNameCheck
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.UUID

/** A request signed by a player whose secret matched. */
data class SignedInPlayer(val id: UUID)

/** What became of a profile change. */
sealed interface ProfileUpdate {
    data object Updated : ProfileUpdate

    data object DisplayNameInvalid : ProfileUpdate

    data object HomeCountyInvalid : ProfileUpdate

    /** The account was deleted after the request was signed. */
    data object NoSuchPlayer : ProfileUpdate
}

/**
 * Anonymous device accounts (ADR 0001): an id and a secret, and nothing that names a person. Every call
 * blocks on the database and on the hash; call it off the request threads.
 *
 * @param leagues the player's league, shown with their profile and left when they delete their account
 * @param creationLimit counts account creations by the caller's address
 */
class Accounts(
    private val players: PlayerStore,
    private val leagues: PlayerLeagues,
    private val creationLimit: RateLimiter = FixedWindowRateLimiter(CREATIONS_PER_ADDRESS, CREATION_WINDOW),
    private val hasher: SecretHasher = Argon2idHasher(),
    private val random: SecureRandom = SecureRandom(),
) {
    /**
     * Checked against when the id names no player, so that an unknown id costs the same hash as a known
     * one: the time of a refusal does not tell whether the account exists.
     */
    private val noSuchPlayerHash = hasher.hash(newSecret())

    /** A new player and their secret, or null when [address] has made too many accounts just now. */
    fun create(address: String): CreateAccountResponse? {
        if (!creationLimit.tryAcquire(address)) return null
        val id = UUID.randomUUID()
        val secret = newSecret()
        players.create(id, hasher.hash(secret))
        return CreateAccountResponse(playerId = id.toString(), secret = secret)
    }

    /** The player, when [playerId] names one and [secret] is theirs; otherwise null. */
    fun authenticate(playerId: String, secret: String): SignedInPlayer? {
        val id = parseId(playerId)
        val stored = id?.let(players::secretHash)
        val matches = hasher.matches(secret, stored ?: noSuchPlayerHash)
        return if (id != null && stored != null && matches) SignedInPlayer(id) else null
    }

    /** The player's profile and league, or null when the account was deleted after the request was signed. */
    fun profile(player: SignedInPlayer): ProfileResponse? = players.profile(player.id)?.let {
        ProfileResponse(displayName = it.displayName, homeCounty = it.homeCounty, league = leagues.current(player.id))
    }

    /**
     * Checks both fields before changing either. A display name is stored trimmed. A home county need
     * only be well-formed, not a county of a pack the server holds.
     */
    fun update(player: SignedInPlayer, request: UpdateProfileRequest): ProfileUpdate {
        val displayName = request.displayName?.let { typed ->
            if (DisplayName.check(typed) != DisplayNameCheck.VALID) return ProfileUpdate.DisplayNameInvalid
            typed.trim()
        }
        val homeCounty = request.homeCounty?.also { code ->
            if (!CountyCode.isWellFormed(code)) return ProfileUpdate.HomeCountyInvalid
        }
        return if (players.update(player.id, displayName, homeCounty)) ProfileUpdate.Updated else ProfileUpdate.NoSuchPlayer
    }

    /**
     * Deletes the player. Cannot be undone: nothing else proves who they were. They leave their leagues
     * first, as leaving would take them out, so that a league whose start has arrived is settled with them
     * still counted; then deleting the row deletes their league places with it.
     */
    fun delete(player: SignedInPlayer) {
        leagues.leaveAll(player.id)
        players.delete(player.id)
    }

    private fun newSecret(): String {
        val bytes = ByteArray(SECRET_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        const val SECRET_BYTES = 32

        /** Enough for a household sharing one address to set up its phones; the beta needs no more. */
        const val CREATIONS_PER_ADDRESS = 10
        val CREATION_WINDOW: Duration = Duration.ofHours(1)

        /** Only the form the server writes, so one account has one id text. */
        private fun parseId(text: String): UUID? = try {
            UUID.fromString(text).takeIf { it.toString() == text }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
