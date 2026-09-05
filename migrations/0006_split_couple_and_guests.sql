-- Every link is one person again: Otto and Cass get their own rows and fresh
-- tokens (send them the new links). History under the shared row goes to
-- Otto, since there is no way to tell which of them it was.
INSERT OR IGNORE INTO members (name, token) VALUES
  ('Otto', lower(hex(randomblob(9)))),
  ('Cass', lower(hex(randomblob(9))));

UPDATE artifacts SET member = 'Otto' WHERE member = 'Otto & Cass';
UPDATE votes SET member = 'Otto' WHERE member = 'Otto & Cass';
UPDATE absences SET member = 'Otto' WHERE member = 'Otto & Cass';
UPDATE sessions SET host = 'Otto' WHERE host = 'Otto & Cass';
UPDATE date_options SET proposed_by = 'Otto' WHERE proposed_by = 'Otto & Cass';

DELETE FROM members WHERE name = 'Otto & Cass';

-- An artifact may be a guest's, recorded under the link of the member who
-- brought them. NULL means it is the member's own.
ALTER TABLE artifacts ADD COLUMN guest TEXT;
