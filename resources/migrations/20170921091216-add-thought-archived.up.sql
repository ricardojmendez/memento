ALTER TABLE thoughts
  ADD COLUMN "archived?" BOOLEAN NOT NULL DEFAULT FALSE;
--;;
CREATE INDEX idx_thought_archived ON thoughts("archived?");