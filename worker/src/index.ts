// The front of the hosted league server (#16, docs/server/hosting.md). Every request to
// api.flywaybracket.cosgriff.io reaches this Worker, which hands it unchanged to the one container that
// runs the Ktor server (server/Dockerfile). The Worker reads nothing in a request and logs nothing of it.

import { Container, getContainer } from "@cloudflare/containers";

interface Env {
  SERVER: DurableObjectNamespace<FlywayServer>;
  // Worker secrets, set by hand with `wrangler secret put` (docs/server/hosting.md); never in the repo.
  DATABASE_URL: string;
  DATABASE_USER: string;
  DATABASE_PASSWORD: string;
}

// One instance, always this name. The server keeps its account rate limiter and its once-a-minute
// SeasonStartJob in memory: two copies would each allow their own 10 account creations an hour, and both
// would settle the same leagues. wrangler.jsonc also sets `max_instances: 1`.
const INSTANCE = "flyway-server";

// Where the image keeps an empty packs directory (server/Dockerfile). No pack is served yet: a container's
// disk is wiped on restart, and packs built from eBird data need the Cornell Lab's permission first.
const PACKS_DIR = "/packs";

export class FlywayServer extends Container<Env> {
  // The server's PORT default (ServerConfig.DEFAULT_PORT).
  defaultPort = 8080;
  // After this long with no request the container stops, and nothing runs until the next request or cron.
  sleepAfter = "10m";
  // The server connects out to Postgres on Neon over JDBC (port 5432), which needs the internet. Set
  // explicitly: Cloudflare's docs don't state the default.
  enableInternet = true;

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.envVars = {
      DATABASE_URL: env.DATABASE_URL,
      DATABASE_USER: env.DATABASE_USER,
      DATABASE_PASSWORD: env.DATABASE_PASSWORD,
      PACKS_DIR,
    };
  }
}

function server(env: Env) {
  return getContainer(env.SERVER, INSTANCE);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    return server(env).fetch(request);
  },

  // Wakes the server so a league's dates pass on time when no one is using the app.
  //
  // Inside the JVM, SeasonStartJob settles every league that is due once a minute, and once at start; a
  // read of a league settles it too (Leagues.kt). Both need the container running, and a sleeping
  // container runs nothing. Settling fixes a league's field (Saturday 00:00, two days before the start),
  // starts its season and each round (Monday 00:00), and decides a round (Monday 12:00, 12 hours after
  // its Sunday ends): each in the league's own time zone, so at any hour in UTC, and some zones are on the
  // half or quarter hour. So the cron in wrangler.jsonc runs every hour: a league's date is settled at
  // most an hour late (45 minutes in the worst case, past a quarter-hour zone), or sooner by a read. Each
  // wake keeps the container up for `sleepAfter`.
  //
  // The request starts the container if it is asleep and waits for the server to listen; the server runs
  // SeasonStartJob as soon as it has started.
  async scheduled(_controller: ScheduledController, env: Env): Promise<void> {
    const response = await server(env).fetch(new Request("http://server/v1/health"));
    console.log(`Wake: health check answered ${response.status}`);
  },
} satisfies ExportedHandler<Env>;
