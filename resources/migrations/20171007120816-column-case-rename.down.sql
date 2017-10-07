ALTER TABLE users_roles RENAME COLUMN "role-id" TO role_id;
--;;
ALTER TABLE thoughts RENAME COLUMN "root-id" TO root_id;
--;;
ALTER TABLE thoughts RENAME COLUMN "follow-id" TO "refine_id";
--;;
DROP TRIGGER thought_root_clear_trigger ON thoughts;
--;;
DROP FUNCTION clear_root_on_delete();
--;;
CREATE FUNCTION clear_root_on_delete()
  RETURNS TRIGGER AS '
BEGIN
  IF (SELECT COUNT(*) FROM thoughts
      WHERE root_id = OLD.root_id) = 1::BIGINT THEN
    UPDATE thoughts
    SET root_id = NULL
    WHERE id = OLD.root_id;
  END IF;
  RETURN OLD;
END
' LANGUAGE 'plpgsql';
--;;
CREATE TRIGGER thought_root_clear_trigger
AFTER DELETE ON thoughts
FOR EACH ROW
EXECUTE PROCEDURE clear_root_on_delete();
--;;
ALTER TABLE reminders RENAME COLUMN "thought-id" TO "thought_id";
--;;
ALTER TABLE reminders RENAME COLUMN "type-id" TO "type_id";
--;;
ALTER TABLE reminders RENAME COLUMN "next-date" TO "next_date";