CREATE TABLE teams (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id   UUID NOT NULL REFERENCES seasons(id),
    owner_name  TEXT NOT NULL,
    team_name   TEXT NOT NULL,
    team_number INT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (season_id, team_name)
);

CREATE INDEX idx_teams_season ON teams(season_id);

CREATE TABLE team_rosters (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id       UUID NOT NULL REFERENCES teams(id),
    golfer_id     UUID NOT NULL REFERENCES golfers(id),
    acquired_via  TEXT NOT NULL DEFAULT 'draft',
    draft_round   INT,
    ownership_pct NUMERIC(5,2) NOT NULL DEFAULT 100.00,
    acquired_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    dropped_at    TIMESTAMPTZ,
    is_active     BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (team_id, golfer_id, acquired_at)
);

CREATE INDEX idx_roster_team_active ON team_rosters(team_id) WHERE dropped_at IS NULL;
CREATE INDEX idx_roster_golfer_active ON team_rosters(golfer_id) WHERE dropped_at IS NULL;
