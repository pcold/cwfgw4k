-- Cached bodies carry snake_case keys the UI no longer reads. Production keys carry the deploy revision and never
-- match them; builds sharing one (`local`) would serve them until expiry, as a read checks only key and expiry.
-- Runs once, unlike a clear at boot: an overlapping rollout's old revision refills its keys a single time.
DELETE FROM cache;
