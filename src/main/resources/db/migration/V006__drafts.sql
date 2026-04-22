CREATE TABLE drafts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id    UUID NOT NULL UNIQUE REFERENCES seasons(id) ON DELETE CASCADE,
    status       TEXT NOT NULL DEFAULT 'pending',
    draft_type   TEXT NOT NULL DEFAULT 'snake',
    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE draft_picks (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    draft_id   UUID NOT NULL REFERENCES drafts(id) ON DELETE CASCADE,
    team_id    UUID NOT NULL REFERENCES teams(id),
    golfer_id  UUID REFERENCES golfers(id),
    round_num  INT NOT NULL,
    pick_num   INT NOT NULL,
    picked_at  TIMESTAMPTZ,
    UNIQUE (draft_id, pick_num)
);

CREATE INDEX idx_picks_draft ON draft_picks(draft_id);
CREATE INDEX idx_picks_team ON draft_picks(team_id);
