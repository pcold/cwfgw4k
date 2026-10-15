-- The character class must match CreateSeasonViolation's NAME_PADDING. The ADD fails while any stored name is blank:
-- fix the row, not this file.
ALTER TABLE seasons ADD CONSTRAINT seasons_name_not_blank CHECK (name ~ '[^ \t\n\r\v\f]');
