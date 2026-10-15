-- The drop path #101 removed was the only writer of is_active = false, and V014 took dropped_at, so a row
-- deactivated before then carries its last mark here; dropping the column would silently return it to its
-- team. The guard refuses to lose that.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM team_rosters WHERE is_active = false) THEN
        RAISE EXCEPTION 'team_rosters holds inactive entries; delete them before dropping is_active';
    END IF;
END $$;

ALTER TABLE team_rosters DROP COLUMN is_active;
