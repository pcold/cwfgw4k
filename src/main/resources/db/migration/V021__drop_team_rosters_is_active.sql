-- The drop path #101 removed was the only writer of is_active = false, and V014 took dropped_at with it, so the
-- flag is the last record that a row left its team. No read filters on it: dropping it would silently rejoin
-- such a row to its roster for good, and the guard refuses to lose that.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM team_rosters WHERE is_active = false) THEN
        RAISE EXCEPTION 'team_rosters holds inactive entries; delete them before dropping is_active';
    END IF;
END $$;

ALTER TABLE team_rosters DROP COLUMN is_active;
