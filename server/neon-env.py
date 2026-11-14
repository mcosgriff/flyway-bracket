#!/usr/bin/env python3
# /// script
# requires-python = ">=3.10"
# dependencies = []
# ///
"""Turn the .env.local that `neon init` and `neon checkout` write into server/.env for the Ktor server.

Neon writes libpq URLs (postgresql://user:password@host/db?sslmode=require). The server wants a JDBC URL
plus a separate user and password (docs/server/running.md). This reads that file, picks the direct
(non-pooled) URL, and writes server/.env with mode 600. It prints variable names only, never a value.

Run from the repo root:  uv run server/neon-env.py .env.local
"""
import os
import sys
from urllib.parse import parse_qsl, unquote, urlencode, urlsplit

USAGE = "uv run server/neon-env.py .env.local"

source = sys.argv[1] if len(sys.argv) > 1 else ".env"
target = os.path.join("server", ".env")

values = {}
with open(source, encoding="utf-8") as f:
    for line in f:
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        values[key.strip().removeprefix("export ").strip()] = value.strip().strip("'\"")

urls = {k: v for k, v in values.items() if v.startswith(("postgres://", "postgresql://"))}
if not urls:
    sys.exit(
        f"No postgres:// URL in {source}. Variables found: {', '.join(values) or 'none'}. "
        f"Usage: {USAGE}"
    )


def is_pooled(name, url):
    host = urlsplit(url).hostname or ""
    return "-pooler" in host or ("POOL" in name.upper() and "UNPOOLED" not in name.upper())


direct = {k: v for k, v in urls.items() if not is_pooled(k, v)}
if not direct:
    sys.exit(f"Only pooled URLs in {source} ({', '.join(urls)}). The server needs the direct one.")
preferred = [k for k in direct if "UNPOOLED" in k.upper() or "DIRECT" in k.upper()]
name = (preferred or sorted(direct))[0]
url = urlsplit(direct[name])

if not (url.username and url.password and url.hostname):
    sys.exit(f"{name} has no user, password or host.")
query = dict(parse_qsl(url.query))
query.setdefault("sslmode", "require")
query.pop("channel_binding", None)  # libpq-only; pgjdbc negotiates it itself
port = f":{url.port}" if url.port else ""
jdbc = f"jdbc:postgresql://{url.hostname}{port}{url.path}?{urlencode(query)}"

lines = [
    f"# Written by `{USAGE}` from {name}. Never commit (git ignores .env).",
    f"DATABASE_URL={jdbc}",
    f"DATABASE_USER={unquote(url.username)}",
    f"DATABASE_PASSWORD={unquote(url.password)}",
    "PORT=8080",
    "PACKS_DIR=../packs-local/out",
]
if os.path.exists(target):
    sys.exit(f"{target} already exists; move it aside or delete it first, then run {USAGE} again.")
fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")
print(f"Wrote {target} from {name} (direct connection), mode 600.")
