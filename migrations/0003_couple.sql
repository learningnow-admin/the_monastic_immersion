-- Otto and Cass are a couple: one seat, one link, one share, one vote.
-- Insert the merged member first so foreign keys stay satisfied while
-- child rows move over; the couple gets a fresh token (send them the new link).
INSERT INTO members (name, token) VALUES ('Otto & Cass', lower(hex(randomblob(9))));

UPDATE artifacts SET member = 'Otto & Cass' WHERE member = 'Otto';
-- If both had an artifact in the same session, keep Otto's (one share per seat).
DELETE FROM artifacts WHERE member = 'Cass'
  AND session_id IN (SELECT session_id FROM artifacts WHERE member = 'Otto & Cass');
UPDATE artifacts SET member = 'Otto & Cass' WHERE member = 'Cass';

UPDATE votes SET member = 'Otto & Cass' WHERE member = 'Otto';
DELETE FROM votes WHERE member = 'Cass'
  AND option_id IN (SELECT option_id FROM votes WHERE member = 'Otto & Cass');
UPDATE votes SET member = 'Otto & Cass' WHERE member = 'Cass';

UPDATE sessions SET host = 'Otto & Cass' WHERE host IN ('Otto', 'Cass');
UPDATE date_options SET proposed_by = 'Otto & Cass' WHERE proposed_by IN ('Otto', 'Cass');

DELETE FROM members WHERE name IN ('Otto', 'Cass');
