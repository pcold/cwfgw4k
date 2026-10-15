-- No read ever filtered on is_active, and V014's guard already refused every row the app had deactivated, so a
-- false here was set by hand and its row still counts on its roster. The guard refuses to drop that flag unseen.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM team_rosters WHERE is_active = false) THEN
        RAISE EXCEPTION 'team_rosters holds is_active = false rows; set them true or delete them before dropping is_active';
    END IF;
END $$;

ALTER TABLE team_rosters DROP COLUMN is_active;
