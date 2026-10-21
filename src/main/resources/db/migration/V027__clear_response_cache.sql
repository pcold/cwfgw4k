-- Every cached body was encoded with snake_case keys, which the UI no longer reads. Deleted, not left to expire:
-- a read checks only key and expiry, so advancing cache_generation alone would keep serving them.
DELETE FROM cache;
