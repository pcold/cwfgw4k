CREATE TABLE seasons (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    league_id       UUID NOT NULL REFERENCES leagues(id),
    name            TEXT NOT NULL,
    season_year     INT NOT NULL,
    season_number   INT NOT NULL DEFAULT 1,
    status          TEXT NOT NULL DEFAULT 'draft',
    tie_floor       NUMERIC(10,4) NOT NULL DEFAULT 1,
    side_bet_amount NUMERIC(10,4) NOT NULL DEFAULT 15,
    max_teams       INT NOT NULL DEFAULT 10,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_seasons_year ON seasons(season_year);

CREATE TABLE season_rule_payouts (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id UUID NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    amount    NUMERIC(10,4) NOT NULL,
    UNIQUE (season_id, position)
);

CREATE TABLE season_rule_side_bet_rounds (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_id UUID NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
    round     INTEGER NOT NULL,
    UNIQUE (season_id, round)
);
