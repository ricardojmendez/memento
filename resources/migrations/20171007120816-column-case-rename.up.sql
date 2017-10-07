ALTER TABLE users_roles RENAME COLUMN role_id to "role-id";
--;;
ALTER TABLE thoughts RENAME COLUMN root_id to "root-id";
--;;
ALTER TABLE thoughts RENAME COLUMN "refine_id" TO "follow-id";
--;;
DROP TRIGGER thought_root_clear_trigger ON thoughts;
--;;
DROP FUNCTION clear_root_on_delete();
--;;
CREATE FUNCTION clear_root_on_delete()
RETURNS TRIGGER AS '
BEGIN
  IF (SELECT COUNT(*) FROM thoughts
      WHERE "root-id" = OLD."root-id") = 1::BIGINT THEN
    UPDATE thoughts
    SET "root-id" = NULL
    WHERE id = OLD."root-id";
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
ALTER TABLE reminders RENAME COLUMN "thought_id" TO "thought-id";
--;;
ALTER TABLE reminders RENAME COLUMN "type_id" TO "type-id";
--;;
ALTER TABLE reminders RENAME COLUMN "next_date" TO "next-date";