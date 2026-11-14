-- Seeding (#34): each league's field of 64 birds. The rules are Field in :domain (domain/bracket); the
-- checks here only keep a row from holding what those rules never write.

-- Why a closed league never started: CloseReason in :domain. A league closed before this column existed
-- could only have been closed for want of players.
ALTER TABLE leagues ADD COLUMN closed_reason TEXT CHECK (closed_reason IN ('TOO_FEW_PLAYERS', 'TOO_FEW_BIRDS'));
UPDATE leagues SET closed_reason = 'TOO_FEW_PLAYERS' WHERE phase = 'CLOSED';
ALTER TABLE leagues ADD CONSTRAINT leagues_closed_reason CHECK ((phase = 'CLOSED') = (closed_reason IS NOT NULL));

-- A league's field, for the season starting on season_start. Before the field is fixed (48 hours before the
-- start) it is the last preview shown, seeded from the members of that moment; from then on it is fixed,
-- and once the season runs it is the bracket and never changes. A league has one at a time.
CREATE TABLE league_fields (
    league_id UUID PRIMARY KEY REFERENCES leagues (id) ON DELETE CASCADE,
    season_start DATE NOT NULL CHECK (EXTRACT(ISODOW FROM season_start) = 1),
    fixed BOOLEAN NOT NULL,
    -- How many birds could have made the field. Fewer than 64, and the field has no teams.
    candidates INTEGER NOT NULL CHECK (candidates >= 0)
);

-- The 64 birds of a field: 16 seeds in each of the four quarters (BirdGroup in :domain).
CREATE TABLE league_field_teams (
    league_id UUID NOT NULL REFERENCES league_fields (league_id) ON DELETE CASCADE,
    quarter TEXT NOT NULL CHECK (quarter IN ('SONGBIRDS', 'WATERBIRDS', 'FLIERS', 'WATERFOWL')),
    seed INTEGER NOT NULL CHECK (seed BETWEEN 1 AND 16),
    common_name TEXT NOT NULL CHECK (char_length(common_name) BETWEEN 1 AND 200),
    PRIMARY KEY (league_id, quarter, seed),
    UNIQUE (league_id, common_name)
);
