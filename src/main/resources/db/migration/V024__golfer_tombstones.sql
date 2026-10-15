-- A tombstoned golfer keeps every row that references it but is never a name-match candidate. Only an operator's
-- script sets either column; merged_into names the live row the tombstone duplicates.
ALTER TABLE golfers
    ADD COLUMN tombstoned_at TIMESTAMPTZ,
    ADD COLUMN merged_into UUID REFERENCES golfers (id),
    ADD CONSTRAINT golfers_merged_into_is_tombstoned CHECK (merged_into IS NULL OR tombstoned_at IS NOT NULL),
    ADD CONSTRAINT golfers_merged_into_other_row CHECK (merged_into <> id);
