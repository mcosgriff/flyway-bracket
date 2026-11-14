# Hosting the server

The league server the beta's phones reach is at `https://api.flywaybracket.cosgriff.io` (issue #16). Release
builds of the app use that address (`flywayServerUrl` in `data/build.gradle.kts`). This page says what runs
where, what the owner sets up once, and how a new version is deployed. It holds no secret, and no account
or project ID.

## What runs where

| Part | Where | Notes |
| --- | --- | --- |
| HTTPS and the name `api.flywaybracket.cosgriff.io` | Cloudflare, a Worker custom domain in the `cosgriff.io` zone | Cloudflare holds the certificate and ends the TLS connection |
| The Worker (`worker/src/index.ts`) | Cloudflare Workers | Hands every request, unchanged, to the container; wakes it every hour |
| The Ktor server (`server/Dockerfile`) | Cloudflare Containers, one instance, type `basic` (1 GiB, 1/4 vCPU) | Started by a Durable Object (`FlywayServer`); sleeps after 10 minutes with no request |
| Postgres 18 | Neon, free plan, region AWS `us-west-2`, branch `production` | Reached over the internet with the direct (not `-pooler`) JDBC URL and `sslmode=require` |

Cloudflare ends the HTTPS connection, so it handles everything the app sends; Neon stores everything the
server keeps. Both are named in the README's Privacy section, the user guide's privacy page and the app's
consent page.

