-- No link write may name a tombstoned golfer, though an existing link survives its tombstone. A CHECK cannot read
-- golfers, so a trigger backs the app's refusal. FOR SHARE waits out a concurrent tombstone; FK's KEY SHARE would not.
CREATE FUNCTION refuse_override_to_tombstoned_golfer() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path FROM CURRENT
AS $$
DECLARE
    tombstoned TIMESTAMPTZ;
BEGIN
    -- Not filtered on tombstoned_at: the snapshot reads an uncommitted tombstone as NULL, so the row would never be locked.
    SELECT tombstoned_at INTO tombstoned FROM golfers WHERE id = NEW.golfer_id FOR SHARE;
    IF tombstoned IS NOT NULL THEN
        RAISE EXCEPTION 'golfer % is tombstoned and cannot be linked', NEW.golfer_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tournament_player_overrides_golfer_not_tombstoned
    BEFORE INSERT OR UPDATE OF golfer_id ON tournament_player_overrides
    FOR EACH ROW EXECUTE FUNCTION refuse_override_to_tombstoned_golfer();
