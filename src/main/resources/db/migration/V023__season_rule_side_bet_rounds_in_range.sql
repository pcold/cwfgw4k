-- Must agree with DRAFT_ROUNDS: no team has a pick outside it to race. The ADD fails while any stored row violates
-- it: fix the row, not this file.
ALTER TABLE season_rule_side_bet_rounds ADD CONSTRAINT season_rule_side_bet_rounds_round_in_range
    CHECK (round BETWEEN 1 AND 8);
