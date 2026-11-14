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
  pack from files downloaded by hand ([`docs/packs.md`](docs/packs.md)).
- The Packs screen: "Add a pack" opens the phone's file picker for a `.fbpack` file, which the app checks
  and then keeps in its own storage. The screen lists each pack (state, counties, the day it was built,
  its size), says where its figures came from, and shows how many species the home county expects at some
  time of year. A pack can be removed after a warning, except the one that holds the home county. A file
  that is not a pack is turned away with the reason. The app does not download packs yet.
- Logging an outing. Start outing on Home starts one in the county the phone is in, found from its
  approximate location, so a trip to another county counts there. The first time, a page explains why
  before Android asks for the permission; saying no is fine. With no permission, no location within five
  seconds, or a location in no pack on the phone, the outing is in the home county, and the Outing screen
  says why when it was not your choice (without the home county's pack on the phone, nothing starts, and
  Home offers the Packs screen). The county can be changed by hand from the Outing screen's menu, from the
  counties of the packs on the phone. The Outing screen shows the time since the start,
  the place name (yours to type, or none), the county, and each bird with its count and plus and minus
  buttons; minus at one removes the bird after asking. Add sighting opens a search over the birds in the
  county's pack: birds expected there this week come first, most often reported first, then the rest by
  name, marked "Not expected this week". The search matches the start of any word in a name and ignores
  case and accents. Leaving the screen keeps the outing going, and Home's button becomes Continue
  outing. Finish ends it and shows a summary: the place, the times, the length, the species, the birds
  counted and the list. An outing can be discarded from the menu after a warning. Marking the birds in
  your bracket comes later.
- Outings and sightings saved on the phone, and Home's figures for the calendar year counted from them:
  species, new lifers, outings and the five most recent outings.
- The first run: the app asks for a display name (2 to 24 characters) and a home county, chosen from a
  list by state. Both stay on the phone until you agree to play in a league (below). The list ships with the app (codes and names from the US Census
  Bureau's list of counties) and holds Colorado only for now.
- An anonymous account on the league server, asked for on a consent page the first time you open League
  or Bracket. The page says what is sent, what other players see and how deleting works; Continue creates
  the account, Not now sends nothing and goes back Home. Without a network, or when the server does not
  answer, the page says which and offers Try again. League and Bracket themselves are not built yet.
- The app shell: a bottom bar (Home, Bracket, League, Packs) and the Home screen, which shows the home
  county, and the Packs screen. Bracket and League are placeholders. Every screen is screenshot-tested in light and dark and
  checked for accessibility (touch targets, contrast, labels) on each build.

- The [user guide](https://mcosgriff.github.io/flyway-bracket/), a small website built from `site/` and
  served by GitHub Pages. Its pages about things the app cannot do yet are marked "Coming in the beta".

- The league server (`server/`, [`docs/server/api.md`](docs/server/api.md)): anonymous accounts, and
  creating, joining, auto-joining and leaving leagues. The app does not talk to it yet.

What does not exist yet: downloading bird packs, the bracket screens, the League screen, and the app's side
of the server.

## How the game works

- **Leagues.** A league is a group of players, at most 64, who play one bracket together. Anyone can
  create one, open or invite-only, with a name, a time zone and a start on a Monday at least a week
  ahead; others join it with its 8-character invite code. A player is in one league at a time. A player
  without a league is added to the open league that starts first and has room, or to a new one starting
  on the second Monday from today. Joining closes when the season starts. A bracket needs at least 8
  players: a league short of 8 when its start date arrives starts a week later, up to four times, and is
  then closed. A player can leave at any time; after the start, their picks stop scoring and they no
  longer count toward any matchup.
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

The app asks for two permissions: approximate location (below), and `INTERNET`, to talk to the league
server (further below). Adding a pack uses the phone's file picker, which needs no permission: the app is
handed the one file you choose and sees no others.

**Approximate location** (`ACCESS_COARSE_LOCATION`) is used once, when an outing starts, to find the county
you are in. A county is far larger than the error of an approximate location, so the app never asks for
your precise location, and it never asks in the background. It uses Android's own location service, not
Google Play services. The coordinates are turned into a county on the phone and then forgotten: they are
never saved, logged or sent. Only the county is kept, with the outing. The app explains this on a page
before Android asks, the first time you start an outing. If you say no, outings are in your home county,
and you can change an outing's county by hand.

### The league server

Written down in [`docs/adr/0001-server-and-network.md`](docs/adr/0001-server-and-network.md). The account
is built: creating it, and sending your display name and home county when you set or change them.
Leagues, picks and sightings are not sent yet; the lines below say what will be.

- The app talks to one server, the project's own, over HTTPS, and to nothing else.
- Nothing is sent before you say yes. Outings work with no account. When you open League or Bracket
  without an account, a page says what will be sent and asks. "Not now" sends nothing.
- An account is anonymous: no email, no password, no phone number. The server gives the app an id and a
  secret, and the app keeps them encrypted on the phone with a key in the Android Keystore.
- The server will store your display name, your home county, your leagues, your bracket picks, and each
  sighting as a bird name, a county and a date. It will never get coordinates, the time of day or the
  place name you typed.
- Other players in your league will see your display name, your picks once they lock, your points, and how
  many birds you logged in a round. They will not see your county or your individual sightings.
- You will be able to delete your account from Settings (not built yet). The server then deletes your name, picks and
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
