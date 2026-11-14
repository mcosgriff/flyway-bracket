-- Results (#37): each league's decided matchups. The rules are MatchupRule and SeasonCalendar in :domain;
-- the checks here only keep a row from holding what those rules never write.

-- A decided matchup, as it was decided: its two birds, each bird's tally and the winner. A round is
-- decided once, 12 hours after its Sunday ends in the league's time zone, from the sightings stored then;
-- a sighting that arrives later, a member who leaves later and a home county changed later do not change
-- it. The tallies are counts only: no row here names a player, a county or a sighting.
--
-- The standings are not stored. They are counted from these rows and the picks, which cannot change once
-- they lock, so a table stored beside them could only fall out of step.
CREATE TABLE league_results (
    league_id UUID NOT NULL REFERENCES leagues (id) ON DELETE CASCADE,
    -- MatchupSlot in :api: 0 to 62, by round, then from the top of the bracket.
    slot SMALLINT NOT NULL CHECK (slot BETWEEN 0 AND 62),
    upper_bird TEXT NOT NULL CHECK (char_length(upper_bird) BETWEEN 1 AND 200),
    lower_bird TEXT NOT NULL CHECK (char_length(lower_bird) BETWEEN 1 AND 200),
    upper_eligible INTEGER NOT NULL CHECK (upper_eligible >= 0),
    upper_logged INTEGER NOT NULL CHECK (upper_logged BETWEEN 0 AND upper_eligible),
    lower_eligible INTEGER NOT NULL CHECK (lower_eligible >= 0),
    lower_logged INTEGER NOT NULL CHECK (lower_logged BETWEEN 0 AND lower_eligible),
    winner TEXT NOT NULL CHECK (winner IN (upper_bird, lower_bird)),
    -- Decision.Reason in :domain.
    reason TEXT NOT NULL CHECK (reason IN ('HIGHER_SHARE', 'TIED_BETTER_SEED', 'TOO_FEW_ELIGIBLE')),
    PRIMARY KEY (league_id, slot)
);

-- Each member's home county as it was when a round began, so that a change counts only for rounds that
-- have not started. Taken by the first settle of the league once the round has begun, and deleted when
-- the round is decided: a league holds at most the round being played and the one waiting to be decided.
-- A member taken out of the league, or deleted, loses these rows with their place (ON DELETE CASCADE).
CREATE TABLE league_round_homes (
    league_id UUID NOT NULL,
    player_id UUID NOT NULL,
    -- Round in :domain.
    round TEXT NOT NULL CHECK (round IN ('ROUND_OF_64', 'ROUND_OF_32', 'ROUND_OF_16', 'QUARTERFINAL', 'SEMIFINAL', 'FINAL')),
    -- Null when the member had none.
    home_county TEXT,
    PRIMARY KEY (league_id, round, player_id),
    FOREIGN KEY (league_id, player_id) REFERENCES league_members (league_id, player_id) ON DELETE CASCADE
);
