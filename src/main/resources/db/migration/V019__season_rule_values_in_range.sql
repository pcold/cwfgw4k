-- A negative payout charges its place instead of paying it, and a negative tie_floor floors nothing; zero stays legal
-- for both. A max_teams below 1 admits no team. Each ADD fails while any stored row violates it: fix the row, not
-- this file.
ALTER TABLE seasons ADD CONSTRAINT seasons_tie_floor_non_negative CHECK (tie_floor >= 0);
ALTER TABLE seasons ADD CONSTRAINT seasons_max_teams_positive CHECK (max_teams >= 1);
ALTER TABLE season_rule_payouts ADD CONSTRAINT season_rule_payouts_amount_non_negative CHECK (amount >= 0);
