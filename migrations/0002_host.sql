-- Each session can have a host: the first member to offer. NULL means the
-- default place (Nina's house).
ALTER TABLE sessions ADD COLUMN host TEXT;
