-- season_number is now always computed server-side (auto-incremented per
-- league + season_year in SeasonRepository.create), so the DB default of 1
-- no longer reflects a meaningful value and only masked callers that forgot
-- to set it.
ALTER TABLE seasons ALTER COLUMN season_number DROP DEFAULT;
