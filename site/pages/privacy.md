---
title: Your privacy
description: What stays on your phone, what is sent, what your league sees, and how to delete your account.
beta: true
---

## In short

The app has no trackers, no analytics, no crash reporting and no ads. That will not change.

Outings work without an account, and then the app sends nothing. It connects to the internet only after
you agree to make a league account, and then only to the project's own server.

It asks for one permission: your approximate location, once when an outing starts, to find the county you
are in. Your location is turned into a county on your phone, then forgotten. It is never saved and never
sent. Only the county is kept, with the outing. If you say no, outings are in your home county.

Android also gives the app two permissions when it is installed, without asking you: one to use the
internet, for the server, and one to tell whether your connection may cost you for data, so that a bird
pack over 5 MB asks before it downloads on mobile data.

## One server, and only when you say yes

Leagues need a server. A matchup is decided by what every player in a league logged, so that information
has to reach one place.

The app talks to one server, the project's own. It talks to nothing else.

The server runs on Cloudflare, which also handles the secure connection, so everything the app sends
passes through Cloudflare. The server's database is stored by Neon, on Amazon Web Services in Oregon, USA
(region us-west-2).

Nothing is sent before you say yes. The first time you open the League or Bracket tab, or ask for bird
packs, a page says what will be sent and asks you. Until you agree, the app makes no connection. "Not now"
sends nothing.

Outings work with no account. If you only want to log birds, you never have to agree, and nothing is ever
sent.

## Your account

Your account is anonymous. The app does not ask for an email address, a password or a phone number.

Instead, the server gives the app an id and a secret. The secret is kept on your phone, locked away by
Android.

## What is sent

| What | When it is sent | Why the game needs it |
| --- | --- | --- |
| Your account id and secret | With each request | To know which player a request comes from |
| Your display name | When you set or change it | To show you to the others in your league |
| Your home county | When you set or change it | Seeds, and who is eligible, are judged by county |
| Your leagues | When you create, join or leave one | To know whose picks and sightings count together |
| A league you create: its name, whether it is open, its time zone and its start date | When you create it | To show it to its players, let open leagues take players, and date its season |
| Your bracket picks | When you make or change them | To score them |
| Each sighting: the bird's name | When you finish an outing, or once the phone is online again | To decide the matchup |
| Each sighting: the county | With the sighting | Logging in a county makes you eligible there |
| Each sighting: the date | With the sighting | To know which round it belongs to |
| Each sighting: a random code | With the sighting, and to delete it | So a sighting sent twice counts once, and the app can delete it. The phone makes it; it says nothing about you or your phone |

The server stores these things. Only sightings dated inside your league's season are sent; ones you
logged before you joined go once you have. Removing a sighting the server may have sends a delete.

Opening the app, and every hour at most while a season is running, fetches your league, bracket and
results. That sends nothing but your account id and secret.

Downloading a bird pack asks the server for its list of packs and for the pack of one state. To update a
pack, the app sends the version it already has, so an unchanged pack is not sent again.

## What stays on your phone

Three things are never sent, and the server never stores them:

- your coordinates, meaning your exact position;
- the time of day of a sighting;
- the place name you typed for an outing.

No rule of the game uses them. They are also the details that could show where you live or walk. So they
stay on your phone.

## What your league sees

The other players in your league can see:

- your display name;
- your picks, once they lock;
- your points;
- how many birds you logged in a round.

They cannot see your county. They cannot see your individual sightings.

## What the server still knows

The server knows your home county. It knows the county and the date of each sighting. That is less exact
than a location, but it is still information about you.

Like any server, it also sees the internet address of each connection, and so does Cloudflare.

## Deleting your account

You can delete your account from Settings in the app. You do not have to ask anyone.

The server then deletes your name, your picks and your sightings.

Deleting your account removes it from the live database at once. Encrypted nightly backups keep a copy for
up to 35 days, after which it is gone everywhere. If the server ever has to be restored from a backup,
accounts deleted since that backup are deleted again. For that, the server keeps a deleted account's random
ID, and nothing else about it, for those 35 days.

Deleting cannot be undone.

## If you lose your phone

In the beta, losing your phone loses your account. So does resetting the phone, or clearing the app's
data. Your place in your league and your points go with it.

This is the cost of an account with no email and no password. Only the secret on your phone proves the
account is yours.

Moving an account to a new phone will come later.

## Read the full plan

The plan is written down in the project's
[record of the decision](https://github.com/mcosgriff/flyway-bracket/blob/main/docs/adr/0001-server-and-network.md).
This page must say the same thing. If the two ever differ, please [tell us](questions.html#report-a-problem).
