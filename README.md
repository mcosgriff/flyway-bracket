# Flyway Bracket

[![CI](https://github.com/mcosgriff/flyway-bracket/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/mcosgriff/flyway-bracket/actions/workflows/ci.yml)
[![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](#build)

A bird-watching bracket game for Android. Each migration season, 64 migrating birds are seeded into a
bracket. Players in a league pick winners, then go outside and log the birds they see. A matchup is won by
the bird that more of the league found.

New here and want to play, not read code? Read the
[user guide](https://mcosgriff.github.io/flyway-bracket/).

## Status

Early development. Nothing is released.

What exists:

- The game rules in `:domain`, unit-tested: time bins, county bar charts, which birds are expected and
  which are migrants, seeding, matchup decisions and pick scoring.
- The state pack file format (`<state code>.fbpack`, gzipped JSON): a state's counties, each with its bar
  chart and its outline, and finding the county a place is in. A developer's command-line tool builds a
  pack from files downloaded by hand ([`docs/packs.md`](docs/packs.md)). The app does not download or
  read a pack yet.
- Outings and sightings saved on the phone, and Home's figures for the calendar year counted from them:
  species, new lifers, outings and the five most recent outings. No screen logs an outing yet, so those
  parts of Home still show their empty state.
- The first run: the app asks for a display name (2 to 24 characters) and a home county, chosen from a
  list by state. Both stay on the phone. The list ships with the app (codes and names from the US Census
  Bureau's list of counties) and holds Colorado only for now.
- The app shell: a bottom bar (Home, Bracket, League, Packs) and the Home screen, which shows the home
  county. The other screens are placeholders. Every screen is screenshot-tested in light and dark and
  checked for accessibility (touch targets, contrast, labels) on each build.

- The [user guide](https://mcosgriff.github.io/flyway-bracket/), a small website built from `site/` and
  served by GitHub Pages. Its pages about things the app cannot do yet are marked "Coming in the beta".

What does not exist yet: bird packs on the phone, the screens for logging an outing, the bracket screens, leagues, and
the server that leagues need.

## How the game works

- **Leagues.** A league is a group of players. Anyone can create one. A player without a league is added to
  an open one.
- **Seasons.** Two tournaments a year, spring and fall. Each seeds its own field of 64 birds in four
  quarters of 16.
- **Calendar.** A league's season starts on a Monday and runs six rounds, one a week, Monday through
  Sunday. A season that starts in January through June is spring; otherwise it is fall. Picks for the
  whole bracket, all 63 matchups, lock when the first round starts: midnight on the first Monday, in the
  league's time zone, which is chosen when the league is created. A round is decided after its Sunday
  ends. A round covers every time bin (four per month) that one of its seven days falls in: a bird is
  expected for the round when it is expected in any of them, and seeding looks at the bins of all six
  rounds.
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
`packs-local/`, which git ignores. [`docs/packs.md`](docs/packs.md) says how to build a state pack there.
Use of eBird data in a released app needs permission from the Cornell
Lab of Ornithology; that has not been obtained yet.

## Privacy

No trackers, analytics, crash reporting or ads. Every build's release APK is scanned with
[Exodus Privacy](https://exodus-privacy.eu.org); a tracker fails the build, and so does a permission that
is not listed in [`.github/privacy/permissions.txt`](.github/privacy/permissions.txt).

Today the app asks for no permissions and makes no network calls. Logging an outing will need location;
that will be explained here and in the app when it is built.

### Planned: the league server

None of this is built yet. It is the plan, written down in
[`docs/adr/0001-server-and-network.md`](docs/adr/0001-server-and-network.md).

- The app will talk to one server, the project's own, over HTTPS, and to nothing else.
- Nothing will be sent before you say yes. Outings will work with no account. The first time you open
  League or Bracket, a page will say what will be sent and ask.
- An account will be anonymous: no email, no password, no phone number. The server gives the app an id and
  a secret, and the secret is kept encrypted on the phone.
- The server will store your display name, your home county, your leagues, your bracket picks, and each
  sighting as a bird name, a county and a date. It will never get coordinates, the time of day or the
  place name you typed.
- Other players in your league will see your display name, your picks once they lock, your points, and how
  many birds you logged in a round. They will not see your county or your individual sightings.
- You will be able to delete your account from Settings. The server then deletes your name, picks and
  sightings.
- In the beta, losing the phone loses the account. Moving an account to a new phone comes later.

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
