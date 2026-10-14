-- The response cache's generation, advanced by every clear; a put stores only under the generation its fill
-- read before fetching. Logged, unlike `cache`: crash recovery empties an UNLOGGED table, and a missing row
-- fails every put. The CHECK keeps it to the one row the migration seeds.
CREATE TABLE cache_generation (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    generation BIGINT NOT NULL
);

INSERT INTO cache_generation (singleton, generation) VALUES (TRUE, 0);
