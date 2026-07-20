CREATE TABLE members (
  name TEXT PRIMARY KEY,
  token TEXT NOT NULL UNIQUE
);

CREATE TABLE sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  number INTEGER NOT NULL UNIQUE,
  status TEXT NOT NULL DEFAULT 'collecting', -- collecting | scheduling | scheduled | done
  locked_date TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE artifacts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id INTEGER NOT NULL REFERENCES sessions(id),
  member TEXT NOT NULL REFERENCES members(name),
  title TEXT NOT NULL,
  type TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (session_id, member)
);

CREATE TABLE date_options (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id INTEGER NOT NULL REFERENCES sessions(id),
  date TEXT NOT NULL, -- YYYY-MM-DD
  proposed_by TEXT,
  UNIQUE (session_id, date)
);

CREATE TABLE votes (
  option_id INTEGER NOT NULL REFERENCES date_options(id),
  member TEXT NOT NULL REFERENCES members(name),
  answer TEXT NOT NULL CHECK (answer IN ('yes', 'maybe', 'no')),
  PRIMARY KEY (option_id, member)
);

-- Each member's secret link is /m/<token>. List them with:
--   wrangler d1 execute tmi --remote --command "SELECT name, token FROM members"
INSERT INTO members (name, token) VALUES
  ('Nina', lower(hex(randomblob(9)))),
  ('Kai',  lower(hex(randomblob(9)))),
  ('Otto', lower(hex(randomblob(9)))),
  ('Cass', lower(hex(randomblob(9)))),
  ('Ada',  lower(hex(randomblob(9))));

INSERT INTO sessions (number, status) VALUES (1, 'collecting');
