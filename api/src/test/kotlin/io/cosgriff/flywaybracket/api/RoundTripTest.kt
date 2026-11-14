package io.cosgriff.flywaybracket.api

import io.cosgriff.flywaybracket.domain.bracket.Decision
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Season
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Every message survives being written as JSON and read back. All names and figures are made up. */
class RoundTripTest {
    private inline fun <reified T> roundTrip(value: T): String {
        val json = ApiJson.encodeToString(value)
        assertEquals(value, ApiJson.decodeFromString<T>(json))
        return json
    }

    private val summary = LeagueSummary(
        id = "league-1",
        name = "Thursday Walkers",
        inviteCode = "HERON7",
        players = 2,
        capacity = 16,
        state = LeagueState.BEFORE_SEASON,
    )

    private val standings = listOf(
        Standing(displayName = "Player One", points = 12, rank = 1, you = true),
        Standing(displayName = "Player Two", points = 9, rank = 2),
    )

    /** One made-up bird per slot. */
    private val picks = MatchupSlot.all.associateWith { "Bird ${it.index}" }

    @Test
    fun `an error`() {
        val json = roundTrip(ApiError(ApiErrorCode.LEAGUE_FULL, detail = "16 of 16"))

        assertEquals("""{"code":"LEAGUE_FULL","detail":"16 of 16"}""", json)
        roundTrip(ApiError(ApiErrorCode.UNAUTHORIZED))
    }

    @Test
    fun `an error's code is one of the fixed list, or unknown to this build`() {
        ApiErrorCode.entries.forEach { assertEquals(it, ApiError(it).errorCode) }

        val fromANewerServer = ApiJson.decodeFromString<ApiError>("""{"code":"NOT_INVENTED_YET"}""")
        assertNull(fromANewerServer.errorCode)
    }

    @Test
    fun `the health check`() {
        val json = roundTrip(HealthResponse(version = "0.1.0", database = true))

        assertEquals("""{"version":"0.1.0","database":true}""", json)
    }

    @Test
    fun `creating an account`() {
        assertEquals("{}", roundTrip(CreateAccountRequest))
        roundTrip(CreateAccountResponse(playerId = "player-1", secret = "made-up-secret"))
    }

    @Test
    fun `updating a profile`() {
        roundTrip(UpdateProfileRequest(displayName = "Player One", homeCounty = "US-ZZ-001"))
        assertEquals("""{"displayName":"Player One"}""", roundTrip(UpdateProfileRequest(displayName = "Player One")))
    }

    @Test
    fun `reading a profile`() {
        roundTrip(ProfileResponse(displayName = "Player One", homeCounty = "US-ZZ-001", league = summary))
        assertEquals("{}", roundTrip(ProfileResponse()))
    }

