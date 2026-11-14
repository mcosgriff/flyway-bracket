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
  answer, the page says which and offers Try again. The League screen itself is not built yet.
- The Bracket tab, once the phone has an account: your league's bracket, read from the server and kept on
  the phone. Three views: a list by round (tabs with the neutral round names, and a quarter at a time up to
  the quarterfinals), a tree of one quarter, and the whole field of 64, which scrolls both ways. Before
  picks lock, tapping a bird picks it to win; the tab shows how many of the 63 picks are made and when
  picks lock, and changing a pick that later picks depend on names those picks and asks first. Picks are
  saved on the phone and sent to the server; ones the server has not taken yet are sent next time. After
  the lock picks are read-only, the round being played is marked live, and a decided matchup shows each
  bird's share of its eligible players with the count ("62% of 13") and the points your pick won. A
  decided round shows its winners, upsets and your points. Tapping a matchup after the lock opens it:
  both birds, how many eligible players logged each, who won and why (the higher share, a tie to the
  better seed, or too few eligible players), your pick and points, and whether you were eligible for and
  logged each bird, from the outings and packs on the phone. With no league, or a league still forming,
  the tab says so and points to League. If picks locked before your latest changes reached the server, or
  the preview's birds changed and cleared picks, the tab says so.
- Sending sightings and fetching results in the background, once the phone has an account. Finishing an
  outing queues its sightings; removing one the server may have queues a delete. Android's WorkManager
  sends the queue when the phone is online, tries again later after a failure, and keeps it across a
  restart. Only
  sightings dated inside your league's season are sent: ones logged before you joined go once you have,
  and the rest stay on the phone. The same background job fetches your league, bracket and results when
  the app is opened, and every hour at most while a season is running. The Summary screen says whether an
  outing has been sent, is waiting, or stays on the phone; Home shows a note with "Send now" when
  sightings have waited more than a day.
- Settings, from the gear on Home. Profile: the display name and home county, changed with the first
  run's rules, and sent to the server when there is an account. Account: whether the phone has one, and
  "Delete my account", which says what the server deletes and that outings stay on the phone, asks twice,
  then deletes the account on the server and the phone. Privacy: the consent page's list, and a link to
  the user guide's privacy page. About: the version, links to the user guide and to report a problem
  (both open in the browser), and where the installed packs' bird data came from. Licences: the app's
  licence line, Inter's licence, and the open-source libraries in the app, listed at build time from
  their Maven POMs.
- The app shell: a bottom bar (Home, Bracket, League, Packs) and the Home screen, which shows the home
  county, and the Packs screen. League is a placeholder. Every screen is screenshot-tested in light and dark and
  checked for accessibility (touch targets, contrast, labels) on each build.

