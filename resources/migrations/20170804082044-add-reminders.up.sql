CREATE TABLE reminder_types
(
  id       VARCHAR(10) PRIMARY KEY,
  schedule JSONB
);
--;;
INSERT INTO reminder_types (id, schedule) VALUES ('spaced', '{
  "days": [
    2,
    10,
    30,
    60
  ]
}');
--;;
INSERT INTO reminder_types (id) VALUES ('once');
--;;
CREATE TABLE reminders
(
  id         UUID PRIMARY KEY     DEFAULT uuid_generate_v4(),
  type_id    VARCHAR(10) NOT NULL REFERENCES reminder_types (id),
  thought_id UUID        NOT NULL REFERENCES thoughts (id),
  created    TIMESTAMPTZ NOT NULL DEFAULT now(),
  next_date  TIMESTAMPTZ NOT NULL,
  properties JSONB
);
--;;
CREATE INDEX reminder_next_date_thought
  ON reminders (next_date, thought_id);
