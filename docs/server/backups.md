# Backing up the database

The hosted server's database is the Neon project's `production` branch ([hosting.md](hosting.md)). Neon's
free plan can restore it to any moment in a short window only: hours, not weeks. So every night a GitHub
Actions job dumps the database, encrypts the dump, and stores it in a Cloudflare R2 bucket that deletes
each dump after 35 days (issue #16). This page says how that works, what the owner sets up once, and how
to restore. Like hosting.md, it holds no secret, and no bucket name, account ID or URL: every `<…>` is a
placeholder.

## What runs

`.github/workflows/backup-database.yml` runs every day at 10:17 UTC, and by hand. In the `backup`
environment it:

1. Checks that the repository variables `BACKUP_AGE_RECIPIENT` and `BACKUP_BUCKET` are set.
2. Installs `age` from Ubuntu's archive.
3. Runs `pg_dump` from the official `postgres:18-alpine` image against `BACKUP_DATABASE_URL`, in the
   custom format (`-Fc`), with `--no-owner --no-privileges`. `pg_dump` must be the server's major
   version or newer, and Neon runs Postgres 18. The URL reaches the container as libpq's `PG*` variables,
   never on a command line.
4. Checks that `pg_restore --list` can read the dump.
5. Encrypts it with `age` to the public key in `BACKUP_AGE_RECIPIENT`, and deletes the unencrypted file.
6. Uploads it to R2 as `postgres/YYYY/MM/DD/flyway-bracket-<UTC time>.dump.age`, checks the size R2
   holds, and prints only the key and the size.
7. Deletes everything it wrote, even when a step failed.

The dump holds personal data: display names, home counties, sightings, leagues and picks, and the hashes
of the account secrets. It is encrypted before it leaves the runner, and only the private key, kept
offline by the owner, can read it. The unencrypted dump is never uploaded as an artifact.

R2 is part of Cloudflare, and the dump is of the database Neon stores, so backups add no company to the
ones the privacy texts name ([ADR 0001](../adr/0001-server-and-network.md#where-it-runs)).

## One-time setup by the owner

These steps need the owner's Cloudflare, Neon and GitHub accounts, and `age` on the owner's computer.
Run the `npx wrangler` commands in `worker/`, after `npm ci`, signed in with `npx wrangler login` (the
deploy job's API token has no R2 permission, and needs none). Run the `neon` commands from the repo root
of a checkout linked to the Neon project ([running.md](running.md#a-neon-database-development-branch)).

### 1. The bucket

Choose a bucket name. It goes only in the repository variable `BACKUP_BUCKET` (step 7).

```sh
cd worker
npx wrangler r2 bucket create <bucket-name> --location wnam
```

`wnam` (western North America) asks R2 to keep the bucket near the database, which is in Oregon. It is a
hint, honoured only when the bucket is first created. If wrangler offers to add the bucket to
`wrangler.jsonc`, say no: the Worker never reads the backups.

### 2. Delete dumps after 35 days

```sh
npx wrangler r2 bucket lifecycle add <bucket-name> delete-after-35-days postgres/ --expire-days 35
npx wrangler r2 bucket lifecycle list <bucket-name>
```

35 days keeps five weeks of nightly dumps. R2 deletes an expired object some time after it expires, not at
the exact moment.

### 3. An R2 token for the backup job

In the Cloudflare dashboard, R2 Object Storage, Manage API tokens, create an **Account API token** with:

- Permissions: **Object Read & Write**
- Buckets: **Apply to specific buckets only**, the backup bucket alone

The page then shows an **Access Key ID** and a **Secret Access Key**, once. Keep the page open for step 7;
store them nowhere else.

The token can read the encrypted dumps and write new ones, nothing more. It cannot read the database, and
the dumps are no use without the private key.

### 4. A read-only database role

The job connects as a role that can read every table and change nothing. Create it on the `production`
branch, as the branch's owner role. First make a password: 48 hex characters, so it needs no escaping in
a URL.

```sh
openssl rand -hex 24
psql "$(neon connection-string production)"
```

Then, in `psql`:

```sql
CREATE ROLE flyway_backup LOGIN;
GRANT pg_read_all_data TO flyway_backup;
\password flyway_backup
```

`\password` asks for the password twice; paste the one `openssl` printed. It is sent hashed, so it is not
in `psql`'s history. The password goes into the secret `BACKUP_DATABASE_URL` (step 7) and nowhere else;
clear the terminal afterwards.

The role is made in SQL, not with `neon roles create`: a role made by Neon's CLI or console is a member of
`neon_superuser`, which can change the database.

If Neon refuses the `GRANT`, grant reading by hand instead. `<server-role>` is the role in the server's
`DATABASE_USER`, which creates the tables:

```sql
GRANT USAGE ON SCHEMA public TO flyway_backup;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO flyway_backup;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO flyway_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE <server-role> IN SCHEMA public GRANT SELECT ON TABLES TO flyway_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE <server-role> IN SCHEMA public GRANT SELECT ON SEQUENCES TO flyway_backup;
```

The secret is a libpq URL to the **direct** host (no `-pooler` in its name: `pg_dump` needs a session of
its own). Take the host and the database name from `neon connection-string production`:

```text
postgresql://flyway_backup:<password>@<direct-host>/<database>?sslmode=require&channel_binding=require
```

The job refuses a URL without `sslmode=require` (or `verify-ca`, `verify-full`), or with a parameter
other than `sslmode` and `channel_binding`.

### 5. The age key

```sh
age-keygen -o ~/keys/flyway-bracket-backup.key
chmod 600 ~/keys/flyway-bracket-backup.key
```

The file holds the private key. It stays offline in `~/keys`, backed up with the release keystore, and
never goes to GitHub, Cloudflare or Neon. Without it no dump can be read: losing it loses every backup.
Only the public key, the line beginning `age1`, goes into the repository variable (step 7).
`age-keygen -y ~/keys/flyway-bracket-backup.key` prints it again.

### 6. The `backup` environment

The job runs in its own environment, `backup`, that only `main` may use. It has **no** required reviewer:
a scheduled run cannot wait for approval. Its secrets are separate from `production`'s, so the deploy job
never sees the database URL, and this job never sees the deploy token.

```sh
gh api --method PUT repos/mcosgriff/flyway-bracket/environments/backup \
  -F 'deployment_branch_policy[protected_branches]=false' \
  -F 'deployment_branch_policy[custom_branch_policies]=true'
gh api --method POST repos/mcosgriff/flyway-bracket/environments/backup/deployment-branch-policies \
  -f name=main -f type=branch
gh api repos/mcosgriff/flyway-bracket/environments/backup \
  --jq '{protection_rules, deployment_branch_policy}'
```

The last command shows no `required_reviewers` rule and `custom_branch_policies: true`.

### 7. Secrets and variables

Each `gh secret set` asks for the value, so it stays out of the shell's history. The account ID is the
same one `production` holds; the environment needs its own copy.

```sh
gh secret set BACKUP_DATABASE_URL --env backup
gh secret set R2_ACCESS_KEY_ID --env backup
gh secret set R2_SECRET_ACCESS_KEY --env backup
gh secret set CLOUDFLARE_ACCOUNT_ID --env backup
gh variable set BACKUP_AGE_RECIPIENT --body "$(age-keygen -y ~/keys/flyway-bracket-backup.key)"
gh variable set BACKUP_BUCKET --body '<bucket-name>'
```

The two variables are repository variables, not secrets, and the job never prints them. Neither needs
hiding: the public key can only encrypt, and the bucket cannot be reached without the token.

### 8. The first run

Run one by hand (below), once the workflow is on `main`. The log ends with
`Uploaded postgres/…/flyway-bracket-….dump.age (<n> bytes)`. Then do the restore test (below) once, to
know the whole chain works.

## Run a backup now

```sh
gh workflow run backup-database.yml
gh run list --workflow backup-database.yml --limit 1
```

Or Actions, backup-database, Run workflow. Only `main` can run it: the `backup` environment refuses a run
from another branch.

## Restore

A restore goes into a **new** Neon branch, never over `production`. Replacing the live database is a
separate decision, taken after the restored copy has been checked.

Work in a directory outside the repository, readable only by you:

```sh
mkdir -m 700 ~/flyway-restore
```

1. Find the dump's key. Each run's log prints it:

   ```sh
   gh run list --workflow backup-database.yml --limit 5
   gh run view <run-id> --log | grep Uploaded
   ```

   The bucket's page in the Cloudflare dashboard lists every object too.

2. Download it, with wrangler (signed in as the owner, in `worker/`):

   ```sh
   npx wrangler r2 object get '<bucket-name>/<key>' --remote --file ~/flyway-restore/restore.dump.age
   ```

   or with the `aws` CLI and the R2 token of step 3:

   ```sh
   AWS_ACCESS_KEY_ID=<access-key-id> AWS_SECRET_ACCESS_KEY=<secret-access-key> AWS_DEFAULT_REGION=auto \
     aws s3 cp 's3://<bucket-name>/<key>' ~/flyway-restore/restore.dump.age \
     --endpoint-url https://<account-id>.r2.cloudflarestorage.com
   ```

   A shell's history keeps that command with the token in it; prefer wrangler.

3. Decrypt it:

   ```sh
   age --decrypt --identity ~/keys/flyway-bracket-backup.key \
     --output ~/flyway-restore/restore.dump ~/flyway-restore/restore.dump.age
   ```

4. Make a branch, and an empty database on it. The branch starts as a copy of `production`, so restore
   into a new database, not the branch's own:

   ```sh
   neon branches create --name restore-test
   neon databases create --name restore_check --branch restore-test
   ```

5. Restore with `pg_restore` 18, from the same image the job uses. The URL is the branch's direct one:

   ```sh
   export RESTORE_URL="$(neon connection-string restore-test --database-name restore_check)"
   docker run --rm -i -e RESTORE_URL postgres:18-alpine \
     sh -c 'exec pg_restore --no-owner --no-privileges --exit-on-error --dbname "$RESTORE_URL"' \
     < ~/flyway-restore/restore.dump
   ```

6. Check it (any `psql`):

   ```sh
   psql "$RESTORE_URL" \
     -c 'SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1' \
     -c 'SELECT count(*) FROM players' -c 'SELECT count(*) FROM leagues' -c 'SELECT count(*) FROM sightings'
   ```

   The migration is the newest in `server/src/main/resources/db/migration` as of the dump, and the counts
   are close to `production`'s, less what was added since the dump.

7. Clean up:

   ```sh
   neon branches delete restore-test
   rm -r ~/flyway-restore
   unset RESTORE_URL
   ```

**To recover the live database**, first decide whether Neon's own restore is enough: in the Neon console,
the `production` branch can be restored to a moment within its restore window, which is newer than any
dump. If the damage is older than that window, restore the newest good dump into a new branch as above,
check it, then point the server at it: set the Worker secrets to that branch's credentials
([hosting.md](hosting.md#4-the-database-secrets)) and run the deploy again. Never restore over
`production` without deciding to.

## A monthly restore test

A backup no one has restored is a guess. Once a month, say on the first Monday, run steps 1 to 7 above
with the newest dump. It takes about ten minutes and proves the key, the bucket, the token and the dump
all still work. If a step fails, fix it before the next night's dump is needed.

## Limits

- A dump is only as new as the last run, at 10:17 UTC. Neon's own restore window covers the hours since;
  between the end of that window and the next run, the newest dump is all there is.
- GitHub may start a scheduled run late, or skip it when Actions is busy.
- GitHub turns off scheduled workflows in a public repository after 60 days with no activity in it. Turn
  it back on in Actions, backup-database.
- When a scheduled run fails, GitHub emails the person who last changed the workflow's `cron` line. That
  email is the only alert.
- An account deleted in the app stays in the dumps taken before, for up to 35 days, encrypted. A restore
  brings back every account deleted since that dump.
- Losing the age private key, and its backup, makes every dump unreadable.
