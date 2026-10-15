-- btrim strips spaces only, so a tab-only name passes; CreateSeasonViolation.BlankName trims the same set to agree.
-- The ADD fails while any stored name is blank: fix the row, not this file.
ALTER TABLE seasons ADD CONSTRAINT seasons_name_not_blank CHECK (btrim(name) <> '');
