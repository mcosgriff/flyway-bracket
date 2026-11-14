-- Deleted accounts (#16): the id of each player who deleted their account, and when. The nightly backups
-- (docs/server/backups.md) keep a deleted account for up to 35 days; if the database is ever restored from
-- one, the operator copies this list from the database as it was before the restore, and the server deletes
-- again every account on it that the restore brought back. ADR 0001 allows the id and the time, and nothing
-- else: no name, no county, nothing the player sent.
--
-- No foreign key: the player's row is gone. The server forgets an id 35 days after the deletion
-- (Accounts.DELETED_IDS_KEEP_DAYS), when no backup holds the account any longer.
CREATE TABLE deleted_players (
    player_id UUID PRIMARY KEY,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
