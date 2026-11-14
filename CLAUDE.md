# Flyway Bracket — notes for Claude

Android app for a bird-watching bracket game. Each migration season, 64 migrating birds are seeded into a
bracket; players in a league pick winners and log the birds they see; a matchup goes to the bird that more
of the league's eligible players found. `README.md` has the rules in short. Public repo, no licence chosen
yet.

Set up from the Android App Playbook (https://claude.ai/artifact/QZmyJUpsjv8rC1z1pzhM7s): toolchain, CI,
releases and issues follow it. When this repo changes a shared practice, update the playbook's section and
add a row to its Revision log.

- Design canvas (mockups): https://claude.ai/artifact/6YaSetusq6mnkkiE5fds6R

There is no local copy of the design in the repo yet. Before building a screen, read its board on the
canvas.

This file holds the rules and the decisions behind them. How a feature works is in its code and tests.

## Status

`:domain` has the game rules. `:data` has the database (`outings` and `sightings`, behind `OutingRepository`),
the player's settings, the bundled table of states and counties (`assets/places/counties.tsv`,
Colorado only) and the state packs on the phone (`PackRepository`). `:app` has the theme, the bottom bar,
the first-run screen (display name and home county, saved in the settings), the Home screen and the Packs
screen, which downloads packs from the server and updates them (`PackDownloads`, #39; the `.fbpack` file
picker is in debug builds only), and the outing
flow: Outing, the Add sighting sheet and Summary, in the county found from the phone's approximate location
(`CountyLocator`, #27), else the home county. Bracket (#42) and League (#40: Join a league, Create a league,
and the league forming, running, finished or closed) stand behind the consent page. Home reads its region
from the settings, its season figures and recent outings from the database, and its league row and hero
from the league kept on the phone (`LeagueRepository`, Room schema 5); it starts or continues an outing.
The app's network calls are the account endpoints (`data/server/ServerClient`, `AccountRepository`, #38),
creating, joining, auto-joining, leaving and reading a league (#40), the bracket's (#41), the pack
endpoints (`PackDownloads`, #39) and the sighting endpoints (`SightingSync`, #43), all made after the
player agrees on the consent page.
`:server` starts, migrates its Postgres, answers a health check and has anonymous device accounts (create,
read, change and delete the profile) and leagues (create, join, auto-join, leave, read); it has no pick or
sighting endpoint yet and is hosted nowhere.
`./gradlew build` is green (Gradle 9.9.1, AGP 9.5.1, lint included; it needs Docker for `:server:test`).
Keep it green; lint fails the build on
a warning as on an error (`warningsAsErrors`, in `:app` and `:data`), and there is no baseline: fix a
warning, or suppress that one case in `app/lint.xml` with a comment saying why.

Not set up yet, from the playbook's new-app checklist: dependency verification
(`gradle/verification-metadata.xml`), the release keystore and its secrets in the `release` environment,
the `CODECOV_TOKEN` secret.

## Commands

- `./gradlew :domain:test` — fast pure-Kotlin tests. Run often.
- `./gradlew build` — everything: unit tests, screenshot and accessibility tests, and lint.
- `./gradlew :app:recordRoborazziDebug` — re-record the reference screenshots (`app/src/test/screenshots/`)
  after a change meant to alter a screen; look at every image it changes.
- `./gradlew :data:testDebugUnitTest` — the database, migration and settings tests, on the JVM (Robolectric).
- `./gradlew :tools:packbuilder:run --args="US-CO"` — build a state pack from the files in `packs-local/`
  (`docs/packs.md`); `:tools:packbuilder:test` runs its tests on made-up inputs.
- `./gradlew :server:test` — the server's tests. Those that need a database start a real Postgres with
  Testcontainers, so Docker must be running; without it they fail.
- `./gradlew :server:run` — start the server against a local Postgres. It reads `DATABASE_URL`,
  `DATABASE_USER`, `DATABASE_PASSWORD` and `PORT` from the environment (`docs/server/running.md`).
- `./gradlew :server:installDist && docker build -t flyway-bracket-server server` — the server's image.
- `./gradlew koverHtmlReportCoverage` — coverage of `:domain`, `:api`, `:data` and `:app`; CI sends the XML to
  Codecov.
- `./gradlew :app:installDebug` — install on a connected phone (debug app id
  `io.cosgriff.flywaybracket.debug`).
- Release: merge the PR `release-please` keeps open (version and changelog come from Conventional Commits);
  it tags `vX.Y.Z` and starts `release.yml`, which builds the signed APK after approval in the `release`
  environment. No release can be built until that environment has its signing secrets.

## Modules

- `:domain` — pure Kotlin, **no Android imports**. All game rules live here and get unit tests. Results
  are typed values, never English text.
  - `domain/pack` — `TimeBin` (48 a year, four per month), `CountyChart`, `BarChartParser`, `StatePack`
    (a state's counties, charts and outlines) and `StatePackCodec` (the `.fbpack` file: gzipped JSON).
  - `src/testFixtures` — made-up charts and a made-up pack (`testStatePack()`) for any module's tests:
    `testImplementation(testFixtures(project(":domain")))`.
  - `domain/bracket` — `Round`, `Team`, `Matchup`, `Seeding`, `MatchupRule`, `PickScore`.
  - `domain/player` — `PlayerSettings` (display name, home county code), `DisplayName` (the name's rule).
  - `domain/league` — `LeagueName`, `SeasonStart`, `InviteCode` (its shape), `LeagueTimeZone` (the zones
    a league may have), `LeagueLifecycle` (capacity, the postponed start, `LeaguePhase`) and `Standings`
    (ranks). The server and the app check with the same functions.
  - `domain/place` — `CountyTable` (states and counties to choose a home county from), `CountyTableParser`,
    `CountyCode` (the shape of a county code, which the server checks a home county against).
  - `domain/outing` — `Outing`, `Sighting`, `OutingLog`, and Home's season figures (`speciesSince`,
    `outingsSince`, `newLifersSince`).
- `:api` — pure Kotlin, no Android: the messages the app and the server exchange (`kotlinx.serialization`
  JSON, read and written with `ApiJson`), `ApiPaths` (every path, under `/v1/`), `ApiErrorCode` and
  `MatchupSlot` (a bracket's 63 matchups, numbered by round, then from the top). Depends on `:domain`.
  The app sends and reads it through `data/server/ServerClient` in `:data`; `:server` answers with it. `docs/server/api.md` lists the endpoints; change the two together. A field the app
  sends must be in ADR 0001's table first. Test: `./gradlew :api:test`.
- `:data` — what the app saves on the phone, mapped to and from `:domain` types. Depends on `:domain`.
  - `data/db` — `FlywayDatabase` (Room 3), its entities and DAOs. Each version's schema is exported to
    `data/schemas` and committed (CI fails if it isn't). A schema change raises the version and adds a
    migration; `MigrationTest` opens every exported version as the current database.
  - `SettingsRepository` — `PlayerSettings` in DataStore, as a `Flow`.
  - `PackRepository` — the state packs, each its `.fbpack` file in `files/packs`: install from bytes
    (checked with `StatePackCodec.decode` first), list, delete, `chartFor(countyCode)` and
    `countyAt(longitude, latitude)`. The files are parsed once and kept in memory.
  - `PackDownloads` — the server's packs (#39): its list, and a download that replaces the pack on the
    phone only once the whole file has come, matched its `ETag` (SHA-256) and been checked; runs in its own
    scope so a download outlives the screen. `NetworkMeter` says whether the connection is metered.
  - `data/di/DataModule` — Hilt: provides the database, its DAOs, the DataStore and the repositories.
  - `data/server/ServerClient` — the league server's account endpoints, with `:api`'s messages and
    `ApiJson` (no content negotiation), on Ktor's OkHttp engine. Results are `ServerResult`; a failure is a
    `ServerProblem`: `NoNetwork`, `NoAnswer` or `Refused(ApiErrorCode?)`. The address is the build
    setting `BuildConfig.SERVER_URL` (`-PflywayServerUrl`, `-PflywayDebugServerUrl`; a debug build
    defaults to `http://10.0.2.2:8080`). Tests use Ktor's mock engine; none reaches a server.
  - `AccountRepository` — create, delete, `hasAccount()`, and the profile sent when the display name or
    home county changes. `data/account/EncryptedAccountStore` keeps the id and secret in `no_backup`,
    AES-GCM under an Android Keystore key. Provided by `data/di/ServerModule`.
  - `LeagueRepository` — the player's league and its table, kept in Room (`league`, `league_members`) with
    when it was fetched; create, join, find (auto-join) and leave. Sends nothing with no account, and
    nothing the `domain/league` rules refuse.
  - Tests run on the JVM under Robolectric; Gradle resolves its Android jar (root `build.gradle.kts`).
- `:app` — Compose UI, theme, navigation (Navigation 3, `@Serializable` `NavKey` routes in
  `ui/FlywayNavHost.kt`). Hilt: `FlywayApp` is the application, `MainActivity` an entry point.
- `:tools:packbuilder` — a JVM command-line tool, not part of the app: builds a state pack from eBird bar
  chart downloads and Census county outlines in `packs-local/` (`docs/packs.md`). Depends on `:domain`.
  Left out of the coverage report and of the dependency graph sent to GitHub.
- `:server` — the league server, not part of the app: Ktor 3 on Netty, Postgres through JDBC and
  HikariCP. Depends on `:domain` and `:api`; **`:app` must never depend on it.** `docs/server/running.md`.
  - `Application.module` is the whole server: JSON with `ApiJson`, paths from `ApiPaths`, every failure
    an `ApiError` from `:api` (never a copy of those types, never wording).
  - `ServerConfig` — configuration comes only from environment variables. No secret in the repository;
    `server/.env.example` holds made-up values.
  - `Database` — the connection pool; at start it applies the SQL in
    `server/src/main/resources/db/migration` with the Flyway migration library (the shared word is a
    coincidence). A merged migration is never edited; a schema change is a new `V<n>__<what>.sql` with a
    test in `DatabaseTest`.
  - `server/account` — anonymous device accounts. `Accounts` holds the rules (display name and county
    code from `:domain`), `PlayerStore` the `players` table (`PostgresPlayerStore`; tests also use an
    in-memory one), `AccountRoutes` the endpoints and the HTTP Basic sign-in. The secret is kept only as
    an Argon2id hash (`Argon2idHasher`, Bouncy Castle), compared in constant time; an unknown id is
    checked against a hash too. Account creation is limited per address (`FixedWindowRateLimiter`).
    `AccountEndpointsTest` holds the HTTP cases once and runs them on both stores.
  - `server/league` — leagues. `Leagues` applies `domain/league`'s rules; `LeagueStore` the `leagues` and
    `league_members` tables inside one transaction (`PostgresLeagueStore`; tests also use an in-memory
    one). Nothing runs when a league's date passes: a league is settled for today when read, and saved
    under its row lock before any change to its players. Locks: at most one player per transaction,
    before any league; a count read after its lock, in a statement of its own. `LeaguesTest` holds the
    rules' cases and `LeagueEndpointsTest` the HTTP ones, each run on both stores.
  - The log has a request's status, method and path, never its body, query or headers: the
    `Authorization` header carries the player's secret. `InMemoryAccountEndpointsTest` reads the log and
    fails if a secret or that header shows up.
  - Logging is `slf4j-simple`, not Logback: a server dependency's licence must be on the allow-list in
    `.github/dependency-review-config.yml`, like the app's.
  - `server/Dockerfile` — a small image that runs as a user that is not root. CI builds it and pushes
    nothing. Left out of the coverage report; its runtime classpath is in the dependency graph sent to
    GitHub.
- `site/` — not a Gradle module: the user guide for players. Markdown in `site/pages/`, built with pandoc
  (`site/build.sh`), checked by `site/check.py` and pa11y, published to GitHub Pages by `pages.yml`. No
  JavaScript, nothing from another host. A page about something not built yet has `beta: true`; remove it
  when the feature ships. A rule changed in `:domain` is changed on the site in the same PR.

Add `:baselineprofile` once the screens settle.

## Decisions — don't change without asking

- **applicationId `io.cosgriff.flywaybracket` must never change. The release signing key must never
  change** once the first release is out.
- **No trackers, analytics, crash reporting or ads.** CI and `release.yml` scan the release APK with Exodus Privacy
  (`.github/scripts/exodus-scan.sh`): a tracker fails, and so does a permission not in
  `.github/privacy/permissions.txt` (or one listed that the APK no longer asks for). A new permission means
  updating that list and explaining it in the app.
- **Network: the league server only, as `docs/adr/0001-server-and-network.md` decides: the account,
  leagues, the bracket, bird packs and sightings.** One server, the
  project's own, over HTTPS (Ktor on OkHttp in `:data`, so the platform's TLS and certificate checks
  apply; the network security config allows no cleartext outside debug builds, and the build refuses a
  release `flywayServerUrl` that is not `https://`); anonymous device accounts, their id and secret
  encrypted under an Android Keystore key; nothing sent before the player says yes on the consent page,
  whose words follow the ADR. The ADR lists every field sent: change it, and the consent page, before
  sending anything else. Errors reach the screen as words from `strings.xml` by `ApiError` code, never the
  server's text.
- **eBird data never goes in the repo** (eBird's terms of use). Tests and previews use made-up figures.
  Local downloads go in `packs-local/`, which is ignored. Shipping data derived from eBird needs the
  Cornell Lab's permission, not yet obtained.
- **Game rules** (each is a constant or function in `:domain`, with tests):
  - A matchup is won by the higher share of *eligible* players who logged the bird; a tie goes to the
    better seed. A side with fewer than `MatchupRule.MIN_ELIGIBLE` eligible players cannot win on share.
  - A player is eligible for a bird when it is expected that week in their home county or in a county
    where they logged an outing.
  - A sighting of a bird not expected for that player that week does not count, and does not make them
    eligible for it: it is on neither side of the share (`MatchupRule.tally`, #17).
  - Expected is yes or no: on at least 3% of the county's checklists (`CountyChart.EXPECTED_FLOOR`).
  - Packs are downloaded by state and judged by county.
  - Pick points double each round, 1 to 32; a called upset adds the seed gap.
  - A league season has a start date, always a Monday (`SeasonCalendar`). The six rounds are six
    consecutive weeks, Monday through Sunday; round 1 (`ROUND_OF_64`) is the first week.
  - Picks for the whole bracket lock when round 1 starts: the first Monday, 00:00 in the league's time
    zone. A player picks all 63 matchups before that.
  - A round is decided after its Sunday ends.
  - A league has one time zone, chosen when it is created; all its dates are in that zone. `:domain`
    works in `LocalDate`.
  - The season is `SPRING` when the start date is in January through June, else `FALL`.
  - A round covers every `TimeBin` that any of its seven days falls in. A bird is expected for a round
    when it is expected in any bin the round covers.
  - Seeding looks at every bin the six rounds cover.
  - A league holds at most 64 players (`LeagueLifecycle.MAX_PLAYERS`); a player is in one forming or
    running league at a time. Joining closes when the season starts.
  - A new league's start is a Monday at least 7 days ahead, in its time zone (`SeasonStart`).
  - A bracket needs 8 players when the season starts (`LeagueLifecycle.MIN_PLAYERS`). Short of 8 on the
    start date, the start moves back a week, up to four times; then the league is closed.
- **Round names are neutral** (`ROUND_OF_16`, `QUARTERFINAL`, `SEMIFINAL`, `FINAL`). "Sweet 16",
  "Elite 8" and "Final Four" are NCAA trademarks: not in code, strings or store text.
- **No Android auto-backup** (`allowBackup=false`, `fullBackupContent=false`, data extraction rules exclude
  all) until the app has its own sync.
- **Dynamic color is off.** Every color comes from the Flyway tokens (`ui/theme/Color.kt`).
- minSdk 29, targetSdk 37, compileSdk 37, JDK 17, Kotlin + Compose + Material 3.
- **IDE is Android Studio.** Keep AGP current (Studio's Upgrade Assistant).

## Design tokens

Values live in `ui/theme/`; don't type a color, radius or size into a screen.

- Colors: `MaterialTheme.colorScheme` for the Material roles, `FlywayTheme.colors` for the rest (pick,
  warning, live, chart). Every Material role is set in `Color.kt`, the `…Fixed` and `surfaceContainer…`
  ones included; one left out falls back to Material's purple.
- Color is never the only cue: a pick, a winner or a warning also has a word, an icon or a figure.
- Type: Inter for text (`MaterialTheme.typography`), score and seed styles in `FlywayTheme.text`. `Display`
  in `Type.kt` is where a second family would go; today it is Inter. Inter is bundled in `res/font`, its
  OFL text in `assets/licences/`.
- Corners from `MaterialTheme.shapes`; spacing and sizes from `FlywayDimens`. Touch targets ≥ 48dp.
- Flat by default: borders, not shadows.

## Conventions

- **Never commit on `main`.** All work goes on a branch named `<type>/<topic>` (Conventional Commit types:
  `feat/outing-flow`, `fix/seed-order`) and reaches `main` through a squash-merged pull request. If the
  checkout is on `main`, create the branch before the first edit. `.githooks/pre-commit` refuses a commit on
  `main`; turn it on once per clone with `git config core.hooksPath .githooks`.
- Small commits, each a Conventional Commit. The pull request title is one too (`pr-title.yml` checks it).
- **Keep `README.md` current in the same PR.** A change people would notice (a new screen or feature, a
  permission, a network call, a rule of the game) updates the README in that PR, not later.
- Work is tracked as GitHub issues; reference the issue number in the PR (`Closes #6`) and in code comments
  where a decision lands.
- Game logic goes in `:domain` with a test; ViewModels stay thin.
- A composable used by more than one screen lives in `ui/components/`.
- A screen is a `…Content` composable (plain state and callbacks) wrapped by a `…Screen` that reads the
  ViewModel, so it can be previewed and screenshot-tested. A `…Content` that needs today takes it as a
  parameter, never `LocalDate.now()`.
- **Every screen gets a screenshot test** in `ScreensScreenshotTest` (`compose.shoot(...)`), light and
  dark. It also runs the Accessibility Test Framework, where a warning fails; suppress a deliberate finding
  for that one kind of element, with a comment saying why.
- **All wording lives in `res/values/strings.xml`**, named `<screen>_<what>`. Composables use
  `stringResource` / `pluralStringResource`.
- Never format a figure or date with `Locale.US` or `String.format` for display.
- Never show placeholder data as if real; mark sample values clearly.
- Any Python helper scripts: use `uv run` with inline script dependencies.
- Public repo: never commit real personal data, keystores, `local.properties` or `.idea/`. Test fixtures
  use made-up values, for people and for bird figures.

## Agent skills

### Issue tracker

GitHub Issues on `mcosgriff/flyway-bracket`, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Five default triage roles. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root, created lazily. See
`docs/agents/domain.md`.
