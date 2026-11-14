# Running the server

The league server is the `:server` module: Ktor on Netty, with Postgres behind it. It is not part of the
app; the app calls its account endpoints once the player agrees (#38). What it may be sent is decided by
[ADR 0001](../adr/0001-server-and-network.md); its endpoints are listed in [api.md](api.md). Today it has
the health check (`GET /v1/health`) and anonymous accounts (`POST /v1/accounts`, and `GET`, `PATCH` and
`DELETE /v1/account`).

```sh
curl -X POST -H 'Content-Type: application/json' -d '{}' http://localhost:8080/v1/accounts
```

answers `201` with a `playerId` and a `secret`. Later calls send them as HTTP Basic credentials:
`curl -u "<playerId>:<secret>" http://localhost:8080/v1/account`. A shell's history keeps that command, so
use only an account made for the test.

Where the server is hosted is not decided (issue #16). This page is for a developer's own machine.

## Configuration

The server reads four environment variables and nothing else: no config file, no command-line option.

| Variable | Meaning | When not set |
| --- | --- | --- |
| `DATABASE_URL` | A JDBC URL, such as `jdbc:postgresql://localhost:5432/flywaybracket` | The server does not start |
| `DATABASE_USER` | The Postgres user | The server does not start |
| `DATABASE_PASSWORD` | That user's password | The server does not start |
| `PORT` | The port to listen on, 1 to 65535 | 8080 |

`server/.env.example` holds made-up values that fit the local Postgres below. A real value never goes in
the repository: `.env` is ignored by git.

## A local Postgres

The server and its tests use Postgres 18. Start one in Docker, with the made-up values of
`server/.env.example`:

```sh
docker run --name flywaybracket-db --rm -d -p 5432:5432 \
  -e POSTGRES_DB=flywaybracket \
  -e POSTGRES_USER=flywaybracket \
  -e POSTGRES_PASSWORD=made-up-local-password \
  postgres:18-alpine
```

`docker stop flywaybracket-db` stops it and, because of `--rm`, deletes its data.

## Start the server

```sh
cp server/.env.example server/.env
set -a; . server/.env; set +a
./gradlew :server:run
```

At start the server connects to the database and applies every migration not yet applied. If the database
cannot be reached, it stops with an error instead of listening.

```sh
curl http://localhost:8080/v1/health
```

answers `{"version":"0.1.0","database":true}`. The version comes from `version.txt`. If the database stops
answering later, the health check still answers 200, with `"database":false`.

## Migrations

The schema is the SQL files in `server/src/main/resources/db/migration`, applied in order by the Flyway
migration library. (It shares a word with this app's name by coincidence.) A file is named
`V<number>__<what>.sql`.

- A migration that has been merged is never edited: Flyway refuses to start a database whose applied file
  has changed. A change to the schema is a new file with the next number.
- A migration gets a test in `DatabaseTest`.

## Tests

```sh
./gradlew :server:test
```

The tests that need a database start a real Postgres with Testcontainers, so Docker must be running.
Without Docker those tests fail; they are not skipped. `./gradlew build` runs them too.

## Logs

The server writes its log to standard error. A request is one line: its status, method and path. The log
never has a request's body, its query or its headers; the `Authorization` header carries the player's
secret. Keep it so when adding a log line.

## The container image

The image holds a Java runtime and the server's jars. It runs as a user that is not root.

```sh
./gradlew :server:installDist
docker build -t flyway-bracket-server server
docker run --rm -p 8080:8080 --env-file server/.env flyway-bracket-server
```

Inside a container `localhost` is the container itself, so `DATABASE_URL` must name a host the container
can reach: on Docker Desktop, `host.docker.internal`.

CI builds the image on every change to prove it builds. Nothing pushes it anywhere.
