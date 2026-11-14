-- Picks (#35): for each matchup a member picked, the bird picked to win it. The rules are BracketPicks in
-- :domain (domain/bracket); the checks here only keep a row from holding what those rules never write.

-- What names a member to the other players, in place of their account id, which no other player ever sees
-- (ADR 0001): GET /v1/leagues/{leagueId}/members/{memberKey}/picks. One per membership, made when the
-- player joins.
ALTER TABLE league_members ADD COLUMN member_key UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE league_members ADD CONSTRAINT league_members_member_key UNIQUE (league_id, member_key);

-- A pick belongs to a membership. A player taken out of a league before its season starts, or deleted,
-- loses their picks with their row (ON DELETE CASCADE); one who leaves after the start keeps their row,
-- marked as left, and their picks with it, which then score nothing.
--
-- A pick is not tied to the field's rows: a new preview replaces those, and the server drops the picks
-- it undoes in the same transaction.
CREATE TABLE league_picks (
    league_id UUID NOT NULL,
    player_id UUID NOT NULL,
    -- MatchupSlot in :api: 0 to 62, by round, then from the top of the bracket.
    slot SMALLINT NOT NULL CHECK (slot BETWEEN 0 AND 62),
    common_name TEXT NOT NULL CHECK (char_length(common_name) BETWEEN 1 AND 200),
    PRIMARY KEY (league_id, player_id, slot),
    FOREIGN KEY (league_id, player_id) REFERENCES league_members (league_id, player_id) ON DELETE CASCADE
);
