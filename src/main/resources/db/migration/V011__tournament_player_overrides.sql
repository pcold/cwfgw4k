-- Manual ESPN→golfer link overrides scoped to a single tournament. Lets an admin disambiguate
-- last-name-only ESPN partner rows (e.g. two Fitzpatricks rostered in a Zurich Classic) by pinning
-- a specific ESPN competitor to a specific rostered golfer. Per-tournament so a wrong link doesn't
-- silently misroute future tournaments. Read-on-import: the override map is consulted by both the
-- durable import path and the live preview matcher; admins re-run import / re-score to re-materialize
-- persisted scores. Links are locked once tournament.status = 'completed' (enforced in the service).
CREATE TABLE tournament_player_overrides (
    tournament_id       UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    espn_competitor_id  TEXT NOT NULL,
    golfer_id           UUID NOT NULL REFERENCES golfers(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tournament_id, espn_competitor_id)
);

CREATE INDEX idx_tournament_player_overrides_golfer ON tournament_player_overrides(golfer_id);
