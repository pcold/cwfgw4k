-- Unread and unwritten since #128 removed the draft code; the draft itself happens outside the app.
-- The only FK into either table is draft_picks → drafts, so dropping the child first needs no CASCADE.
DROP TABLE draft_picks;
DROP TABLE drafts;
