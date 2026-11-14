# The server's API

What the app and the server say to each other. The types are in the `:api` module
(`api/src/main/kotlin/io/cosgriff/flywaybracket/api/`), which both sides compile against; this page lists
the endpoints that carry them. Change the two together.

Nothing here is built yet: no server exists and the app makes no network call. The endpoints are built in
the server issues. What the app may send is decided by [ADR 0001](../adr/0001-server-and-network.md); a
new field sent to the server needs a change to that record first.

## Rules for every call

- One host, HTTPS only. Every path starts with `/v1/` (`ApiPaths`).
- Bodies are JSON, read and written with `ApiJson`. A reader skips a key it does not know, so a field can
  be added without a new version. A field that is null is left out.
- A date is an ISO local date (`2027-03-01`). An instant is ISO-8601 in UTC (`2027-03-01T07:00:00Z`). No
  message carries an instant today.
- A bird is named by its common name, as in the bracket and the packs. A county is its region code
  (`US-CO-059`), a state its region code (`US-CO`).
- Enums travel as their Kotlin names: `Round`, `Season` and `Decision.Reason` from `:domain`,
  `LeagueState` from `:api`.
- **Who is asking.** Every call except `POST /v1/accounts` carries the account id and secret as HTTP Basic
  credentials (`Authorization: Basic`, the id as the user and the secret as the password). They are never
  in a path, a query or a body.
- **A call with nothing to return** answers `204 No Content`.

## Errors

A refused call answers with a 4xx or 5xx status and one body, `ApiError(code, detail)`. `code` is the name
of an `ApiErrorCode`. The app turns the code into words from `strings.xml`; `detail` is for logs and is
never shown. An app older than the server may meet a code it does not know (`ApiError.errorCode` is then
null) and must treat it as a general failure.

| Code | Status | Meaning |
| --- | --- | --- |
| `INVALID_REQUEST` | 400 | The body is not well-formed, or a field is outside what its type allows |
| `UNAUTHORIZED` | 401 | The account id and secret are missing or wrong |
| `LEAGUE_NOT_FOUND` | 404 | No such league, or the player is not in it; the two are not told apart |
| `INVITE_CODE_UNKNOWN` | 404 | No league has this invite code |
| `LEAGUE_FULL` | 409 | The league is at its capacity |
| `ALREADY_A_MEMBER` | 409 | The player is already in this league |
| `SEASON_START_NOT_A_MONDAY` | 422 | A season starts on a Monday |
| `TIME_ZONE_UNKNOWN` | 422 | The time zone is not an IANA zone the server knows |
| `DISPLAY_NAME_INVALID` | 422 | The display name is empty, too long or not allowed |
| `COUNTY_UNKNOWN` | 422 | The county code is not a county of any pack |
| `BIRD_UNKNOWN` | 422 | A sighting's bird is not one the server knows, or a pick is not one of its matchup's two birds |
| `PICKS_LOCKED` | 409 | The season has started; picks can no longer change |
| `PICKS_INCOMPLETE` | 422 | Fewer than 63 picks, or a later-round pick of a bird not picked to reach that matchup |
| `BRACKET_NOT_READY` | 409 | The league has no bracket yet |
| `ROUND_NOT_DECIDED` | 409 | The round is not decided yet |
| `RATE_LIMITED` | 429 | Too many requests |
| `SERVER_ERROR` | 500 | The server failed; nothing the app sent was wrong |

Every endpoint can return `RATE_LIMITED` and `SERVER_ERROR`. Every endpoint except `POST /v1/accounts` can
return `UNAUTHORIZED`. Every endpoint with a request body can return `INVALID_REQUEST`. The tables below
list only the codes an endpoint adds to these.

## Account

| Endpoint | Request | Response | Errors it adds |
| --- | --- | --- | --- |
| `POST /v1/accounts` | `CreateAccountRequest` | `CreateAccountResponse` | none |
| `PATCH /v1/account` | `UpdateProfileRequest` | 204 | `DISPLAY_NAME_INVALID`, `COUNTY_UNKNOWN` |
| `DELETE /v1/account` | none | 204 | none |

- `CreateAccountRequest` has no fields. ADR 0001: the request that creates an account carries nothing
  about the player. The display name and the home county are sent with `PATCH /v1/account` when the player
  sets or changes them; a field left out is not changed.
- `CreateAccountResponse` is the only message that carries the secret.
- `DELETE /v1/account` removes the player's name, picks and sightings, and cannot be undone.

## Leagues

| Endpoint | Request | Response | Errors it adds |
| --- | --- | --- | --- |
| `POST /v1/leagues` | `CreateLeagueRequest` | `LeagueSummary` | `SEASON_START_NOT_A_MONDAY`, `TIME_ZONE_UNKNOWN` |
| `GET /v1/leagues` | none | `LeagueListResponse` | none |
| `POST /v1/leagues/join` | `JoinLeagueRequest` | `LeagueSummary` | `INVITE_CODE_UNKNOWN`, `LEAGUE_FULL`, `ALREADY_A_MEMBER` |
| `GET /v1/leagues/{leagueId}` | none | `LeagueDetail` | `LEAGUE_NOT_FOUND` |
| `DELETE /v1/leagues/{leagueId}/membership` | none | 204 | `LEAGUE_NOT_FOUND` |

