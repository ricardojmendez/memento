-- This might fail to rollback if there are null elements
ALTER TABLE reminders ALTER COLUMN next_date SET NOT NULL;