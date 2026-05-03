-- Generic key/value cache for serialized API GET responses. Keyed by the
-- full request URI (path + query string), valued by the JSON response body.
-- UNLOGGED because the cache is purely an optimization — losing it on crash
-- is fine, and skipping WAL keeps writes cheap. Pure-TTL semantics: readers
-- filter `expires_at > now()` and a periodic sweep deletes the rest.
CREATE UNLOGGED TABLE cache (
    key TEXT PRIMARY KEY,
    value JSONB NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_cache_expires ON cache(expires_at);
