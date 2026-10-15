-- Until now a season with no side-bet round rows read back as rounds 5-8; from here no rows means no side bets. A
-- season holding none relied on that fallback, so it gets 5-8 as rows and settles exactly as it did before.
INSERT INTO season_rule_side_bet_rounds (season_id, round)
SELECT seasons.id, fallback.round
FROM seasons
CROSS JOIN (VALUES (5), (6), (7), (8)) AS fallback(round)
WHERE NOT EXISTS (
    SELECT 1 FROM season_rule_side_bet_rounds existing WHERE existing.season_id = seasons.id
);
