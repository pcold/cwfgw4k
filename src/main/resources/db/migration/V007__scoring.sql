CREATE TABLE fantasy_scores (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id     UUID NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    team_id       UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    tournament_id UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    golfer_id     UUID NOT NULL REFERENCES golfers(id),
    points        NUMERIC(12,4) NOT NULL DEFAULT 0,
    position      INTEGER NOT NULL DEFAULT 0,
    num_tied      INTEGER NOT NULL DEFAULT 1,
    base_payout   NUMERIC(10,4) NOT NULL DEFAULT 0,
    ownership_pct NUMERIC(10,4) NOT NULL DEFAULT 100,
    payout        NUMERIC(10,4) NOT NULL DEFAULT 0,
    multiplier    NUMERIC(4,2)  NOT NULL DEFAULT 1,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (season_id, team_id, tournament_id, golfer_id)
);

CREATE INDEX idx_scores_season_tournament ON fantasy_scores(season_id, tournament_id);
CREATE INDEX idx_scores_team ON fantasy_scores(team_id);

CREATE TABLE season_standings (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id          UUID NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    team_id            UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    total_points       NUMERIC(14,4) NOT NULL DEFAULT 0,
    tournaments_played INT NOT NULL DEFAULT 0,
    last_updated       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (season_id, team_id)
);