- The [user guide](https://mcosgriff.github.io/flyway-bracket/), a small website built from `site/` and
  served by GitHub Pages. Its pages about things the app cannot do yet are marked "Coming in the beta".

- The league server (`server/`, [`docs/server/api.md`](docs/server/api.md)): anonymous accounts,
  creating, joining, auto-joining and leaving leagues, listing and downloading the state packs it is
  given (to signed-in players only), taking and deleting a player's sightings, each kept as a bird
  name, a county, a date and the random id the app made for it only, and seeding each league's bracket: a preview before the season, fixed
  48 hours before it starts. The app uses it for the account, the bracket and sightings so far.

What does not exist yet: downloading bird packs, the League screen, and the rest of the app's side of the
server (joining and leaving leagues).

## How the game works

- **Leagues.** A league is a group of players, at most 64, who play one bracket together. Anyone can
  create one, open or invite-only, with a name, a time zone and a start on a Monday at least a week
  ahead; others join it with its 8-character invite code. A player is in one league at a time. A player
  without a league is added to the open league that starts first and has room, or to a new one starting
  on the second Monday from today. Joining closes when the season starts. A bracket needs at least 8
  players: a league short of 8 when its start date arrives starts a week later, up to four times, and is
  then closed. A league is also closed on its start date when too few migrating birds pass through its
  players' home counties to fill a field of 64. A player can leave at any time; after the start, their
  picks stop scoring and they no longer count toward any matchup.
- **Seasons.** Two tournaments a year, spring and fall. Each seeds its own field of 64 birds in four
  quarters of 16.
- **Calendar.** A league's season starts on a Monday and runs six rounds, one a week, Monday through
  Sunday. A season that starts in January through June is spring; otherwise it is fall. Picks for the
  whole bracket, all 63 matchups, lock when the first round starts: midnight on the first Monday, in the
  league's time zone, which is chosen when the league is created. A round is decided 12 hours after its
  Sunday ends, at noon on Monday, so a phone that was offline over the weekend can still send its
  sightings; one sent later does not change the round. A round covers every time bin (four per month)
  that one of its seven days falls in: a bird is expected for the round when it is expected in any of
  them, and seeding looks at the bins of all six rounds.
- **Seeding.** A league's field is drawn from the birds that migrate through its members' home counties and
  are expected there during the season; residents, there all year, are left out. The four quarters are
  Songbirds, Waterbirds, Fliers (hummingbirds, swifts, swallows, nighthawks and raptors) and Waterfowl,
  each bird in its group's quarter by the family word in its name. Each quarter takes its 16 birds most
  often reported in the members' home counties during the season, seeded 1 to 16, and seed 1 meets seed
  16 first. A quarter whose group is short is filled with the strongest birds left over from the others.
  Until 48 hours before the start the bracket is a preview, seeded again as players join, leave or change
  home county, so picks can be made early; a pick for a matchup whose birds changed is dropped. From then
  on the field is fixed: a late joiner's county no longer changes it. A player whose home county is in no
  pack the server holds is left out of seeding, and is told so.
- **Picks.** A right pick scores 1, 2, 4, 8, 16 and 32 points by round. Calling an upset adds the gap
  between the two seeds. Picks can be saved a few at a time until they lock; a matchup with no pick scores
  nothing. A bird picked to win a later matchup must be picked to win every earlier one on its way there.
  Other players' picks are hidden until they lock.
- **Matchups.** Friends in one league can live far apart, so a bird is judged only among *eligible*
  players: those for whom the bird is expected that week in their home county, as it was when the week
  began, or in a county where they logged an outing that week. A sighting by a player who was not
  eligible for the bird does not count. The bird with the higher share of eligible players who
  logged it wins; a bird with fewer than 3 eligible players has no share. A tie goes to the better seed.
- **Standings.** A league's table ranks its players by points, then by right picks; players level on both
  share a rank, the earlier joiner listed first. Others in the league see each player's name and points,
  never their county or their sightings: a matchup's result shows only how many were eligible and how
  many logged each bird. The season ends when the final is decided.
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
server (further below). Android's WorkManager library, which sends sightings in the background, adds four
that are granted at install and never asked for: `ACCESS_NETWORK_STATE` (to wait for a network),
`WAKE_LOCK` (to finish a send), `RECEIVE_BOOT_COMPLETED` (to keep the queue across a restart) and
`FOREGROUND_SERVICE` (declared by the library; the app never starts a foreground service or shows a
notification, and asks for no notification permission). Adding a pack uses the phone's file picker, which
needs no permission: the app is handed the one file you choose and sees no others.

**Approximate location** (`ACCESS_COARSE_LOCATION`) is used once, when an outing starts, to find the county
you are in. A county is far larger than the error of an approximate location, so the app never asks for
your precise location, and it never asks in the background. It uses Android's own location service, not
Google Play services. The coordinates are turned into a county on the phone and then forgotten: they are
never saved, logged or sent. Only the county is kept, with the outing. The app explains this on a page
before Android asks, the first time you start an outing. If you say no, outings are in your home county,
and you can change an outing's county by hand.

### The league server

Written down in [`docs/adr/0001-server-and-network.md`](docs/adr/0001-server-and-network.md). The account
is built: creating it, and sending your display name and home county when you set or change them. The
Bracket tab reads your league from your account, fetches its bracket and results, and sends your picks as
you make them. Sightings are sent in the background. Joining leagues is not built in the app yet; the
lines below say what will be sent then.

- The app talks to one server, the project's own, over HTTPS, and to nothing else.
- Nothing is sent before you say yes. Outings work with no account. When you open League or Bracket
  without an account, a page says what will be sent and asks. "Not now" sends nothing.
- When data is sent, once you have an account: your display name and home county when you set or change
  them; your picks as you make them; your sightings when you finish an outing (or, offline, as soon as
  the phone is online again, or when you tap "Send now" on Home), and only those dated inside your
  league's season, so ones logged before you joined go once you have; a delete when you remove a sighting
  the server may have. Opening the app, and every hour at most while a season is running, fetches your
  league, bracket and results, which sends nothing but your account id and secret.
- An account is anonymous: no email, no password, no phone number. The server gives the app an id and a
  secret, and the app keeps them encrypted on the phone with a key in the Android Keystore.
- The server stores your display name, your home county, your leagues, your bracket picks, and each
  sighting as a bird name, a county, a date and a random id the app makes for it, which says nothing
  about you or your phone: it stops a sighting sent twice from counting twice, and lets the app delete
  it. It never gets
  coordinates, the time of day, how many of a bird you counted or the place name you typed.
- Other players in your league will see your display name, your picks once they lock, your points, and how
  many birds you logged in a round. They will not see your county or your individual sightings.
- You can delete your account from Settings. The server then deletes your name, picks and
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
SIL Open Font License (`app/src/main/assets/licences/inter-OFL.txt`). The app's own licence line is one
string, `app_licence` in `app/src/main/res/values/strings.xml`.

The app's Licences screen lists every library in the release APK with its licence. The build writes that
list (`libraryLicences` in `app/build.gradle.kts`) from the libraries' Maven POMs; no plugin or library
does it, and the app makes no call to show it. A library whose POM names no licence fails the build.

Flyway Bracket is not affiliated with eBird or the Cornell Lab of Ornithology.