The server reads its configuration from environment variables ([running.md](running.md#configuration)).
On Cloudflare the Worker passes them to the container:

| Variable | Comes from |
| --- | --- |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | Worker secrets, set by hand ([below](#4-the-database-secrets)) |
| `PACKS_DIR` | `/packs`, fixed in `worker/src/index.ts`: an empty directory in the image ([Packs](#packs-not-served-yet)) |
| `PORT` | The image's default, 8080 |

## One-time setup by the owner

These steps need the owner's Cloudflare, Neon and GitHub accounts. Nothing here is done by a workflow.

### 1. Cloudflare plan

Containers need the **Workers Paid** plan ($5 a month, plus the container's memory, vCPU and disk by the
second while it runs). `cosgriff.io` must be a zone on the same Cloudflare account; the first deploy adds
the `api.flywaybracket` custom domain and its certificate.

### 2. API token for the deploy job

In the Cloudflare dashboard, My Profile, API Tokens, create a custom token with only:

- Account: **Workers Scripts**: Edit
- Account: **Containers**: Edit
- Zone `cosgriff.io` only: **Workers Routes**: Edit

If the first deploy is refused for a missing permission, the error names it: add that one only.

Store the token, and the account ID, as secrets of the `production` environment, never in the repository:

```sh
gh secret set CLOUDFLARE_API_TOKEN --env production
gh secret set CLOUDFLARE_ACCOUNT_ID --env production
```

Each command asks for the value, so it stays out of the shell's history.

### 3. The `production` environment

In the repository's Settings, Environments, create `production`, add the owner as a **required reviewer**,
and limit its deployment branches to `main`. `deploy-server.yml` runs in it, so no deploy starts without
that approval, and only the job in it can read the token.

### 4. The database secrets

The server's database is the Neon project's `production` branch. Its credentials go to Cloudflare as
Worker secrets, and nowhere else.

1. Get them as [running.md](running.md#a-neon-database-development-branch) says, but for `production`:
   `neon checkout production`, then `uv run server/neon-env.py .env.local`. The script writes the direct
   JDBC URL with `sslmode=require`, the user and the password to `server/.env` (mode 600, ignored by git).
2. Set each one as a Worker secret, from `worker/`, after `npm ci`. Each command asks for the value; paste
   it from `server/.env`. Before the first deploy, wrangler asks whether to create the Worker: say yes.

   ```sh
   cd worker
   npx wrangler secret put DATABASE_URL
   npx wrangler secret put DATABASE_USER
   npx wrangler secret put DATABASE_PASSWORD
   ```

3. Delete `server/.env` and `.env.local`, and run `neon checkout dev` so the next local run is not against
   the live database.

The container reads them when it starts. Set them before the first deploy, or the server stops at start
with `Environment variables missing`. After changing one later, run the deploy again (Actions,
deploy-server, Run workflow) so the server restarts with it.

### 5. Check it

After the first deploy:

```sh
curl -s https://api.flywaybracket.cosgriff.io/v1/health
```

answers `{"version":"…","database":true}`. The first request after the container has slept waits for the
JVM to start and for Neon's compute to wake: several seconds.

## How a deploy runs

`.github/workflows/deploy-server.yml` runs on a push to `main` that changes `server/`, `api/`, `domain/` or
`worker/`, or by hand (Actions, deploy-server, Run workflow). It waits for approval in `production`, then:

1. `./gradlew :server:installDist`, with no Gradle cache, as in `release.yml`.
2. `npm ci` in `worker/`, so wrangler and the libraries are the versions in `worker/package-lock.json`.
3. `wrangler deploy` (cloudflare/wrangler-action): builds the image from `server/Dockerfile` on the
   runner's Docker, pushes it to Cloudflare's registry, and deploys the Worker and the container together.
   The running container is replaced; the server applies any new database migration when it starts.

The workflow sees only the API token and the account ID; the database credentials never pass through
GitHub.

## Why one instance

`max_instances` is 1 and the Worker sends every request to the one container named `flyway-server`. The
server keeps two things in memory: the limit on account creation (10 per address per hour), and
`SeasonStartJob`, which settles due leagues once a minute. Two copies would each keep their own limit, and
would both run the job. The job is safe to run twice, since each league is settled under its lock, but it
is wasted work.

## Sleep and the hourly wake

The container stops after 10 minutes with no request (`sleepAfter` in `worker/src/index.ts`), and while it
sleeps nothing runs: no `SeasonStartJob`. A league's dates fall at midnight on a Saturday (its field is
fixed), midnight on a Monday (its season or a round starts) and noon on a Monday (a round is decided), in
the league's own time zone, so at any hour in UTC. A read of a league settles it too, but no one may be
looking.

So a Cron Trigger (`0 * * * *` in `worker/wrangler.jsonc`) calls the Worker every hour. Its `scheduled`
handler asks the server for `/v1/health`, which starts the container if it is asleep; the server runs
`SeasonStartJob` as soon as it starts, then every minute until it sleeps again. A league's date is settled
at most an hour late, and 45 minutes in a time zone on the quarter hour. Each wake keeps the container up
for 10 minutes, about four hours a day in all.

## Packs: not served yet

The hosted server starts with an empty packs directory (`/packs`, made in `server/Dockerfile`). A container's
disk is wiped whenever it restarts, so a pack copied in would not last; and serving packs built from eBird
data needs the Cornell Lab's permission, which the project does not have yet. So, on the hosted server:

- `GET /v1/packs` is an empty list, and the app offers no pack to download.
- Every sighting is refused: a sighting's county and bird must be in a pack the server holds.
- No member's home county is in a pack, so seeding leaves every member out: a league's field has no birds,
  there is nothing to pick, and the league closes at its start (`TOO_FEW_BIRDS`).

Packs will be served once that permission is given and the server can read them from storage that lasts
(Cloudflare R2). Until then the hosted server is good for accounts and forming leagues only.

## Not done yet

- **The account creation limit sees one address.** Behind the Worker the server sees the address the
  container's traffic comes from, not the phone's, so the 10-per-hour limit counts every player together
  ([api.md](api.md#account)). The server must read the caller's address from a header the Worker sets
  before testers sign up in numbers.
- **Backups.** Neon's free plan keeps only a short restore window. The plan is a scheduled GitHub Actions
  job that runs `pg_dump` against the direct URL and keeps a few weeks of dumps in Cloudflare R2.
- **Waiting for CI.** The deploy does not wait for `ci.yml` on the same commit, as `release.yml` does; the
  approval is the check.
