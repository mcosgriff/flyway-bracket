-- Sightings (#36): what a player logged, reduced to what the game needs. ADR 0001 allows a bird's name, a
-- county and a date, and nothing else: no coordinates, no time of day, no place name, no count, and not
-- even when the row was written, which could stand in for the time of day.
--
-- A sighting belongs to the player, not to a league: it counts in every league they are in. Deleting the
-- player deletes their sightings (ON DELETE CASCADE).
CREATE TABLE sightings (
    player_id UUID NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    -- The id the app made. One sighting per id for each player, so an upload sent again adds nothing; two
    -- players' ids never meet.
    client_id UUID NOT NULL,
    common_name TEXT NOT NULL CHECK (char_length(common_name) BETWEEN 1 AND 200),
    county_code TEXT NOT NULL CHECK (county_code ~ '^[A-Z]{2}-[A-Z]{2}-[0-9]{3}$'),
    -- The day where the player was.
    sighted_on DATE NOT NULL,
    PRIMARY KEY (player_id, client_id)
);