- `GET /v1/leagues` lists the leagues the player is in, and no others.
- `DELETE …/membership` is the player leaving the league.
- `LeagueDetail.members` and `StandingsResponse` are lists of `Standing`: display name, points, rank, and
  whether the row is the asking player. A `Standing` has no account id, no county and no sightings.

## Bracket and picks

| Endpoint | Request | Response | Errors it adds |
| --- | --- | --- | --- |
| `GET /v1/leagues/{leagueId}/bracket` | none | `BracketResponse` | `LEAGUE_NOT_FOUND`, `BRACKET_NOT_READY` |
| `GET /v1/leagues/{leagueId}/picks` | none | `PicksResponse` | `LEAGUE_NOT_FOUND` |
| `PUT /v1/leagues/{leagueId}/picks` | `SubmitPicksRequest` | `PicksResponse` | `LEAGUE_NOT_FOUND`, `BRACKET_NOT_READY`, `PICKS_LOCKED`, `PICKS_INCOMPLETE`, `BIRD_UNKNOWN` |

- `PUT …/picks` replaces the player's whole bracket: 63 picks, one per slot. `GET …/picks` returns the
  asking player's own picks.
- **Matchup slots.** A matchup is a `MatchupSlot`: one number, 0 to 62, counted by round, then by position
  from the top of the bracket. In JSON it is a bare number; as a key of `picks`, that number in quotes.

  | Round | Matchups | Slots |
  | --- | --- | --- |
  | `ROUND_OF_64` | 32 | 0–31 |
  | `ROUND_OF_32` | 16 | 32–47 |
  | `ROUND_OF_16` | 8 | 48–55 |
  | `QUARTERFINAL` | 4 | 56–59 |
  | `SEMIFINAL` | 2 | 60–61 |
  | `FINAL` | 1 | 62 |

  The winners of positions `2p` and `2p + 1` of a round meet at position `p` of the next, the winner of
  the even one drawn above.

## Results and standings

| Endpoint | Request | Response | Errors it adds |
| --- | --- | --- | --- |
| `GET /v1/leagues/{leagueId}/standings` | none | `StandingsResponse` | `LEAGUE_NOT_FOUND` |
| `GET /v1/leagues/{leagueId}/rounds/{round}/results` | none | `RoundResultsResponse` | `LEAGUE_NOT_FOUND`, `ROUND_NOT_DECIDED`, `INVALID_REQUEST` |

- `{round}` is a `Round` name, such as `ROUND_OF_64`; any other text is `INVALID_REQUEST`.
- A `MatchupResult` gives each bird's eligible and logged counts, the winner, the reason
  (`Decision.Reason`) and the asking player's points for the matchup, as the round's points and the upset
  bonus.

## Sightings

| Endpoint | Request | Response | Errors it adds |
| --- | --- | --- | --- |
| `POST /v1/sightings` | `UploadSightingsRequest` | 204 | `BIRD_UNKNOWN`, `COUNTY_UNKNOWN` |

- A sighting is `SightingUpload(clientId, commonName, countyCode, date)` and nothing else. Coordinates,
  the time of day and the place name the player typed never leave the phone (ADR 0001); a test in `:api`
  fails if the type gains a field.
- `clientId` is made by the app, one per sighting. The server keeps one sighting per id, so sending a
  batch again after a lost reply counts nothing twice.
- A sighting belongs to the player, not to a league: it counts in every league the player is in.

## Packs

| Endpoint | Request | Response | Errors it adds |
| --- | --- | --- | --- |
| `GET /v1/packs` | none | `PackListResponse` | none |

## Not decided

ADR 0001 leaves these open, so no message or endpoint settles them:

- **The league's own fields and who can see them.** `CreateLeagueRequest` (name, open, time zone, season
  start) and `LeagueSummary` hold the fields issue #29 lists, but the ADR's table of what the app sends
  does not name them yet. The ADR must be changed before the first call that sends them. There is no
  endpoint that lists leagues the player is not in, and what `open` lets a stranger see or do is not
  defined.
- **An outing with no birds.** Eligibility counts the county of any outing, but only sightings are sent;
  there is no message for an outing without one.
- **What deleting an account covers** beyond name, picks and sightings, and what it does to matchups
  already decided.
- **How long data is kept** after a season.

Also open, for the server issues:

- Downloading a pack file. `PackListResponse` describes the packs; no endpoint for the file itself is
  defined here.
- Whether the pack list needs an account. Outings work with no account, and the app makes no network call
  before the player agrees.
- Seeing other players' picks once they lock, and how many birds each player logged in a round. ADR 0001
  allows both; issue #29 lists no type for either.
