-- Make the four "structure-bearing" FKs cascade on delete so DELETE FROM seasons
-- WHERE id = ? wipes the entire season subtree in one statement. Without this,
-- seasons.id can't be deleted while teams or tournaments still reference it,
-- and team-bound rosters / draft picks would dangle even if it could.
--
--   seasons → teams (this migration)        → team_rosters (this migration)
--                                           → draft_picks (this migration)
--   seasons → tournaments (this migration)  → tournament_results (already cascades)
--
-- Other season-bound tables (drafts, fantasy_scores, season_standings,
-- season_payouts, season_side_bets) already cascade from their original
-- migrations, so this finishes the chain.

ALTER TABLE teams
    DROP CONSTRAINT teams_season_id_fkey,
    ADD CONSTRAINT teams_season_id_fkey
        FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE;

ALTER TABLE tournaments
    DROP CONSTRAINT tournaments_season_id_fkey,
    ADD CONSTRAINT tournaments_season_id_fkey
        FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE;

ALTER TABLE team_rosters
    DROP CONSTRAINT team_rosters_team_id_fkey,
    ADD CONSTRAINT team_rosters_team_id_fkey
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE;

ALTER TABLE draft_picks
    DROP CONSTRAINT draft_picks_team_id_fkey,
    ADD CONSTRAINT draft_picks_team_id_fkey
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE;