    @Test
    fun `creating and joining a league`() {
        val json = roundTrip(
            CreateLeagueRequest(
                name = "Thursday Walkers",
                open = false,
                timeZone = "America/Denver",
                seasonStart = LocalDate.of(2027, 3, 1),
            ),
        )

        assertTrue(json, json.contains(""""seasonStart":"2027-03-01""""))
        roundTrip(JoinLeagueRequest(inviteCode = "HERON7"))
    }

    @Test
    fun `a league summary, with and without its invite code`() {
        roundTrip(summary)
        roundTrip(summary.copy(inviteCode = null))
        roundTrip(LeagueListResponse(listOf(summary, summary.copy(id = "league-2", state = LeagueState.FINISHED))))
        roundTrip(LeagueListResponse(emptyList()))
    }

    @Test
    fun `a league's detail`() {
        roundTrip(
            LeagueDetail(
                league = summary.copy(state = LeagueState.IN_SEASON),
                season = Season.SPRING,
                timeZone = "America/Denver",
                seasonStart = LocalDate.of(2027, 3, 1),
                seasonEnd = LocalDate.of(2027, 4, 11),
                members = standings,
            ),
        )
    }

    @Test
    fun `a bracket`() {
        val teams = listOf("North", "East", "South", "West").flatMap { quarter ->
            (1..16).map { seed -> BracketTeam(commonName = "$quarter Bird $seed", quarter = quarter, seed = seed) }
        }
        val matchups = MatchupSlot.all.map { slot ->
            when (slot.round) {
                Round.ROUND_OF_64 -> BracketMatchup(slot, upper = "Upper ${slot.index}", lower = "Lower ${slot.index}", winner = "Upper ${slot.index}")
                Round.ROUND_OF_32 -> BracketMatchup(slot, upper = "Upper ${slot.index}")
                else -> BracketMatchup(slot)
            }
        }

        roundTrip(BracketResponse(teams, matchups))
        assertEquals("""{"slot":62}""", roundTrip(BracketMatchup(MatchupSlot(62))))
    }

    @Test
    fun `picks are keyed by slot`() {
        val request = SubmitPicksRequest(picks)

        val json = roundTrip(request)

        assertTrue(request.isComplete)
        assertFalse(SubmitPicksRequest(picks - MatchupSlot(62)).isComplete)
        assertEquals(MatchupSlot.all.map { it.index.toString() }, ApiJson.parseToJsonElement(json).jsonObject.getValue("picks").jsonObject.keys.toList())
        roundTrip(PicksResponse(picks, locked = true))
        roundTrip(PicksResponse(emptyMap(), locked = false))
    }

    @Test
    fun `a pick for a slot that does not exist is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiJson.decodeFromString<SubmitPicksRequest>("""{"picks":{"63":"Bird"}}""")
        }
    }

    @Test
    fun `round results, with every reason`() {
        val results = Decision.Reason.entries.mapIndexed { position, reason ->
            MatchupResult(
                slot = MatchupSlot.of(Round.ROUND_OF_16, position),
                upper = BirdTally(commonName = "Upper Bird", eligible = 8, logged = 5),
                lower = BirdTally(commonName = "Lower Bird", eligible = 6, logged = 2),
                winner = "Upper Bird",
                reason = reason,
                roundPoints = 4,
                upsetBonus = 7,
            )
        }

        val json = roundTrip(RoundResultsResponse(Round.ROUND_OF_16, results))

        assertTrue(json, json.contains(""""round":"ROUND_OF_16""""))
        assertTrue(json, json.contains(""""reason":"TIED_BETTER_SEED""""))
    }

    @Test
    fun standings() {
        roundTrip(StandingsResponse(standings))
    }

    @Test
    fun `uploading sightings`() {
        roundTrip(
            UploadSightingsRequest(
                listOf(
                    SightingUpload("6f1c0d4e-0000-4000-8000-000000000001", "Upper Bird", "US-ZZ-001", LocalDate.of(2027, 3, 4)),
                    SightingUpload("6f1c0d4e-0000-4000-8000-000000000002", "Lower Bird", "US-ZZ-003", LocalDate.of(2027, 3, 5)),
                ),
            ),
        )
        roundTrip(UploadSightingsRequest(emptyList()))
    }

    /** ADR 0001: no coordinates, no time of day, no typed place name. A new field must change the ADR first. */
    @Test
    fun `a sighting carries its bird, county, date and id, and nothing else`() {
        val json = ApiJson.encodeToString(
            SightingUpload("6f1c0d4e-0000-4000-8000-000000000001", "Upper Bird", "US-ZZ-001", LocalDate.of(2027, 3, 4)),
        )

        assertEquals(
            """{"clientId":"6f1c0d4e-0000-4000-8000-000000000001","commonName":"Upper Bird","countyCode":"US-ZZ-001","date":"2027-03-04"}""",
            json,
        )
        assertEquals(setOf("clientId", "commonName", "countyCode", "date"), (ApiJson.parseToJsonElement(json) as JsonObject).keys)
    }

    @Test
    fun `the pack list`() {
        val json = roundTrip(
            PackListResponse(
                listOf(
                    PackSummary(stateCode = "US-ZZ", name = "Made-up State", sizeBytes = 3_400_000_000, builtOn = LocalDate.of(2027, 1, 15), version = "2027-01-15.1"),
                ),
            ),
        )

        assertTrue(json, json.contains(""""builtOn":"2027-01-15""""))
    }

    @Test
    fun `a field this build does not know is skipped`() {
        assertEquals(
            JoinLeagueRequest("HERON7"),
            ApiJson.decodeFromString<JoinLeagueRequest>("""{"inviteCode":"HERON7","addedLater":true}"""),
        )
    }

    @Serializable
    private data class Stamped(val on: ApiDate, val at: ApiInstant)

    @Test
    fun `dates are ISO local dates and instants are ISO-8601 in UTC`() {
        val json = roundTrip(Stamped(on = LocalDate.of(2027, 3, 1), at = Instant.parse("2027-03-01T00:00:00-07:00")))

        assertEquals("""{"on":"2027-03-01","at":"2027-03-01T07:00:00Z"}""", json)
    }
}
