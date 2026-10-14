-- Roster reads stop filtering on dropped_at, so a row dropped before #101 removed its writer would rejoin
-- its team: the guard refuses to lose that. team_id lookups stay covered as the leading column of the
-- (team_id, golfer_id, acquired_at) unique key; golfer_id gets back a plain index.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM team_rosters WHERE dropped_at IS NOT NULL) THEN
        RAISE EXCEPTION 'team_rosters holds dropped entries; delete them before dropping dropped_at';
    END IF;
END $$;

ALTER TABLE golfers DROP COLUMN active;
ALTER TABLE seasons DROP COLUMN status;
ALTER TABLE team_rosters DROP COLUMN dropped_at;

CREATE INDEX idx_roster_golfer ON team_rosters(golfer_id);
