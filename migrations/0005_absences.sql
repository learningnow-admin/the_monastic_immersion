-- A seat can sit a session out: no share, no vote marks, no hosting.
-- Absences are per session, so the next session starts with everyone in.
CREATE TABLE absences (
  session_id INTEGER NOT NULL REFERENCES sessions(id),
  member TEXT NOT NULL REFERENCES members(name),
  PRIMARY KEY (session_id, member)
);
