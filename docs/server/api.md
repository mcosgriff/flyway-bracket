# The server's API

What the app and the server say to each other. The types are in the `:api` module
(`api/src/main/kotlin/io/cosgriff/flywaybracket/api/`), which both sides compile against; this page lists
the endpoints that carry them. Change the two together.

The server (`:server`, [running.md](running.md)) answers the health check, the account endpoints, the
league endpoints, the pack endpoints and the sighting endpoints, and the app makes no network call yet. The other endpoints are built in the server issues. What the app may send is decided by [ADR 0001](../adr/0001-server-and-network.md); a
new field sent to the server needs a change to that record first.

## Rules for every call

- One host, HTTPS only. Every path starts with `/v1/` (`ApiPaths`).
- Bodies are JSON, read and written with `ApiJson`. A reader skips a key it does not know, so a field can
  be added without a new version. A field that is null is left out. One exception: the server refuses a
  sighting upload with any key its type does not have ([Sightings](#sightings)).
- A date is an ISO local date (`2027-03-01`). An instant is ISO-8601 in UTC (`2027-03-01T07:00:00Z`). No
  message carries an instant today.
- A bird is named by its common name, as in the bracket and the packs. A county is its region code
  (`US-CO-059`), a state its region code (`US-CO`).
- Enums travel as their Kotlin names: `Round`, `Season` and `Decision.Reason` from `:domain`,
  `LeagueState` from `:api`.
- **Who is asking.** Every call except `POST /v1/accounts` and `GET /v1/health` carries the account id and secret as HTTP Basic
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
| `ALREADY_IN_A_LEAGUE` | 409 | The player is already in a league that is forming or in season |
| `JOINING_CLOSED` | 409 | The league's season has started, or the league is finished or closed |
| `LEAGUE_NAME_INVALID` | 422 | A league's name is not 3 to 40 characters after trimming |
| `SEASON_START_NOT_A_MONDAY` | 422 | A season starts on a Monday |
| `SEASON_START_TOO_SOON` | 422 | A new league's season starts at least 7 days after the day it is created |
| `TIME_ZONE_UNKNOWN` | 422 | The time zone is not an IANA zone the server knows |
| `DISPLAY_NAME_INVALID` | 422 | The display name is empty, too long or not allowed |
| `COUNTY_UNKNOWN` | 422 | A home county that is not a well-formed county code, or a sighting's county that is not a county of any pack |
| `BIRD_UNKNOWN` | 422 | A sighting's bird is named in no county's chart of any pack, or a pick is not one of its matchup's two birds |
| `SIGHTING_DATE_IN_FUTURE` | 422 | A sighting's date is more than one day after today at UTC+14 |
| `SIGHTING_NOT_FOUND` | 404 | The player has no sighting with this id; another player's is not told apart from none |
| `PICKS_LOCKED` | 409 | The season has started; picks can no longer change |
| `PICKS_INCOMPLETE` | 422 | Fewer than 63 picks, or a later-round pick of a bird not picked to reach that matchup |
| `BRACKET_NOT_READY` | 409 | The league has no bracket yet |
| `ROUND_NOT_DECIDED` | 409 | The round is not decided yet |
| `PACK_NOT_FOUND` | 404 | The server holds no pack for this state |
| `RATE_LIMITED` | 429 | Too many requests |
| `SERVER_ERROR` | 500 | The server failed; nothing the app sent was wrong |

A path the server does not have answers 404, a method it does not have on that path 405, and a body that
is not JSON 415. All three carry `INVALID_REQUEST`: the status tells them apart. A 404 an endpoint gives
itself (`LEAGUE_NOT_FOUND`, `INVITE_CODE_UNKNOWN`, `PACK_NOT_FOUND`, `SIGHTING_NOT_FOUND`) carries its own code. `detail` is empty for
these and for bad JSON, so that an error never repeats what was sent.

Every endpoint can return `RATE_LIMITED` and `SERVER_ERROR`. Every endpoint except `POST /v1/accounts` and `GET /v1/health` can
return `UNAUTHORIZED`. Every endpoint with a request body can return `INVALID_REQUEST`. The tables below
list only the codes an endpoint adds to these.

## Health

| Endpoint | Request | Response | Errors it adds |
| --- | --- | --- | --- |
| `GET /v1/health` | none | `HealthResponse` | none |

- Needs no account and says nothing about any player: the server's version, and whether its database
  answered just now.
- It answers 200 even when the database does not answer; `database` is then `false`.

## Account

| Endpoint | Request | Response | Errors it adds |
| --- | --- | --- | --- |
| `POST /v1/accounts` | `CreateAccountRequest` | 201, `CreateAccountResponse` | none |
| `GET /v1/account` | none | `ProfileResponse` | none |
| `PATCH /v1/account` | `UpdateProfileRequest` | 204 | `DISPLAY_NAME_INVALID`, `COUNTY_UNKNOWN` |
| `DELETE /v1/account` | none | 204 | none |

- `CreateAccountRequest` has no fields. ADR 0001: the request that creates an account carries nothing
  about the player. A field sent with it anyway is not kept. The display name and the home county are sent
  with `PATCH /v1/account` when the player sets or changes them; a field left out is not changed.
- `CreateAccountResponse` is the only message that carries the secret. The id is a UUID; the secret is 32
  random bytes in URL-safe Base64 with no padding (43 characters).
- **The secret is never stored.** The server keeps an Argon2id hash of it (19 MiB, two passes, one lane:
  OWASP's lowest recommended setting), as a PHC string in `players.secret_hash`. Argon2id rather than
  bcrypt: it is memory-hard, so a stolen table is costly to search on a GPU, and it reads the whole
  secret where bcrypt stops at 72 bytes.
- **A refusal takes the same time** whether the secret was nearly right or not, and whether the id names a
  player or not: hashes are compared in constant time, and an unknown id is checked against a hash all the
  same. A missing, malformed or non-Basic `Authorization` header is `UNAUTHORIZED` too, never
  `INVALID_REQUEST`; the 401 carries `WWW-Authenticate: Basic`.
- **Account creation is limited** to 10 per address per hour, in fixed windows counted from the hour.
  The 11th answers 429 `RATE_LIMITED`. The addresses are counted in memory and forgotten when the window
  ends; they are not stored with the account or logged. Behind a proxy the server sees the proxy's address:
  where it is hosted is not decided (#16), and the proxy's forwarded address must be read then.
- `GET /v1/account` is the asking player's own profile: their display name and home county, each absent
  until set, and `league`, the `LeagueSummary` of their league: the one forming or in season, else their
  latest finished or closed one; absent when they are in none. A league whose state is `CLOSED` never
  started for want of players; this is how its players are told.
- `PATCH /v1/account` checks both fields before it changes either. The display name follows the app's rule
  (`DisplayName` in `:domain`): 2 to 24 characters after trimming, stored trimmed. The home county must be
  a well-formed county code (`CountyCode` in `:domain`, such as `US-CO-059`), otherwise `COUNTY_UNKNOWN`; it
  need not be a county of a pack the server holds.
- `DELETE /v1/account` cannot be undone. The player first leaves every league they are in, as
  `DELETE …/membership` would; then their row (id, secret hash, display name, home county) is deleted, and
  their league places and their sightings with it (the tables' foreign keys are `ON DELETE CASCADE`).
  Once the server has picks, it also deletes those; their results in a finished season stay, under no
  name, as "a former player". After it, the id and secret
  are `UNAUTHORIZED`.
- **A home county change and rounds.** The server stores only the current home county. A change is meant to
  count for rounds that have not started; that is for the issue that decides rounds on the server.

## Leagues

| Endpoint | Request | Response | Errors it adds |
| --- | --- | --- | --- |
| `POST /v1/leagues` | `CreateLeagueRequest` | 201, `LeagueSummary` | `LEAGUE_NAME_INVALID`, `TIME_ZONE_UNKNOWN`, `SEASON_START_NOT_A_MONDAY`, `SEASON_START_TOO_SOON`, `DISPLAY_NAME_INVALID`, `ALREADY_IN_A_LEAGUE` |
| `GET /v1/leagues` | none | `LeagueListResponse` | none |
| `POST /v1/leagues/join` | `JoinLeagueRequest` | `LeagueSummary` | `DISPLAY_NAME_INVALID`, `INVITE_CODE_UNKNOWN`, `ALREADY_A_MEMBER`, `ALREADY_IN_A_LEAGUE`, `JOINING_CLOSED`, `LEAGUE_FULL` |
| `POST /v1/leagues/auto-join` | none | `LeagueSummary` | `DISPLAY_NAME_INVALID`, `ALREADY_IN_A_LEAGUE` |
| `GET /v1/leagues/{leagueId}` | none | `LeagueDetail` | `LEAGUE_NOT_FOUND` |
| `DELETE /v1/leagues/{leagueId}/membership` | none | 204 | `LEAGUE_NOT_FOUND` |

The rules are `domain/league` in `:domain` (#32); the server applies them.

- **One league at a time.** A player is in at most one league that is forming or in season
  (`ALREADY_IN_A_LEAGUE`). A finished or closed league does not count: its players can create or join the
  next. A player needs a display name to create or join one, since the others see it
  (`DISPLAY_NAME_INVALID` when it is not set).
- **Creating.** A name of 3 to 40 characters, stored trimmed; open or invite-only; an IANA time zone such
  as `America/Denver` (an offset such as `+02:00` is `TIME_ZONE_UNKNOWN`); and a season start on a
  Monday at least 7 days after today, today being the date in that time zone. The player who creates it
  is its first member.
- **Invite codes.** Every league has one: 8 characters from `ABCDEFGHJKMNPQRSTUVWXYZ23456789`, which has
  no 0, O, 1, I or L. `POST /v1/leagues/join` reads the code without case and without spaces at either
  end. Every member is given it in `LeagueSummary.inviteCode`.
- **Size.** A league holds at most 64 players (`LeagueSummary.capacity`); the 65th is `LEAGUE_FULL`. The
  check and the join happen under a lock on the league's row, so two players asking for the last seat at
  once get one seat between them.
- **Joining closes when the season starts**: midnight at the start of its first Monday, in the league's
  time zone. After that, and for a finished or closed league, a join is `JOINING_CLOSED`.
- **Auto-join** puts the player in the open league with the earliest start that has room and has not
  started (the earliest made, when two start on the same day). When there is none it creates an open
  league starting on the second Monday after today, named `Flyway` and four random characters. The
  request has no body and that league's time zone is UTC: ADR 0001's table of what the app sends names
  no time zone for a player. Auto-joins wait for one another, so two players who find no room open one
  league between them.
- **The start.** A bracket needs at least 8 players when the season starts. With fewer when the start date
  arrives, the start moves back one week and the league goes on taking players; this happens up to four
  times. If it is still short on the fifth date, the league is `CLOSED`, and its players see that in
  `GET /v1/account`. A postponed league's `seasonStart` and `seasonEnd` are its new dates.
- **States.** `LeagueState` is the server's league phase (`LeaguePhase` in `:domain`):
  `BEFORE_SEASON` is forming, `IN_SEASON` running, `FINISHED` after the final's Sunday has ended, and
  `CLOSED` never started.
- **Nothing runs when a date passes.** The server settles a league, bringing it up to today, whenever it is
  read, and saves that under the league's lock before any change to its players. The count of players at
  each date is therefore the count when that date passed.
- `GET /v1/leagues` lists the leagues the player is in and has not left, latest start first: at most one
  forming or in season, and any finished or closed.
- `GET /v1/leagues/{leagueId}` is for members only; any other player gets `LEAGUE_NOT_FOUND`.
  `LeagueDetail.members` and `StandingsResponse` are lists of `Standing`: display name, points, rank, and
  whether the row is the asking player, best rank first, then by name. A `Standing` has no account id, no
  county and no sightings. Players level on points share a rank (10, 7, 7, 3 points rank 1, 2, 2, 4).
  Points are 0 until the server scores picks.
- `DELETE …/membership` is the player leaving the league. Before the season starts, or from a closed
  league, they are taken out as if they had never joined, and can join again. After it starts they are
  kept, marked as left: their picks stop scoring, they no longer count toward any matchup, and they are
  not in the members or the count. Either way they are free to join another league.

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
| `POST /v1/sightings` | `UploadSightingsRequest` | 204 | `COUNTY_UNKNOWN`, `BIRD_UNKNOWN`, `SIGHTING_DATE_IN_FUTURE` |
| `DELETE /v1/sightings/{clientId}` | none | 204 | `SIGHTING_NOT_FOUND` |

The rules are `Sightings` in `:server` (#36).

- **Nothing else is accepted or stored.** A sighting is `SightingUpload(clientId, commonName, countyCode,
  date)` and nothing else. Coordinates, the time of day, the place name the player typed and the count
  never leave the phone (ADR 0001); a test in `:api` fails if the type gains a field. Unlike every other
  request, an upload with any other key, in a sighting or beside the list, is `INVALID_REQUEST` (400) and
  stores nothing: an extra field is refused, not dropped. The server keeps no time of its own either: the
  `sightings` table has no column for when a row was written.
- **The id.** `clientId` is made by the app, one per sighting: a UUID in its usual text form
  (`6f1c0d4e-…`), in either case; anything else is `INVALID_REQUEST`. The server keeps one sighting per id
  for each player. A sighting whose id the player already has changes nothing, whatever its other fields,
  so sending a batch again after a lost reply counts nothing twice. Of two sightings with one id in one
  batch, the first is kept. To change a sighting, the app deletes it and sends it again. Two players'
  ids never meet.
- **At most 500 a request** (`UploadSightingsRequest.MAX_SIGHTINGS`); 501 or more is `INVALID_REQUEST`.
  An empty list is 204.
- **All or none.** Every sighting is checked before any is stored. A refusal stores nothing and gives the
  first sighting that fails, in the order sent; within one sighting the checks are id, county, bird, date.
- **County and bird.** The county must be a county of a pack the server holds (`COUNTY_UNKNOWN`). The bird
  must be named in the chart of some county of some pack it holds (`BIRD_UNKNOWN`), spelled exactly; it
  need not be in the sighting's own county's chart, so a bird seen out of its range still counts, and an
  app holding an older pack than the server's is not refused for a bird the new pack moved.
- **The date** is the day where the player was. A date more than one day after today is
  `SIGHTING_DATE_IN_FUTURE`, today being the date at UTC+14, the earliest time zone on Earth. A sighting
  belongs to the player, not to a league, so no one league's time zone applies, and a player anywhere
  has a date no later than today there; the extra day allows for a phone's clock a little ahead. There is
  no earliest date.
- **A date in a round already decided** is stored like any other but does not change that round's result.
  Deciding rounds is for #37, which must read the sightings once, when the round ends, and keep the result.
- `DELETE /v1/sightings/{clientId}` removes one of the player's own sightings: the player removed it on the
  phone. An id the player has no sighting with is `SIGHTING_NOT_FOUND` (404): one never sent, one already
  deleted, another player's, or text that is not a UUID; the four are not told apart. The app can take a
  404 to mean the sighting is gone.
- **Who sees them.** A sighting belongs to the player, not to a league: it counts in every league the
  player is in. No endpoint gives a player's sightings to anyone, the player included; a league will see
  only totals (#37).

## Packs

| Endpoint | Request | Response | Errors it adds |
| --- | --- | --- | --- |
| `GET /v1/packs` | none | `PackListResponse` | none |
| `GET /v1/packs/{state}` | `If-None-Match` (optional) | the `.fbpack` file, or 304 | `PACK_NOT_FOUND` |

The server's packs are the files in its packs directory ([running.md](running.md#state-packs), #33).

- Both need an account, so the files are not open to the whole internet.
- `PackSummary` gives the state's code and name, the file's size in bytes, the day the pack was built and
  its `version`: the SHA-256 of the file, in lowercase hex. The list is in order of state name.
- `{state}` is a state's code, such as `US-CO` (`ApiPaths.pack`). A code that is not one, or a state the
  server holds no pack for, is `PACK_NOT_FOUND`.
- The file is sent as it is on the server, `application/octet-stream`, with `ETag: "<version>"` and
  `Cache-Control: private, no-cache`. The app reads it with `StatePackCodec`, as a file added by hand.
- **Asking again.** The app sends `If-None-Match: "<version>"` with the version of the pack it holds. When
  that is still the server's version, the answer is `304 Not Modified` with the `ETag` and no body.

## Not decided

ADR 0001 leaves these open, so no message or endpoint settles them:

- **The league's own fields and who can see them.** `CreateLeagueRequest` (name, open, time zone, season
  start) and `LeagueSummary` hold the fields issue #29 lists, and the server takes them, but the ADR's
  table of what the app sends does not name them yet. The ADR must be changed before the app first sends
  them. There is no endpoint that lists leagues the player is not in; `open` only lets auto-join choose
  the league.
- **A player's time zone.** Issue #32 has auto-join open a league in the player's time zone; the ADR does
  not name one among what the app sends, so auto-join sends none and opens its league in UTC.
- **An outing with no birds.** Eligibility counts the county of any outing, but only sightings are sent;
  there is no message for an outing without one.
- **What deleting an account covers** beyond name, picks and sightings, and what it does to matchups
  already decided.
- **How long data is kept** after a season.

Also open, for the server issues:

- Whether the app may download packs before the player has an account. Both pack endpoints need one
  (#33); outings work with no account, and the app makes no network call before the player agrees.
- Seeing other players' picks once they lock, and how many birds each player logged in a round. ADR 0001
  allows both; issue #29 lists no type for either.
