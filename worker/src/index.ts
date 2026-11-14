// The front of the hosted league server (#16, docs/server/hosting.md). Every request to
// api.flywaybracket.cosgriff.io reaches this Worker, which hands it to the one container that runs the
// Ktor server (server/Dockerfile), adding only the caller's address (CLIENT_IP_HEADER). The Worker reads
// nothing else in a request and logs nothing of it.

import { Container, getContainer } from "@cloudflare/containers";
import type { DurableObject } from "cloudflare:workers";

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

// How long a request waits for a sleeping server to start listening. The library's default is 20 s in all;
// a cold start here is the JVM starting, Neon's compute waking and the migrations running, and the server
// opens its port only after all three.
const START_TIMEOUT_MS = 90_000;

// Behind this Worker the server sees the Worker's address, not the phone's. So the Worker puts the caller's
// address, from Cloudflare's CF-Connecting-IP, in this header on every request, replacing any the caller
// sent, and tells the server (CLIENT_IP_HEADER) to read it. The server uses it only to count account
// creations in memory; it never stores or logs it.
const CLIENT_IP_HEADER = "X-Flyway-Client-Ip";

export class FlywayServer extends Container<Env> {
  // The server's PORT default (ServerConfig.DEFAULT_PORT).
  defaultPort = 8080;
  // After this long with no request the container stops, and nothing runs until the next request or cron.
  sleepAfter = "10m";
  // The server connects out to Postgres on Neon over JDBC (port 5432), which needs the internet. Set
  // explicitly: Cloudflare's docs don't state the default.
  enableInternet = true;

  constructor(ctx: DurableObject["ctx"], env: Env) {
    super(ctx, env);
    this.envVars = {
      DATABASE_URL: env.DATABASE_URL,
      DATABASE_USER: env.DATABASE_USER,
      DATABASE_PASSWORD: env.DATABASE_PASSWORD,
      PACKS_DIR,
      CLIENT_IP_HEADER,
    };
  }

  // As Container.fetch, which calls containerFetch, but a sleeping container gets START_TIMEOUT_MS to start
  // instead of the library's default. Once it is healthy, containerFetch forwards without waiting.
  override async fetch(request: Request): Promise<Response> {
    const state = await this.getState();
    if (!this.ctx.container?.running || state.status !== "healthy") {
      try {
        await this.startAndWaitForPorts({
          cancellationOptions: { abort: request.signal, portReadyTimeoutMS: START_TIMEOUT_MS },
        });
      } catch {
        // containerFetch tries once more and answers with the library's own 429, 500 or 503.
      }
    }
    return this.containerFetch(request);
  }
}

function server(env: Env) {
  return getContainer(env.SERVER, INSTANCE);
}

// The request with CLIENT_IP_HEADER set to the caller's address, or removed when Cloudflare gave none, so a
// value the caller sent never reaches the server.
function withClientAddress(request: Request): Request {
  const headers = new Headers(request.headers);
  const address = request.headers.get("CF-Connecting-IP");
  if (address) {
    headers.set(CLIENT_IP_HEADER, address);
  } else {
    headers.delete(CLIENT_IP_HEADER);
  }
  return new Request(request, { headers });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    return server(env).fetch(withClientAddress(request));
  },

  // Wakes the server so a league's dates pass on time when no one is using the app.
  //
  // Inside the JVM, SeasonStartJob settles every league that is due once a minute, and once at start; a
  // read of a league settles it too (Leagues.kt). Both need the container running, and a sleeping
  // container runs nothing. Settling fixes a league's field (Saturday 00:00, two days before the start),
  // starts its season and each round (Monday 00:00), and decides a round (Monday 12:00, 12 hours after
  // its Sunday ends): each in the league's own time zone, so at any hour in UTC, and some zones are on the
  // half or quarter hour. So the cron in wrangler.jsonc runs every hour, on the hour: a date is settled
  // within the server's start-up time of it in a whole-hour zone, 30 or 45 minutes late in a zone on the
  // half or quarter hour, or sooner by a read. Each wake keeps the container up for `sleepAfter`.
  //
  // The request starts the container if it is asleep and waits for the server to listen; the server runs
  // SeasonStartJob as soon as it has started.
  async scheduled(_controller: ScheduledController, env: Env): Promise<void> {
    const response = await server(env).fetch(new Request("http://server/v1/health"));
    console.log(`Wake: health check answered ${response.status}`);
  },
} satisfies ExportedHandler<Env>;
