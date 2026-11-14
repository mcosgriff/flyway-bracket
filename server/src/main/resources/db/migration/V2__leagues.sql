-- Leagues (#32): a group of players who play one bracket together. The game's rules for them are in
-- :domain (domain/league); the checks here only keep a row from holding what those rules never write.
CREATE TABLE leagues (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL CHECK (char_length(name) BETWEEN 3 AND 40),
    open BOOLEAN NOT NULL,
    -- An IANA zone id. Every date of the league is a date in this zone.
    time_zone TEXT NOT NULL,
    invite_code TEXT NOT NULL UNIQUE CHECK (invite_code ~ '^[A-HJKMNP-Z2-9]{8}$'),
    -- LeaguePhase in :domain, as last settled: the server moves a league on when its dates have passed,
    -- before any change to its players.
    phase TEXT NOT NULL CHECK (phase IN ('FORMING', 'RUNNING', 'FINISHED', 'CLOSED')),
    -- The first Monday, after any postponements.
    season_start DATE NOT NULL CHECK (EXTRACT(ISODOW FROM season_start) = 1),
    postponements INTEGER NOT NULL DEFAULT 0 CHECK (postponements BETWEEN 0 AND 4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Auto-join looks for the open forming league with the earliest start.
CREATE INDEX leagues_joinable ON leagues (season_start, created_at) WHERE open AND phase = 'FORMING';

-- A player's place in a league. A player who leaves before the season starts loses the row; one who
-- leaves after keeps it with left_at set, and no longer counts. Deleting the player deletes the row.
CREATE TABLE league_members (
    league_id UUID NOT NULL REFERENCES leagues (id) ON DELETE CASCADE,
    player_id UUID NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at TIMESTAMPTZ,
    PRIMARY KEY (league_id, player_id)
);

CREATE INDEX league_members_player ON league_members (player_id);
