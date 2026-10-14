-- A negative side_bet_amount counts as staked and settles inverted, the losers collecting; zero is the season
-- with no side bets and stays legal. The ADD fails while any stored row is negative: fix the row, not this file.
ALTER TABLE seasons ADD CONSTRAINT seasons_side_bet_amount_non_negative CHECK (side_bet_amount >= 0);
