package io.cosgriff.flywaybracket.server.sighting

import io.cosgriff.flywaybracket.api.SightingUpload
import io.cosgriff.flywaybracket.api.UploadSightingsRequest
import io.cosgriff.flywaybracket.server.account.SignedInPlayer
import io.cosgriff.flywaybracket.server.pack.PackCatalog
import io.cosgriff.flywaybracket.server.pack.Packs
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** What became of an upload. Every outcome but [STORED] stored nothing. */
enum class UploadOutcome {
    /** Every sighting is stored, or was already. */
    STORED,

    /** More than [UploadSightingsRequest.MAX_SIGHTINGS]. */
    TOO_MANY,

    /** A sighting's id is not a UUID. */
    ID_INVALID,

    /** A sighting's county is not a county of any pack the server holds. */
    COUNTY_UNKNOWN,

    /** A sighting's bird is named in no county's chart of any pack the server holds. */
    BIRD_UNKNOWN,

    /** A sighting's date is more than one day after today in the earliest time zone on Earth. */
    DATE_IN_FUTURE,

    /** The account was deleted after the request was signed. */
    NO_SUCH_PLAYER,
}

/**
 * The sightings players upload (#36): a bird's name, a county and a date, and nothing else (ADR 0001).
 * A sighting belongs to the player, not to a league, and no other player is ever given it; a league sees
 * only totals, once rounds are decided on the server (#37). Every call blocks on the database; call it
 * off the request threads.
 *
 * @param packs which counties and birds the server knows
 * @param clock today, for refusing a date in the future
 */
class Sightings(
    private val store: SightingStore,
    private val packs: Packs,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Stores the sightings, all or none. Each is checked before any is stored; the outcome is the first
     * refusal, in the order the sightings were sent and, within one, in the order of [UploadOutcome]. A
     * sighting whose id the player already used changes nothing, so a batch sent again is safe.
     *
     * A sighting dated in a round that is already decided is stored like any other. It must not change
     * that round's result: deciding rounds (#37) reads the sightings once, 12 hours after the round's Sunday
     * ends, and keeps the result.
     */
    fun upload(player: SignedInPlayer, request: UploadSightingsRequest): UploadOutcome {
        if (request.sightings.size > UploadSightingsRequest.MAX_SIGHTINGS) return UploadOutcome.TOO_MANY
        val latest = latestDate()
        val catalog = packs.catalog()
        val stored = ArrayList<StoredSighting>(request.sightings.size)
        for (sighting in request.sightings) {
            when (val checked = check(sighting, catalog, latest)) {
                is Checked.Valid -> stored += checked.sighting
                is Checked.Refused -> return checked.outcome
            }
        }
        return if (store.add(player.id, stored)) UploadOutcome.STORED else UploadOutcome.NO_SUCH_PLAYER
    }

    /**
     * Deletes one of the player's own sightings.
     *
     * @return false when the player has no sighting with this id: none was sent, it was deleted already, or
     *   it is another player's. The three are not told apart.
     */
    fun delete(player: SignedInPlayer, clientId: String): Boolean {
        val id = parseId(clientId) ?: return false
        return store.delete(player.id, id)
    }

    private fun check(sighting: SightingUpload, catalog: PackCatalog, latest: LocalDate): Checked {
        val id = parseId(sighting.clientId) ?: return Checked.Refused(UploadOutcome.ID_INVALID)
        if (sighting.countyCode !in catalog.counties) return Checked.Refused(UploadOutcome.COUNTY_UNKNOWN)
        if (sighting.commonName !in catalog.birds) return Checked.Refused(UploadOutcome.BIRD_UNKNOWN)
        if (sighting.date > latest) return Checked.Refused(UploadOutcome.DATE_IN_FUTURE)
        return Checked.Valid(StoredSighting(id, sighting.commonName, sighting.countyCode, sighting.date))
    }

    /**
     * The latest date a sighting may have: one day after today in the earliest time zone on Earth. The
     * date is the day where the player was, and a sighting counts in every league the player is in, so no
     * one league's zone applies; no player anywhere has a date later than today there.
     */
    private fun latestDate(): LocalDate = LocalDate.now(clock.withZone(EARLIEST_ZONE)).plusDays(1)

    private sealed interface Checked {
        data class Valid(val sighting: StoredSighting) : Checked

        data class Refused(val outcome: UploadOutcome) : Checked
    }

    companion object {
        /** UTC+14, the Line Islands: the first place on Earth where each day begins. */
        val EARLIEST_ZONE: ZoneOffset = ZoneOffset.ofHours(14)

        /** A UUID in its usual text form, in either case; null for anything else. */
        private fun parseId(text: String): UUID? = try {
            UUID.fromString(text).takeIf { it.toString() == text.lowercase() }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
