-- Each round's holes as TournamentRepository encodes them. A row imported before this column holds '[]' until its
-- tournament is imported again.
ALTER TABLE tournament_results
    ADD COLUMN scorecards JSONB NOT NULL DEFAULT '[]',
    ADD CONSTRAINT tournament_results_scorecards_array CHECK (jsonb_typeof(scorecards) = 'array');
