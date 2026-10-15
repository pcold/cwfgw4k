-- A tombstoned golfer is never a new link target. A CHECK cannot read golfers, so a trigger backs the app's refusal.
-- FOR SHARE waits out a concurrent tombstone, which FK's KEY SHARE would not. An existing link survives a tombstone.
CREATE FUNCTION refuse_override_to_tombstoned_golfer() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path FROM CURRENT
AS $$
DECLARE
    tombstoned TIMESTAMPTZ;
BEGIN
    -- Filtering on tombstoned_at in the WHERE would skip the lock on a row whose tombstone has not committed yet.
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
