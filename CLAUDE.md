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

`:domain` has the game rules. `:data` has the database (one table, `outings`, not used by a screen yet) and
the player's settings. `:app` has the theme, the bottom bar and the Home screen; Bracket, League, Packs and
Outing are placeholders (`NotBuiltYetContent`). No screen reads or saves anything yet. No network, no server.
`./gradlew build` is green (Gradle 9.9.1, AGP 9.5.1, lint included). Keep it green; lint fails the build on
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
- `./gradlew koverHtmlReportCoverage` — coverage of `:domain`, `:data` and `:app`; CI sends the XML to
  Codecov.
- `./gradlew :app:installDebug` — install on a connected phone (debug app id
  `io.cosgriff.flywaybracket.debug`).
- Release: merge the PR `release-please` keeps open (version and changelog come from Conventional Commits);
  it tags `vX.Y.Z` and starts `release.yml`, which builds the signed APK after approval in the `release`
  environment. No release can be built until that environment has its signing secrets.

## Modules

- `:domain` — pure Kotlin, **no Android imports**. All game rules live here and get unit tests. Results
  are typed values, never English text.
  - `domain/pack` — `TimeBin` (48 a year, four per month), `CountyChart`, `BarChartParser`.
  - `domain/bracket` — `Round`, `Team`, `Matchup`, `Seeding`, `MatchupRule`, `PickScore`.
  - `domain/player` — `PlayerSettings` (display name, home county code).
- `:data` — what the app saves on the phone, mapped to and from `:domain` types. Depends on `:domain`.
  - `data/db` — `FlywayDatabase` (Room 3), its entities and DAOs. Each version's schema is exported to
    `data/schemas` and committed (CI fails if it isn't). A schema change raises the version and adds a
    migration; `MigrationTest` opens every exported version as the current database.
  - `SettingsRepository` — `PlayerSettings` in DataStore, as a `Flow`.
  - `data/di/DataModule` — Hilt: provides the database, its DAOs, the DataStore and the repositories.
  - Tests run on the JVM under Robolectric; Gradle resolves its Android jar (root `build.gradle.kts`).
- `:app` — Compose UI, theme, navigation (Navigation 3, `@Serializable` `NavKey` routes in
  `ui/FlywayNavHost.kt`). Hilt: `FlywayApp` is the application, `MainActivity` an entry point.

Add `:baselineprofile` once the screens settle.

## Decisions — don't change without asking

- **applicationId `io.cosgriff.flywaybracket` must never change. The release signing key must never
  change** once the first release is out.
- **No trackers, analytics, crash reporting or ads.** CI and `release.yml` scan the release APK with Exodus Privacy
  (`.github/scripts/exodus-scan.sh`): a tracker fails, and so does a permission not in
  `.github/privacy/permissions.txt` (or one listed that the APK no longer asks for). A new permission means
  updating that list and explaining it in the app.
- **Network: none yet; the plan is `docs/adr/0001-server-and-network.md`.** One server, the project's own,
  over HTTPS; anonymous device accounts; nothing sent before the player says yes. The ADR lists every
  field sent: change it before sending anything else.
- **eBird data never goes in the repo** (eBird's terms of use). Tests and previews use made-up figures.
  Local downloads go in `packs-local/`, which is ignored. Shipping data derived from eBird needs the
  Cornell Lab's permission, not yet obtained.
- **Game rules** (each is a constant or function in `:domain`, with tests):
  - A matchup is won by the higher share of *eligible* players who logged the bird; a tie goes to the
    better seed. A side with fewer than `MatchupRule.MIN_ELIGIBLE` eligible players cannot win on share.
  - A player is eligible for a bird when it is expected that week in their home county or in a county
    where they logged an outing.
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
