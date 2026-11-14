# ADR 0001: what the app sends to the server, and when

- Status: accepted. The account endpoints are built, in the server (#31) and the app (#38), and so are
  creating, joining and leaving a league in the app (#40); the rest is the plan.
- Date: 2026-11-13
- Issue: #28

## Context

Leagues cannot work on one phone. A matchup is decided by what every player in a league logged, so picks
and sightings have to reach a place all the players' phones can read. That means a server, and it means
data leaves the phone for the first time.

The project already has rules that bound this:

- No trackers, analytics, crash reporting or ads.
- A sighting says where a person was on a given day. Players should not have to give up more of that than
  the game needs.
- The first release is a small beta, run by one person in their own time.
- `CLAUDE.md` requires this record before the first network call.

## Decision

### One server

The app talks to one server, the project's own, over HTTPS. It talks to no other host. There is no
analytics and no crash reporting.

Reason: the no-trackers rule allows nothing else, and one host is easy to check. Anyone can read this
record, watch the app's traffic and see that the two agree.

### Anonymous device accounts

On first use of an online feature the app asks the server for an account. The server returns an id and a
secret. The app asks for no email, no password and no phone number. The secret is kept on the phone,
encrypted with a key in the Android Keystore.

Reason: the game needs to tell players apart, not to know who they are. With no email and no password the
server holds nothing that names a person, and there is no password to store, reset or leak. The secret is
the only proof that an account belongs to a phone, so it is not stored in plain form.

### What the app sends, and what the server stores

| Field | When it is sent | Why the game needs it |
| --- | --- | --- |
| Account id and secret | With each request, after the server has issued them | To know which player a request comes from |
| Display name | When the player sets or changes it | To show the player to the others in the league |
| Home county code | When the player sets or changes it | Eligibility and seeding are judged by county |
| League membership | When the player creates, joins or leaves a league | To know whose picks and sightings count together |
| League name | When the player creates a league | To show the league to its players |
| League open or invite-only | When the player creates a league | Auto-join places players only in open leagues |
| League time zone | When the player creates a league | Every date of the league's season is a date in it |
| League start date | When the player creates a league | The season and its rounds start on it, and picks lock then |
| Bracket picks | When the player makes or changes them | To score them |
| Sighting: bird name | When the player logs a bird | To decide the matchup |
| Sighting: county code | With the sighting | A county where the player logged makes them eligible there |
| Sighting: date | With the sighting | To know which round the sighting belongs to |
| Sighting: random id | With the sighting, and to delete it; stored with the sighting | A random UUID the phone makes for each sighting, unrelated to the player or the device: a retried upload is not counted twice, and the phone can delete a sighting it sent |
| State code of a bird pack | When the player downloads or updates that state's pack (#39) | To send that state's pack |
| Version of the pack held | When the player updates a pack the phone has (#39) | So that an unchanged pack is not sent again |

The request that creates an account carries nothing about the player.

The app never sends, and the server never stores:

- coordinates;
- the time of day of a sighting;
- the place name the player typed.

Reason: every field in the table is used by a rule of the game. The three left out are used by none. They
are also the ones that could show where a person lives or walks, so they stay on the phone.

### What other players in the league can see

- display name;
- picks, once they lock;
- points;
- how many birds the player logged in a round.

They cannot see the player's county or the player's individual sightings.

Reason: this is what a league table needs. A county, or a list of what someone saw and on which day, tells
the others where that person was. Picks are hidden until they lock so that no player can copy another's.

### Nothing is sent before the player says yes

Outings work with no account. The first time the player opens League or Bracket, or asks for bird packs
(#39), a page says what will be sent and asks. Until the player agrees, the app makes no network call.

Reason: a player should know what leaves the phone before it leaves. A person who only wants to log
outings never needs an account and never sends anything.

### Deleting an account

A player can delete their account from Settings. The server then deletes their name, picks and sightings.

Deleting an account removes it from the live database at once. Encrypted nightly backups keep a copy for
up to 35 days, after which it is gone everywhere (#16, `docs/server/backups.md`). If the server ever has to
be restored from a backup, accounts deleted since that backup are deleted again. For that, the server keeps
each deleted account's random ID and the time it was deleted, and nothing else, for 35 days; after a
restore the operator deletes those accounts again, as `docs/server/backups.md` says.

Reason: the data is the player's. Leaving the game should remove it, without having to ask anyone.

### Losing the phone

In the beta, losing the phone loses the account. Moving an account to a new phone is a later feature.

Reason: this follows from accounts with no email or password: nothing but the secret on the phone proves
who owns the account. For a small beta that cost is accepted in return for holding no contact details.

### The server

The server is Ktor with Postgres. It lives in this repository and runs the same `:domain` rules as the
app.

Reason: `:domain` is pure Kotlin with no Android imports, so the server can use it as it is. One set of
rules means the app and the server cannot disagree about who is eligible or which bird won. One repository
means a rule and both of its users change in the same pull request.

### Where it runs

The server runs on Cloudflare, which also handles the secure connection, so everything the app sends
passes through Cloudflare. The server's database is stored by Neon, on Amazon Web Services in Oregon, USA
(region us-west-2). The app's one host is `api.flywaybracket.cosgriff.io` (#16, `docs/server/hosting.md`).

Reason: one bill for a small beta, deploys from GitHub after approval, and no server at home next to
personal files. Cloudflare and Neon are processors of the data in the table above, not extra hosts the app
talks to; the consent page, the README's Privacy section and the user guide's privacy page name both.

## Consequences

- The app will need the `INTERNET` permission. It must be added to `.github/privacy/permissions.txt` and
  explained in the app when the first network code lands.
- The README's Privacy section and the consent page must say the same thing as the table above. A new
  field sent to the server needs a change to this record first.
- A sighting on the phone holds more than the server gets. The code that builds the request must drop the
  coordinates, the time of day and the place name; that is worth a test.
- The server knows each player's home county and the county and date of each sighting. That is coarser
  than a location, but it is still personal data, and the server must be looked after as such.
- Like any server, this one sees the IP address of each connection, and so does Cloudflare. The server uses
  it only to limit how many accounts one address creates, counted in memory (#16). Cloudflare also keeps a
  trace of each request for 7 days, so the operator can see how the server is doing: its method and path,
  its status, how long it took, the app's user agent, and the country, region and city Cloudflare works out
  from the IP address. A trace never holds the secret, a request's body or the IP address itself
  (`docs/server/hosting.md`). The server's own log keeps a request's status, method and path, never its
  body, query or headers.
- Changing the host, or adding a company that handles the data, needs a change to this record and to the
  consent page first, as a new field does.
- A player who loses or resets their phone, or clears the app's data, loses their account, league place
  and points. The app should say so before the player agrees.
- Deleting an account cannot be undone, for the same reason: there is no way to prove who the player was.
- The project now has a server to run, back up and keep patched, and a Postgres database to migrate.
- `:domain` must stay free of Android imports, because the server depends on it.

### Not decided here

- Who can see a league's own fields (its name, whether it is open, its time zone and its start date). The
  app sends them when the player creates a league (#40), as the table above says.
- How long the server keeps data after a season ends.
- What happens to matchups already decided when a player deletes their account, and whether deletion also
  covers the home county and league membership.
- How the server learns of an outing in which the player logged no birds. The eligibility rule counts the
  county of any outing, but only sightings are sent.
