-- Run once, by an operator, against production after V024 has migrated; never from a migration, since it picks
-- rows by production's data:
--   psql --set ON_ERROR_STOP=1 -f scripts/one-off/tombstone-duplicate-and-phantom-golfers.sql
-- Each step raises unless it touched exactly the rows it expects, which rolls the whole run back, and a second
-- run finds nothing left and refuses. It deletes nothing: the 8 unreferenced blank-name rows went on 2026-07-25.
--
-- Left out on purpose: Byeong Hun An is a real golfer missing his ESPN id, not a duplicate, so he is never
-- tombstoned. When his ESPN id is confirmed, backfill it in its own one-off script.

BEGIN;

DO $$
DECLARE
    touched INT;
    survivor UUID;
BEGIN
    -- The surname-only phantoms an unmatched team partner created. They hold results, so they are kept.
    UPDATE cwfgw4k.golfers
       SET tombstoned_at = now()
     WHERE pga_player_id IS NULL
       AND btrim(first_name) = ''
       AND tombstoned_at IS NULL;
    GET DIAGNOSTICS touched = ROW_COUNT;
    IF touched <> 17 THEN
        RAISE EXCEPTION 'expected 17 id-less blank-first-name golfers, tombstoned %', touched;
    END IF;

    -- STRICT raises unless exactly one live row holds the id under that surname.
    SELECT id INTO STRICT survivor
      FROM cwfgw4k.golfers
     WHERE pga_player_id = '4837' AND last_name = 'Detry' AND tombstoned_at IS NULL;
    UPDATE cwfgw4k.golfers
       SET tombstoned_at = now(), merged_into = survivor
     WHERE pga_player_id IS NULL AND first_name = 'Thomas' AND last_name = 'Detry' AND tombstoned_at IS NULL;
    GET DIAGNOSTICS touched = ROW_COUNT;
    IF touched <> 1 THEN
        RAISE EXCEPTION 'expected 1 id-less Thomas Detry, tombstoned %', touched;
    END IF;

    SELECT id INTO STRICT survivor
      FROM cwfgw4k.golfers
     WHERE pga_player_id = '4364865' AND last_name = 'Fitzpatrick' AND tombstoned_at IS NULL;
    UPDATE cwfgw4k.golfers
       SET tombstoned_at = now(), merged_into = survivor
     WHERE pga_player_id IS NULL AND first_name = 'Alex' AND last_name = 'Fitzpatrick' AND tombstoned_at IS NULL;
    GET DIAGNOSTICS touched = ROW_COUNT;
    IF touched <> 1 THEN
        RAISE EXCEPTION 'expected 1 id-less Alex Fitzpatrick, tombstoned %', touched;
    END IF;

    -- Jordan Shipley is a football player: the row is a roster typo for Neal Shipley.
    SELECT id INTO STRICT survivor
      FROM cwfgw4k.golfers
     WHERE pga_player_id = '5209442' AND last_name = 'Shipley' AND tombstoned_at IS NULL;
    UPDATE cwfgw4k.golfers
       SET tombstoned_at = now(), merged_into = survivor
     WHERE pga_player_id IS NULL AND first_name = 'Jordan' AND last_name = 'Shipley' AND tombstoned_at IS NULL;
    GET DIAGNOSTICS touched = ROW_COUNT;
    IF touched <> 1 THEN
        RAISE EXCEPTION 'expected 1 id-less Jordan Shipley, tombstoned %', touched;
    END IF;
END $$;

SELECT id, first_name, last_name, merged_into
  FROM cwfgw4k.golfers
 WHERE tombstoned_at IS NOT NULL
 ORDER BY last_name, first_name;

COMMIT;
