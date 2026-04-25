-- Team events (e.g. Zurich Classic) pair two golfers on one PGA team with a shared score and position.
-- The is_team_event flag lets reporting halve the per-position payout so the two partners split rather
-- than each collect the full payout. Backfills false; ESPN import flips it true on first scoreboard pass.
ALTER TABLE tournaments
    ADD COLUMN is_team_event BOOLEAN NOT NULL DEFAULT false;

-- Preserve partner identity for team-event results. Both partners share the same pair_key (derived from
-- ESPN's team competitor id, e.g. "team:<espnTeamId>") so the leaderboard can regroup them into one
-- visual row after the live overlay is gone.
ALTER TABLE tournament_results
    ADD COLUMN pair_key TEXT;
