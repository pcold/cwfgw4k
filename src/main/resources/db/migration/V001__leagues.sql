CREATE TABLE leagues (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO leagues (id, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'Castlewood Fantasy Golf');
