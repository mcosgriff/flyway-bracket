# Flyway Bracket

[![CI](https://github.com/mcosgriff/flyway-bracket/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/mcosgriff/flyway-bracket/actions/workflows/ci.yml)
[![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](#build)

A bird-watching bracket game for Android. Each migration season, 64 migrating birds are seeded into a
bracket. Players in a league pick winners, then go outside and log the birds they see. A matchup is won by
the bird that more of the league found.

## Status

Early development. Nothing is released.

What exists:

- The game rules in `:domain`, unit-tested: time bins, county bar charts, which birds are expected and
  which are migrants, seeding, matchup decisions and pick scoring.
- The app shell: a bottom bar (Home, Bracket, League, Packs) and the Home screen with its empty state.
  The other screens are placeholders.

What does not exist yet: bird packs on the phone, outings and sightings, the bracket screens, leagues, and
the server that leagues need.

## How the game works

- **Leagues.** A league is a group of players. Anyone can create one. A player without a league is added to
  an open one.
- **Seasons.** Two tournaments a year, spring and fall. Each seeds its own field of 64 birds in four
  quarters of 16.
- **Seeding.** Birds are seeded by how often they are reported in the league members' home counties during
  the season.
- **Picks.** A right pick scores 1, 2, 4, 8, 16 and 32 points by round. Calling an upset adds the gap
  between the two seeds.
- **Matchups.** Friends in one league can live far apart, so a bird is judged only among *eligible*
  players: those for whom the bird is expected that week, in their home county or a county where they
  logged an outing. The bird with the higher share of eligible players who logged it wins. A tie goes to
  the better seed.
- **Packs.** Bird data is downloaded by state and judged by county. A bird is expected in a county in a
  given week when it is on at least 3% of checklists there.

## Bird data

The expected-bird tables are built from [eBird](https://ebird.org) bar chart data. eBird data is not in
this repository and must not be committed: tests use made-up figures, and local downloads go in
`packs-local/`, which git ignores. Use of eBird data in a released app needs permission from the Cornell
Lab of Ornithology; that has not been obtained yet.

## Privacy

No trackers, analytics, crash reporting or ads. Every build's release APK is scanned with
[Exodus Privacy](https://exodus-privacy.eu.org); a tracker fails the build, and so does a permission that
is not listed in [`.github/privacy/permissions.txt`](.github/privacy/permissions.txt).

Today the app asks for no permissions and makes no network calls. Logging an outing will need location,
and leagues will need a server; each will be explained here and in the app when it is built.

## Build

Android 10 or later (minSdk 29). JDK 17. Open the folder in Android Studio, or:

```
./gradlew build
```

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the tests and the pull request rules.

## Licence

No licence has been chosen yet, so all rights are reserved for now. Inter, the bundled font, is under the
SIL Open Font License (`app/src/main/assets/licences/inter-OFL.txt`).

Flyway Bracket is not affiliated with eBird or the Cornell Lab of Ornithology.
