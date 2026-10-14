-- Write-only: every report ranks teams from fantasy_scores at read time, never from this table.
-- No FK, view or trigger points at it (its own FKs point out), so a plain DROP needs no CASCADE.
DROP TABLE season_standings;
