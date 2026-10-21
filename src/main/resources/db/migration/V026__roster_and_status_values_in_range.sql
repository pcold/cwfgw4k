-- draft_round must agree with DRAFT_ROUNDS and ownership_pct with OWNERSHIP_PCTS. A NULL draft_round, the undrafted
-- entry, stays legal and never collides on the unique key. Each ADD fails while any stored row violates it: fix the
-- row, not this file.
ALTER TABLE team_rosters
    ADD CONSTRAINT team_rosters_draft_round_in_range CHECK (draft_round BETWEEN 1 AND 8),
    ADD CONSTRAINT team_rosters_ownership_pct_in_range CHECK (ownership_pct > 0 AND ownership_pct <= 100),
    ADD CONSTRAINT team_rosters_one_pick_per_round UNIQUE (team_id, draft_round);

-- Exactly the values of TournamentStatus and UserRole: a variant added there needs a migration widening its list.
ALTER TABLE tournaments
    ADD CONSTRAINT tournaments_status_known CHECK (status IN ('upcoming', 'in_progress', 'completed'));

ALTER TABLE users
    ADD CONSTRAINT users_role_known CHECK (role IN ('admin', 'user'));
