CREATE INDEX idx_thought_username ON thoughts(username);
--;;
CREATE INDEX idx_reminder_thought_id ON reminders(thought_id);