-- A player is an anonymous account (ADR 0001): an id and the hash of a secret, never the secret itself.
-- The display name and the home county are empty until the player sets them.
CREATE TABLE players (
    id UUID PRIMARY KEY,
    display_name TEXT,
    home_county TEXT,
    secret_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
