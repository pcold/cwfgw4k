-- Nothing writes these columns since the routes that set them were removed (#101).
-- Dropping dropped_at drops both partial roster indexes: golfer_id gets a plain one back, while
-- team_id stays covered as the leading column of the (team_id, golfer_id, acquired_at) unique key.
ALTER TABLE golfers DROP COLUMN active;
ALTER TABLE seasons DROP COLUMN status;
ALTER TABLE team_rosters DROP COLUMN dropped_at;

CREATE INDEX idx_roster_golfer ON team_rosters(golfer_id);
