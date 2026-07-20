-- The couple's seat may hold two artifacts per session, so the
-- one-per-member UNIQUE constraint has to go. SQLite can't drop a
-- constraint in place: rebuild the table without it, keeping ids.
CREATE TABLE artifacts_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id INTEGER NOT NULL REFERENCES sessions(id),
  member TEXT NOT NULL REFERENCES members(name),
  title TEXT NOT NULL,
  type TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
INSERT INTO artifacts_new (id, session_id, member, title, type, created_at)
  SELECT id, session_id, member, title, type, created_at FROM artifacts;
DROP TABLE artifacts;
ALTER TABLE artifacts_new RENAME TO artifacts;
